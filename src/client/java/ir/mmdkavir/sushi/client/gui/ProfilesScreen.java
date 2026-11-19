package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.profile.Profile;
import ir.mmdkavir.sushi.util.ActionResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Create, rename, switch and delete profiles. Each profile has its own macro folder. */
public final class ProfilesScreen extends SushiScreen {
	private static final int ROW_H = 22;

	private List<Profile> profiles = List.of();
	private String selectedId;
	private String pendingDeleteId;
	private int page;
	private EditBox nameBox;

	public ProfilesScreen() {
		super(Section.PROFILES);
	}

	@Override
	protected void initContent() {
		profiles = core().profiles().list();
		if (selectedId == null || core().profiles().get(selectedId) == null) {
			Profile active = core().profiles().active();
			selectedId = active == null ? null : active.id;
		}

		nameBox = new EditBox(this.font, cx, cy + 12, Math.min(cw, 260), 18, Component.literal("Profile name"));
		nameBox.setMaxLength(ir.mmdkavir.sushi.profile.ProfileManager.MAX_NAME_LENGTH);
		nameBox.setHint(Component.literal("Profile name"));
		addRenderableWidget(nameBox);

		int gap = 4;
		int bw = Math.min(90, (cw - 3 * gap) / 4);
		int by = cy + 36;
		addRenderableWidget(Button.builder(Component.literal("Create"), b -> {
			report(core().profiles().create(nameBox.getValue()));
			rebuildWidgets();
		}).bounds(cx, by, bw, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> {
			if (selectedId != null) {
				report(core().profiles().rename(selectedId, nameBox.getValue()));
				rebuildWidgets();
			}
		}).bounds(cx + (bw + gap), by, bw, 20).build());

		Profile active = core().profiles().active();
		Button switchBtn = Button.builder(Component.literal("Switch"), b -> {
			if (selectedId != null) {
				ActionResult switched = core().profiles().switchTo(selectedId);
				report(switched);
				if (switched.ok()) {
					core().scripts().onProfileSwitched();
				}
				rebuildWidgets();
			}
		}).bounds(cx + 2 * (bw + gap), by, bw, 20).build();
		switchBtn.active = selectedId != null && (active == null || !selectedId.equals(active.id));
		addRenderableWidget(switchBtn);

		boolean confirm = core().config().get().confirmDestructiveActions;
		boolean armed = selectedId != null && selectedId.equals(pendingDeleteId);
		Button delete = Button.builder(Component.literal(armed ? "Confirm?" : "Delete"), b -> {
			if (selectedId == null) {
				return;
			}
			if (confirm && !selectedId.equals(pendingDeleteId)) {
				pendingDeleteId = selectedId;
				status = "Click Delete again to confirm. Its macros are moved to macros/.trash.";
				statusOk = true;
			} else {
				report(core().profiles().delete(selectedId));
				selectedId = null;
				pendingDeleteId = null;
			}
			rebuildWidgets();
		}).bounds(cx + 3 * (bw + gap), by, bw, 20).build();
		delete.active = selectedId != null;
		addRenderableWidget(delete);

		int listTop = cy + 62;
		int pageY = height - 46;
		int rows = Math.max(1, (pageY - 4 - listTop) / ROW_H);
		int pages = Math.max(1, (profiles.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));

		for (int i = 0; i < rows; i++) {
			int index = page * rows + i;
			if (index >= profiles.size()) {
				break;
			}
			Profile p = profiles.get(index);
			boolean isActive = active != null && p.id.equals(active.id);
			String label = (p.id.equals(selectedId) ? "> " : "") + p.name + (isActive ? "   (active)" : "");
			addRenderableWidget(Button.builder(Component.literal(fit(label, cw - 12)), b -> {
				selectedId = p.id;
				pendingDeleteId = null;
				status = "";
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
		text(g, "Profiles (" + profiles.size() + ")", cx, cy - 2, Theme.TEXT);
		text(g, "Per profile: macros and script auto-start (Script Manager). Images, overlays: later phases", cx + 136, height - 40, Theme.MUTED);
	}
}
