package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.script.ScriptManager;
import ir.mmdkavir.sushi.script.ScriptMetadata;
import ir.mmdkavir.sushi.script.ScriptPermission;
import ir.mmdkavir.sushi.script.ScriptStore;
import ir.mmdkavir.sushi.script.engine.ScriptFailure;
import ir.mmdkavir.sushi.util.ActionResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;

/**
 * Lists the scripts in {@code config/sushi/scripts/} with their status and lets the user create,
 * run, stop, reload, edit, rename, delete and approve them. Several scripts can run at once.
 */
public final class ScriptManagerScreen extends SushiScreen {
	private static final int ROW_H = 20;

	private List<ScriptStore.Entry> entries = List.of();
	private static String selectedId;
	private String pendingDeleteId;
	private int page;
	private EditBox nameBox;

	public ScriptManagerScreen() {
		super(Section.SCRIPTS);
	}

	private ScriptManager scripts() {
		return core().scripts();
	}

	private static String statusLabel(ScriptManager.Status s) {
		return switch (s) {
			case RUNNING -> "RUNNING";
			case STOPPED -> "stopped";
			case ERROR -> "ERROR";
			case NEEDS_APPROVAL -> "needs approval";
			case MISSING -> "missing";
		};
	}

	@Override
	protected void initContent() {
		entries = scripts().store().list();
		if (selectedId != null && entries.stream().noneMatch(e -> e.id().equals(selectedId))) {
			selectedId = null;
		}

		nameBox = new EditBox(this.font, cx, cy + 12, Math.min(cw - 70, 240), 18, Component.literal("Script name"));
		nameBox.setMaxLength(100);
		nameBox.setHint(Component.literal("name or folder/name"));
		addRenderableWidget(nameBox);
		int newX = cx + Math.min(cw - 70, 240) + 4;
		addRenderableWidget(Button.builder(Component.literal("New"), b -> {
			ActionResult r = scripts().create(nameBox.getValue());
			if (r.ok()) {
				selectedId = r.message();
				minecraft.gui.setScreen(new ScriptEditorScreen(selectedId));
				return;
			}
			report(r);
			rebuildWidgets();
		}).bounds(newX, cy + 11, 60, 20).build());

		int gap = 4;
		int bw = Math.max(40, Math.min(70, (cw - 3 * gap) / 4));
		int row1 = cy + 36;
		int row2 = cy + 58;
		boolean sel = selectedId != null;
		ScriptManager.Status st = sel ? scripts().status(selectedId) : ScriptManager.Status.MISSING;
		boolean isRunning = st == ScriptManager.Status.RUNNING;

		Button run = Button.builder(Component.literal("Run"), b -> act(scripts().start(selectedId)))
				.bounds(cx, row1, bw, 20).build();
		run.active = sel && !isRunning && st != ScriptManager.Status.NEEDS_APPROVAL;
		addRenderableWidget(run);

		Button stop = Button.builder(Component.literal("Stop"), b -> act(scripts().stop(selectedId)))
				.bounds(cx + (bw + gap), row1, bw, 20).build();
		stop.active = sel && (isRunning || st == ScriptManager.Status.ERROR);
		addRenderableWidget(stop);

		Button reload = Button.builder(Component.literal("Reload"), b -> act(scripts().reload(selectedId)))
				.bounds(cx + 2 * (bw + gap), row1, bw, 20).build();
		reload.active = sel && st != ScriptManager.Status.NEEDS_APPROVAL && st != ScriptManager.Status.MISSING;
		addRenderableWidget(reload);

		Button edit = Button.builder(Component.literal("Edit"), b -> minecraft.gui.setScreen(new ScriptEditorScreen(selectedId)))
				.bounds(cx + 3 * (bw + gap), row1, bw, 20).build();
		edit.active = sel;
		addRenderableWidget(edit);

		boolean needsApproval = sel && !scripts().missingPermissions(selectedId).isEmpty();
		Button approve = Button.builder(Component.literal(needsApproval || !sel ? "Approve" : "Revoke"),
				b -> act(needsApproval ? scripts().approve(selectedId) : scripts().revoke(selectedId)))
				.bounds(cx, row2, bw, 20).build();
		approve.active = sel && (needsApproval || !scripts().granted(selectedId).isEmpty());
		addRenderableWidget(approve);

		Button rename = Button.builder(Component.literal("Rename"), b -> {
			ActionResult r = scripts().rename(selectedId, nameBox.getValue());
			if (r.ok()) {
				selectedId = r.message();
				report(ActionResult.success("Renamed to " + r.message() + ". A running script was stopped."));
			} else {
				report(r);
			}
			rebuildWidgets();
		}).bounds(cx + (bw + gap), row2, bw, 20).build();
		rename.active = sel;
		addRenderableWidget(rename);

		boolean confirm = core().config().get().confirmDestructiveActions;
		boolean armed = sel && selectedId.equals(pendingDeleteId);
		Button delete = Button.builder(Component.literal(armed ? "Confirm?" : "Delete"), b -> {
			if (confirm && !selectedId.equals(pendingDeleteId)) {
				pendingDeleteId = selectedId;
				status = "Click Delete again to move this script to scripts/.trash.";
				statusOk = true;
			} else {
				report(scripts().delete(selectedId));
				selectedId = null;
				pendingDeleteId = null;
			}
			rebuildWidgets();
		}).bounds(cx + 2 * (bw + gap), row2, bw, 20).build();
		delete.active = sel;
		addRenderableWidget(delete);

		boolean auto = sel && scripts().isAutoStart(selectedId);
		Button autoBtn = Button.builder(Component.literal(auto ? "Auto: ON" : "Auto: off"), b -> {
			report(scripts().setAutoStart(selectedId, !scripts().isAutoStart(selectedId)));
			rebuildWidgets();
		}).bounds(cx + 3 * (bw + gap), row2, bw, 20).build();
		autoBtn.active = sel;
		addRenderableWidget(autoBtn);

		int listTop = cy + 84;
		int pageY = height - 46;
		int rows = Math.max(1, (pageY - 38 - listTop) / ROW_H);
		int pages = Math.max(1, (entries.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));

		for (int i = 0; i < rows; i++) {
			int index = page * rows + i;
			if (index >= entries.size()) {
				break;
			}
			ScriptStore.Entry e = entries.get(index);
			String label = (e.id().equals(selectedId) ? "> " : "") + e.id() + "   [" + statusLabel(scripts().status(e.id())) + "]";
			addRenderableWidget(Button.builder(Component.literal(fit(label, cw - 12)), b -> {
				selectedId = e.id();
				pendingDeleteId = null;
				status = "";
				rebuildWidgets();
			}).bounds(cx, listTop + i * ROW_H, cw - 6, 18).build());
		}

		Button prev = Button.builder(Component.literal("< Prev"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(cx, pageY, 50, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		int totalPages = pages;
		Button next = Button.builder(Component.literal("Next >"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(cx + 54, pageY, 50, 20).build();
		next.active = page < totalPages - 1;
		addRenderableWidget(next);
		addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
			status = "Reloaded the script list.";
			statusOk = true;
			rebuildWidgets();
		}).bounds(cx + 108, pageY, 56, 20).build());
		Button stopAll = Button.builder(Component.literal("Stop all"), b -> {
			scripts().stopAll();
			status = "Stopped all scripts.";
			statusOk = true;
			rebuildWidgets();
		}).bounds(cx + 168, pageY, 56, 20).build();
		stopAll.active = scripts().runningCount() > 0;
		addRenderableWidget(stopAll);
	}

	private void act(ActionResult r) {
		report(r);
		rebuildWidgets();
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		text(g, "Scripts (" + entries.size() + ")   running: " + scripts().runningCount(), cx, cy - 2, Theme.TEXT);
		if (entries.isEmpty()) {
			text(g, "No scripts yet. Type a name and press New (use folder/name for a folder).", cx, cy + 90, Theme.MUTED);
		}
		int dy = height - 46 - 34;
		if (selectedId == null) {
			text(g, "Select a script to see its details.", cx, dy, Theme.MUTED);
			return;
		}
		ScriptStore.Entry entry = entries.stream().filter(e -> e.id().equals(selectedId)).findFirst().orElse(null);
		if (entry == null) {
			return;
		}
		ScriptMetadata m = entry.metadata();
		ScriptManager.Status st = scripts().status(selectedId);
		int stColor = switch (st) {
			case RUNNING -> Theme.OK;
			case ERROR -> Theme.ERROR;
			case NEEDS_APPROVAL -> Theme.WARN;
			default -> Theme.MUTED;
		};
		text(g, m.name() + (m.version().isEmpty() ? "" : "  v" + m.version())
				+ (m.author().isEmpty() ? "" : "  by " + m.author()) + "   -   " + statusLabel(st).toUpperCase(), cx, dy, stColor);
		Set<ScriptPermission> missing = scripts().missingPermissions(selectedId);
		String perms = m.permissions().isEmpty() ? "none requested" : String.join(", ", ScriptPermission.ids(m.permissions()));
		text(g, "Permissions: " + perms + (missing.isEmpty() ? "" : "   (NOT approved: " + String.join(", ", ScriptPermission.ids(missing)) + ")"),
				cx, dy + 11, missing.isEmpty() ? Theme.MUTED : Theme.WARN);
		ScriptFailure f = scripts().lastFailure(selectedId);
		if (f != null) {
			text(g, f.describe(), cx, dy + 22, Theme.ERROR);
		} else if (!m.description().isEmpty()) {
			text(g, m.description(), cx, dy + 22, Theme.MUTED);
		} else if (!m.unknownPermissions().isEmpty()) {
			text(g, "Unknown permission(s) ignored: " + String.join(", ", m.unknownPermissions()), cx, dy + 22, Theme.WARN);
		}
	}
}
