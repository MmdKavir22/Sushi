package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.util.ConsoleLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Live view of the Sushi log with level filters and a Clear button. */
public final class ConsoleScreen extends SushiScreen {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final int LINE_H = 10;

	/** null = show all levels. Kept static so the filter survives reopening the console. */
	private static ConsoleLog.Level filter;

	public ConsoleScreen() {
		super(Section.CONSOLE);
	}

	@Override
	protected void initContent() {
		int gap = 4;
		int bw = Math.max(40, Math.min(60, (cw - 5 * gap) / 6));
		addFilter("All", null, 0, bw, gap);
		addFilter("Info", ConsoleLog.Level.INFO, 1, bw, gap);
		addFilter("Warn", ConsoleLog.Level.WARN, 2, bw, gap);
		addFilter("Error", ConsoleLog.Level.ERROR, 3, bw, gap);
		addFilter("Debug", ConsoleLog.Level.DEBUG, 4, bw, gap);
		addRenderableWidget(Button.builder(Component.literal("Clear"), b -> core().console().clear())
				.bounds(cx + 5 * (bw + gap), cy, bw, 20).build());
	}

	private void addFilter(String label, ConsoleLog.Level level, int index, int bw, int gap) {
		Button b = Button.builder(Component.literal(label), btn -> {
			filter = level;
			rebuildWidgets();
		}).bounds(cx + index * (bw + gap), cy, bw, 20).build();
		b.active = filter != level;
		addRenderableWidget(b);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int top = cy + 28;
		int visible = Math.max(1, (height - 28 - top) / LINE_H);
		List<ConsoleLog.Entry> entries = core().console().snapshot(filter);
		if (entries.isEmpty()) {
			text(g, "No messages for this filter.", cx, top, Theme.MUTED);
			return;
		}
		int from = Math.max(0, entries.size() - visible);
		int y = top;
		for (int i = from; i < entries.size(); i++) {
			ConsoleLog.Entry e = entries.get(i);
			String line = TIME.format(Instant.ofEpochMilli(e.epochMillis())) + " " + e.level().name().charAt(0) + "  " + e.message();
			text(g, line, cx, y, colorOf(e.level()));
			y += LINE_H;
		}
	}

	private static int colorOf(ConsoleLog.Level level) {
		return switch (level) {
			case INFO -> Theme.TEXT;
			case WARN -> Theme.WARN;
			case ERROR -> Theme.ERROR;
			case DEBUG -> Theme.DEBUG;
		};
	}
}
