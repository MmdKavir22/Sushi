package ir.mmdkavir.sushi.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Honest placeholder page for a section whose functionality arrives in a later phase. It has no buttons. */
final class PlannedScreen extends SushiScreen {
	private final String heading;
	private final String[] lines;

	PlannedScreen(Section section, String heading, String... lines) {
		super(section);
		this.heading = heading;
		this.lines = lines;
	}

	@Override
	protected void initContent() {
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		text(g, "NOT AVAILABLE YET", cx, cy, Theme.WARN);
		text(g, heading, cx, cy + 16, Theme.TEXT);
		int y = cy + 36;
		for (String line : lines) {
			text(g, line, cx, y, Theme.MUTED);
			y += 12;
		}
	}
}
