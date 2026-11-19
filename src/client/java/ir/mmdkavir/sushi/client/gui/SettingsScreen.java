package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.client.SushiKeys;
import ir.mmdkavir.sushi.config.SushiSettings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Settings that exist in this build, plus a read-only view of the registered keybinds. */
public final class SettingsScreen extends SushiScreen {
	public SettingsScreen() {
		super(Section.SETTINGS);
	}

	private static String onOff(boolean v) {
		return v ? "ON" : "OFF";
	}

	@Override
	protected void initContent() {
		SushiSettings s = core().config().get();
		int w = Math.min(cw - 6, 260);

		addRenderableWidget(Button.builder(Component.literal("Debug logging: " + onOff(s.debugLogging)), b -> {
			s.debugLogging = !s.debugLogging;
			core().console().setDebugEnabled(s.debugLogging);
			saveAndRefresh();
		}).bounds(cx, cy, w, 20).build());

		addRenderableWidget(Button.builder(
				Component.literal("Confirm destructive actions: " + onOff(s.confirmDestructiveActions)), b -> {
					s.confirmDestructiveActions = !s.confirmDestructiveActions;
					saveAndRefresh();
				}).bounds(cx, cy + 24, w, 20).build());
	}

	private void saveAndRefresh() {
		if (core().config().save()) {
			status = "Saved.";
			statusOk = true;
		} else {
			status = "Could not save settings. See the console.";
			statusOk = false;
		}
		rebuildWidgets();
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = cy + 54;
		text(g, "Keybinds (change them in Options > Controls > Key Binds > Sushi)", cx, y, Theme.ACCENT);
		y += 14;
		y = keyLine(g, "Open Sushi", SushiKeys.openGui, y);
		y = keyLine(g, "Open Sushi Console", SushiKeys.openConsole, y);
		y += 6;
		text(g, "Not yet available: emergency stop, recording and playback keys (Phase 2).", cx, y, Theme.MUTED);
		y += 16;
		text(g, "Data folder: " + core().paths().root(), cx, y, Theme.MUTED);
		y += 12;
		text(g, "Recording, playback, script, GitHub and overlay settings arrive with their phases.", cx, y, Theme.MUTED);
	}

	private int keyLine(GuiGraphicsExtractor g, String label, KeyMapping key, int y) {
		String value = key == null ? "(not registered)" : key.getTranslatedKeyMessage().getString();
		text(g, label + ":  " + value, cx, y, Theme.TEXT);
		return y + 12;
	}
}
