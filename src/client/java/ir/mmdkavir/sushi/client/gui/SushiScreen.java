package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.SushiCore;
import ir.mmdkavir.sushi.profile.Profile;
import ir.mmdkavir.sushi.util.ActionResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for every Sushi screen: draws the dark theme, a sidebar with navigation and a content
 * area ({@code cx, cy, cw, ch}) that subclasses fill in {@link #initContent()} / {@link #renderContent}.
 */
public abstract class SushiScreen extends Screen {
	protected static final int SIDEBAR_W = 136;

	protected final Section section;
	protected int cx;
	protected int cy;
	protected int cw;
	protected int ch;
	/** One-line feedback shown at the bottom of the content area. */
	protected String status = "";
	protected boolean statusOk = true;

	protected SushiScreen(Section section) {
		super(Component.literal("Sushi - " + section.label));
		this.section = section;
	}

	protected SushiCore core() {
		return SushiCore.get();
	}

	@Override
	protected void init() {
		cx = SIDEBAR_W + 14;
		cy = 36;
		cw = Math.max(120, width - cx - 10);
		ch = Math.max(80, height - cy - 24);

		Section[] all = Section.values();
		int step = Math.max(20, Math.min(24, (height - 40) / all.length));
		for (int i = 0; i < all.length; i++) {
			Section target = all[i];
			Button nav = Button.builder(Component.literal(target.buttonLabel()), b -> openSection(target))
					.bounds(8, 32 + i * step, SIDEBAR_W - 12, 20).build();
			nav.active = target != section; // the current section is shown as the disabled button
			addRenderableWidget(nav);
		}
		initContent();
	}

	protected abstract void initContent();

	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
	}

	protected void openSection(Section target) {
		this.minecraft.gui.setScreen(target.create());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}

	/**
	 * Draws the themed backdrop. Written without {@code @Override} on purpose: if a future
	 * Minecraft version renames this hook the GUI still works, it just loses the custom backdrop.
	 */
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, Theme.BG);
		g.fill(0, 0, SIDEBAR_W, height, Theme.SIDEBAR);
		g.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, height, Theme.BORDER);
		g.fill(SIDEBAR_W + 1, 0, width, 26, Theme.HEADER);
		g.fill(SIDEBAR_W + 1, 26, width, 27, Theme.BORDER);
		g.fill(cx - 6, cy - 6, width - 6, height - 6, Theme.PANEL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(0, 0, 4, 26, Theme.ACCENT);
		g.text(this.font, "SUSHI", 12, 9, Theme.ACCENT, true);
		g.text(this.font, section.label, SIDEBAR_W + 12, 9, Theme.TEXT, true);
		Profile active = core().profiles().active();
		if (active != null) {
			String p = fit("Profile: " + active.name, 150);
			g.text(this.font, p, width - this.font.width(p) - 10, 9, Theme.MUTED, true);
		}
		renderContent(g, mouseX, mouseY);
		if (!status.isEmpty()) {
			text(g, status, cx, height - 20, statusOk ? Theme.OK : Theme.ERROR);
		}
	}

	protected void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(this.font, fit(s, width - x - 10), x, y, color, true);
	}

	/** Truncates a string with "..." so it fits in {@code maxWidth} pixels. */
	protected String fit(String s, int maxWidth) {
		if (this.font.width(s) <= maxWidth) {
			return s;
		}
		String ellipsis = "...";
		int end = s.length();
		while (end > 0 && this.font.width(s.substring(0, end) + ellipsis) > maxWidth) {
			end--;
		}
		return s.substring(0, end) + ellipsis;
	}

	protected void report(ActionResult r) {
		status = r.message();
		statusOk = r.ok();
	}
}
