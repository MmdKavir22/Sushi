package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.client.ModInfo;
import ir.mmdkavir.sushi.profile.Profile;
import ir.mmdkavir.sushi.util.ConsoleLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Overview of the current state. Only states that actually exist in this build are shown as live. */
public final class DashboardScreen extends SushiScreen {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private int macroCount;
	private int scriptCount;

	public DashboardScreen() {
		super(Section.DASHBOARD);
	}

	@Override
	protected void initContent() {
		Profile p = core().profiles().active();
		macroCount = p == null ? 0 : core().macros().list(p.id).size();
		scriptCount = core().countScriptFiles();
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Profile p = core().profiles().active();
		int y = cy;
		text(g, "Sushi " + ModInfo.sushiVersion() + "  |  Minecraft " + ModInfo.minecraftVersion(), cx, y, Theme.TEXT);
		y += 14;
		text(g, "Current profile: " + (p == null ? "none" : p.name), cx, y, Theme.TEXT);
		y += 16;

		g.fill(cx, y + 1, cx + 7, y + 8, Theme.OK);
		text(g, "Status: Idle", cx + 12, y, Theme.OK);
		text(g, "Recording, Playing, Paused, Stopped and Error states arrive with Phase 2.", cx, y + 12, Theme.MUTED);
		y += 28;

		text(g, "Running script: none  (script engine: Phase 4)", cx, y, Theme.MUTED);
		y += 12;
		text(g, "Saved macros: " + macroCount + "   Script files: " + scriptCount + " (cannot run yet)", cx, y, Theme.TEXT);
		y += 18;

		text(g, "Available now: profiles, macro files, settings, console, keybinds.", cx, y, Theme.OK);
		y += 12;
		text(g, "Planned: P2 record/playback, P3 editor, P4 scripts+overlays, P5 hub, P6 images.", cx, y, Theme.MUTED);
		y += 18;

		text(g, "Recent activity", cx, y, Theme.ACCENT);
		y += 12;
		List<ConsoleLog.Entry> all = core().console().snapshot(null);
		int shown = 0;
		for (int i = all.size() - 1; i >= 0 && shown < 4 && y < height - 30; i--, shown++) {
			ConsoleLog.Entry e = all.get(i);
			text(g, TIME.format(Instant.ofEpochMilli(e.epochMillis())) + "  " + e.message(), cx, y, Theme.MUTED);
			y += 11;
		}
		if (all.isEmpty()) {
			text(g, "Nothing yet.", cx, y, Theme.MUTED);
		}
	}
}
