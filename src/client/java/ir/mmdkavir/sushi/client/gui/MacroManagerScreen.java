package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.macro.MacroStore;
import ir.mmdkavir.sushi.profile.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Manages the macro files of the active profile: create an empty macro, rename, delete, inspect
 * validation problems. Recording, playback and the event editor are not implemented yet.
 */
public final class MacroManagerScreen extends SushiScreen {
	private static final int ROW_H = 22;

	private List<MacroStore.Entry> entries = List.of();
	private String selectedId;
	private String pendingDeleteId;
	private int page;
	private EditBox nameBox;

	public MacroManagerScreen() {
		super(Section.MACROS);
	}

	private String profileId() {
		Profile p = core().profiles().active();
		return p == null ? "default" : p.id;
	}

	@Override
	protected void initContent() {
		entries = core().macros().list(profileId());

		nameBox = new EditBox(this.font, cx, cy + 12, Math.min(cw, 260), 18, Component.literal("Macro name"));
		nameBox.setMaxLength(64);
		nameBox.setHint(Component.literal("Macro name"));
		addRenderableWidget(nameBox);

		int gap = 4;
		int bw = Math.min(90, (cw - 3 * gap) / 4);
		int by = cy + 36;
		addRenderableWidget(Button.builder(Component.literal("Create"), b -> {
			report(core().macros().createEmpty(profileId(), nameBox.getValue()));
			rebuildWidgets();
		}).bounds(cx, by, bw, 20).build());

		Button rename = Button.builder(Component.literal("Rename"), b -> {
			if (selectedId == null) {
				return;
			}
			report(core().macros().rename(profileId(), selectedId, nameBox.getValue()));
			rebuildWidgets();
		}).bounds(cx + (bw + gap), by, bw, 20).build();
		rename.active = selectedId != null;
		addRenderableWidget(rename);

		boolean confirm = core().config().get().confirmDestructiveActions;
		boolean armed = selectedId != null && selectedId.equals(pendingDeleteId);
		Button delete = Button.builder(Component.literal(armed ? "Confirm?" : "Delete"), b -> {
			if (selectedId == null) {
				return;
			}
			if (confirm && !selectedId.equals(pendingDeleteId)) {
				pendingDeleteId = selectedId;
				status = "Click Delete again to move this macro to macros/.trash.";
				statusOk = true;
			} else {
				report(core().macros().delete(profileId(), selectedId));
				selectedId = null;
				pendingDeleteId = null;
			}
			rebuildWidgets();
		}).bounds(cx + 2 * (bw + gap), by, bw, 20).build();
		delete.active = selectedId != null;
		addRenderableWidget(delete);

		addRenderableWidget(Button.builder(Component.literal("Reload"), b -> {
			status = "Reloaded macro list.";
			statusOk = true;
			rebuildWidgets();
		}).bounds(cx + 3 * (bw + gap), by, bw, 20).build());

		int listTop = cy + 62;
		int pageY = height - 46;
		int rows = Math.max(1, (pageY - 4 - listTop) / ROW_H);
		int pages = Math.max(1, (entries.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));

		for (int i = 0; i < rows; i++) {
			int index = page * rows + i;
			if (index >= entries.size()) {
				break;
			}
			MacroStore.Entry e = entries.get(index);
			String label = (e.id().equals(selectedId) ? "> " : "") + e.displayName() + "  -  " + e.eventCount()
					+ " events" + (e.valid() ? "" : "  [INVALID]");
			addRenderableWidget(Button.builder(Component.literal(fit(label, cw - 12)), b -> {
				selectedId = e.id();
				pendingDeleteId = null;
				if (e.valid()) {
					status = "";
				} else {
					status = "Invalid file: " + e.problems().get(0);
					statusOk = false;
				}
				rebuildWidgets();
			}).bounds(cx, listTop + i * ROW_H, cw - 6, 20).build());
		}

		Button prev = Button.builder(Component.literal("< Prev"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(cx, pageY, 60, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		int currentPages = pages;
		Button next = Button.builder(Component.literal("Next >"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(cx + 66, pageY, 60, 20).build();
		next.active = page < currentPages - 1;
		addRenderableWidget(next);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Profile p = core().profiles().active();
		text(g, "Macros in profile: " + (p == null ? "?" : p.name) + "   (" + entries.size() + ")", cx, cy - 2, Theme.TEXT);
		if (entries.isEmpty()) {
			text(g, "No macros yet. Type a name and press Create.", cx, cy + 66, Theme.MUTED);
		}
		text(g, "Macro recording, playback and editing: not implemented yet", cx + 136, height - 40, Theme.MUTED);
	}
}
