package ir.mmdkavir.sushi.client.gui;

import ir.mmdkavir.sushi.script.ScriptManager;
import ir.mmdkavir.sushi.script.engine.ScriptFailure;
import ir.mmdkavir.sushi.util.ActionResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-line editor for one script: line numbers, current-line highlight, error line marker, an
 * unsaved-changes indicator, Save / Check syntax / Run / Stop, and rename / delete of the script.
 * Editing: typing, Backspace, Delete, Enter (keeps indentation), Tab (two spaces), arrow keys, Home,
 * End, Page Up/Down, mouse click and wheel, Ctrl+S (save), Ctrl+V (paste), Ctrl+C (copy current line).
 * There is no text selection and no undo.
 */
public final class ScriptEditorScreen extends SushiScreen {
	private static final int LINE_H = 10;
	private static final int MAX_LINES = 10_000;
	private static final int MAX_LINE_LENGTH = 2_000;

	private final String scriptId;
	private final List<String> lines = new ArrayList<>();
	private int row;
	private int col;
	private int scroll;
	private int hScroll;
	private boolean dirty;
	private boolean loaded;
	private boolean confirmLeave;
	private boolean confirmDelete;
	private int errorLine;
	private String errorMessage = "";
	private EditBox nameBox;

	// editor rectangle, set in initContent
	private int ex;
	private int ey;
	private int ew;
	private int eh;
	private int gutterW;

	public ScriptEditorScreen(String scriptId) {
		super(Section.SCRIPTS);
		this.scriptId = scriptId;
		load();
	}

	private ScriptManager scripts() {
		return core().scripts();
	}

	private void load() {
		lines.clear();
		try {
			String src = scripts().store().read(scriptId).replace("\r\n", "\n").replace('\r', '\n').replace("\t", "  ");
			for (String l : src.split("\n", -1)) {
				lines.add(l);
			}
			loaded = true;
		} catch (IOException | IllegalArgumentException e) {
			lines.add("");
			loaded = false;
			status = "Could not open " + scriptId + ": " + e.getMessage();
			statusOk = false;
		}
		if (lines.isEmpty()) {
			lines.add("");
		}
		row = 0;
		col = 0;
		dirty = false;
		ScriptFailure f = scripts().lastFailure(scriptId);
		if (f != null) {
			setError(f);
		}
	}

	private String text() {
		return String.join("\n", lines);
	}

	private void setError(ScriptFailure f) {
		errorLine = f == null ? 0 : f.line();
		errorMessage = f == null ? "" : f.describe();
	}

	// ---- widgets -----------------------------------------------------------------------------------

	@Override
	protected void initContent() {
		int gap = 4;
		int bw = Math.max(36, Math.min(58, (cw - 5 * gap) / 6));
		int y1 = cy - 2;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(cx, y1, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Check"), b -> check()).bounds(cx + (bw + gap), y1, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Run"), b -> run()).bounds(cx + 2 * (bw + gap), y1, bw, 20).build());
		Button stop = Button.builder(Component.literal("Stop"), b -> {
			report(scripts().stop(scriptId));
		}).bounds(cx + 3 * (bw + gap), y1, bw, 20).build();
		addRenderableWidget(stop);
		Button approve = Button.builder(Component.literal("Approve"), b -> {
			if (dirty) {
				save();
			}
			report(scripts().approve(scriptId));
		}).bounds(cx + 4 * (bw + gap), y1, bw, 20).build();
		addRenderableWidget(approve);
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> back()).bounds(cx + 5 * (bw + gap), y1, bw, 20).build());

		int y2 = cy + 22;
		int boxW = Math.max(60, Math.min(160, cw - 2 * (bw + gap)));
		nameBox = new EditBox(this.font, cx, y2, boxW, 18, Component.literal("New name"));
		nameBox.setMaxLength(100);
		nameBox.setHint(Component.literal("new name"));
		addRenderableWidget(nameBox);
		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename()).bounds(cx + boxW + gap, y2 - 1, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "Confirm?" : "Delete"), b -> delete())
				.bounds(cx + boxW + gap + bw + gap, y2 - 1, bw, 20).build());

		gutterW = this.font.width("00000") + 8;
		ex = cx;
		ey = cy + 46;
		ew = cw - 6;
		eh = Math.max(LINE_H * 3, height - 44 - ey);
	}

	private int visibleRows() {
		return Math.max(1, eh / LINE_H);
	}

	// ---- actions -----------------------------------------------------------------------------------

	private boolean save() {
		if (!loaded) {
			status = "Nothing to save: the file could not be read.";
			statusOk = false;
			return false;
		}
		ActionResult r = scripts().save(scriptId, text());
		report(r);
		if (r.ok()) {
			dirty = false;
		}
		return r.ok();
	}

	private void check() {
		ScriptFailure f = scripts().checkSyntax(scriptId, text());
		setError(f);
		if (f == null) {
			status = "No syntax errors found.";
			statusOk = true;
		} else {
			status = f.describe();
			statusOk = false;
			if (f.line() > 0) {
				goToLine(f.line() - 1);
			}
		}
	}

	private void run() {
		if (dirty && !save()) {
			return;
		}
		setError(null);
		ActionResult r = scripts().status(scriptId) == ScriptManager.Status.RUNNING
				? scripts().reload(scriptId) : scripts().start(scriptId);
		report(r);
		ScriptFailure f = scripts().lastFailure(scriptId);
		if (f != null) {
			setError(f);
			if (f.line() > 0) {
				goToLine(f.line() - 1);
			}
		}
	}

	private void rename() {
		if (dirty && !save()) {
			return;
		}
		ActionResult r = scripts().rename(scriptId, nameBox.getValue());
		if (r.ok()) {
			this.minecraft.gui.setScreen(new ScriptEditorScreen(r.message()));
		} else {
			report(r);
		}
	}

	private void delete() {
		if (core().config().get().confirmDestructiveActions && !confirmDelete) {
			confirmDelete = true;
			status = "Click Delete again to move this script to scripts/.trash.";
			statusOk = true;
			rebuildWidgets();
			return;
		}
		ActionResult r = scripts().delete(scriptId);
		if (r.ok()) {
			this.minecraft.gui.setScreen(new ScriptManagerScreen());
		} else {
			report(r);
		}
	}

	private void back() {
		if (leaveBlocked()) {
			return;
		}
		this.minecraft.gui.setScreen(new ScriptManagerScreen());
	}

	/** First attempt with unsaved changes only warns; the second leaves and discards them. */
	private boolean leaveBlocked() {
		if (dirty && !confirmLeave) {
			confirmLeave = true;
			status = "Unsaved changes! Save first, or repeat the action to discard them.";
			statusOk = false;
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		if (leaveBlocked()) {
			return;
		}
		super.onClose();
	}

	@Override
	protected void openSection(Section target) {
		if (leaveBlocked()) {
			return;
		}
		super.openSection(target);
	}

	// ---- editing -----------------------------------------------------------------------------------

	private void goToLine(int r) {
		row = Math.max(0, Math.min(r, lines.size() - 1));
		col = Math.min(col, lines.get(row).length());
		ensureVisible();
	}

	private void edited() {
		dirty = true;
		confirmLeave = false;
		errorLine = 0;
		errorMessage = "";
		ensureVisible();
	}

	private void ensureVisible() {
		int rows = visibleRows();
		if (row < scroll) {
			scroll = row;
		} else if (row >= scroll + rows) {
			scroll = row - rows + 1;
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - 1)));
		int textW = ew - gutterW - 8;
		String line = lines.get(row);
		col = Math.min(col, line.length());
		if (col < hScroll) {
			hScroll = col;
		}
		while (hScroll < col && this.font.width(line.substring(hScroll, col)) > textW - 6) {
			hScroll++;
		}
	}

	private void insert(String s) {
		String line = lines.get(row);
		if (line.length() + s.length() > MAX_LINE_LENGTH) {
			return;
		}
		lines.set(row, line.substring(0, col) + s + line.substring(col));
		col += s.length();
		edited();
	}

	private void insertText(String text) {
		String clean = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "  ");
		String[] parts = clean.split("\n", -1);
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				newLine(false);
			}
			StringBuilder sb = new StringBuilder();
			for (char c : parts[i].toCharArray()) {
				if (c >= 32 && c != 127) {
					sb.append(c);
				}
			}
			insert(sb.toString());
		}
	}

	private void newLine(boolean autoIndent) {
		if (lines.size() >= MAX_LINES) {
			return;
		}
		String line = lines.get(row);
		String indent = "";
		if (autoIndent) {
			int n = 0;
			while (n < line.length() && line.charAt(n) == ' ') {
				n++;
			}
			indent = line.substring(0, Math.min(n, col));
			if (line.substring(0, col).stripTrailing().endsWith("{")) {
				indent += "  ";
			}
		}
		lines.set(row, line.substring(0, col));
		lines.add(row + 1, indent + line.substring(col));
		row++;
		col = indent.length();
		edited();
	}

	private void backspace() {
		if (col > 0) {
			String line = lines.get(row);
			lines.set(row, line.substring(0, col - 1) + line.substring(col));
			col--;
		} else if (row > 0) {
			String prev = lines.get(row - 1);
			col = prev.length();
			lines.set(row - 1, prev + lines.get(row));
			lines.remove(row);
			row--;
		} else {
			return;
		}
		edited();
	}

	private void deleteForward() {
		String line = lines.get(row);
		if (col < line.length()) {
			lines.set(row, line.substring(0, col) + line.substring(col + 1));
		} else if (row < lines.size() - 1) {
			lines.set(row, line + lines.get(row + 1));
			lines.remove(row + 1);
		} else {
			return;
		}
		edited();
	}

	private boolean editorHasFocus() {
		return nameBox == null || !nameBox.isFocused();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!editorHasFocus()) {
			return super.keyPressed(event);
		}
		int key = event.key();
		boolean ctrl = (event.modifiers() & 2) != 0 || (event.modifiers() & 8) != 0;
		if (ctrl) {
			if (key == 83) { // S
				save();
				return true;
			}
			if (key == 86) { // V
				insertText(this.minecraft.keyboardHandler.getClipboard());
				return true;
			}
			if (key == 67) { // C: copies the current line (there is no selection)
				this.minecraft.keyboardHandler.setClipboard(lines.get(row));
				status = "Copied the current line.";
				statusOk = true;
				return true;
			}
			return super.keyPressed(event);
		}
		switch (key) {
			case 257, 335 -> newLine(true);
			case 259 -> backspace();
			case 261 -> deleteForward();
			case 258 -> insert("  ");
			case 263 -> {
				if (col > 0) {
					col--;
				} else if (row > 0) {
					row--;
					col = lines.get(row).length();
				}
				ensureVisible();
			}
			case 262 -> {
				if (col < lines.get(row).length()) {
					col++;
				} else if (row < lines.size() - 1) {
					row++;
					col = 0;
				}
				ensureVisible();
			}
			case 265 -> {
				if (row > 0) {
					row--;
				}
				ensureVisible();
			}
			case 264 -> {
				if (row < lines.size() - 1) {
					row++;
				}
				ensureVisible();
			}
			case 268 -> {
				col = 0;
				ensureVisible();
			}
			case 269 -> {
				col = lines.get(row).length();
				ensureVisible();
			}
			case 266 -> {
				row = Math.max(0, row - visibleRows());
				ensureVisible();
			}
			case 267 -> {
				row = Math.min(lines.size() - 1, row + visibleRows());
				ensureVisible();
			}
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!editorHasFocus()) {
			return super.charTyped(event);
		}
		int cp = event.codepoint();
		if (cp < 32 || cp == 127) {
			return false;
		}
		insert(new String(Character.toChars(cp)));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mx >= ex && mx < ex + ew && my >= ey && my < ey + eh) {
			this.setFocused(null);
			row = Math.max(0, Math.min(lines.size() - 1, scroll + (int) ((my - ey) / LINE_H)));
			String line = lines.get(row);
			double rel = mx - (ex + gutterW + 4);
			int c = hScroll;
			while (c < line.length() && this.font.width(line.substring(hScroll, c + 1)) <= rel) {
				c++;
			}
			col = Math.max(0, Math.min(c, line.length()));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= ex && mouseX < ex + ew && mouseY >= ey && mouseY < ey + eh) {
			scroll = Math.max(0, Math.min(Math.max(0, lines.size() - 1), scroll - (int) Math.signum(scrollY) * 3));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- drawing -----------------------------------------------------------------------------------

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		String title = scriptId + (dirty ? "  *" : "");
		int tw = this.font.width(title);
		g.text(this.font, title, ex + ew - tw - 4, cy + 28, dirty ? Theme.WARN : Theme.MUTED, true);
		if (dirty) {
			g.text(this.font, "UNSAVED", ex + ew - this.font.width("UNSAVED") - 4, cy + 38, Theme.WARN, true);
		}

		g.fill(ex, ey, ex + ew, ey + eh, Theme.BG);
		g.fill(ex, ey, ex + gutterW, ey + eh, Theme.SIDEBAR);
		int textX = ex + gutterW + 4;
		int textW = ew - gutterW - 8;
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= lines.size()) {
				break;
			}
			int y = ey + i * LINE_H + 1;
			boolean isErr = errorLine == idx + 1;
			if (isErr) {
				g.fill(ex, y - 1, ex + ew, y + LINE_H - 1, 0x44EC7063);
			} else if (idx == row && editorHasFocus()) {
				g.fill(ex + gutterW, y - 1, ex + ew, y + LINE_H - 1, 0x22FFFFFF);
			}
			String num = String.valueOf(idx + 1);
			g.text(this.font, num, ex + gutterW - 4 - this.font.width(num), y, isErr ? Theme.ERROR : Theme.MUTED, false);
			String line = lines.get(idx);
			String shown = clip(line, idx == row ? hScroll : hScroll, textW);
			boolean comment = line.stripLeading().startsWith("//");
			g.text(this.font, shown, textX, y, comment ? Theme.MUTED : Theme.TEXT, false);
		}
		if (editorHasFocus() && row >= scroll && row < scroll + rows && (System.currentTimeMillis() / 500) % 2 == 0) {
			String line = lines.get(row);
			int cxp = textX + this.font.width(line.substring(Math.min(hScroll, col), col));
			int y = ey + (row - scroll) * LINE_H;
			g.fill(cxp, y, cxp + 1, y + LINE_H, Theme.ACCENT);
		}
		g.fill(ex, ey, ex + ew, ey + 1, Theme.BORDER);
		g.fill(ex, ey + eh - 1, ex + ew, ey + eh, Theme.BORDER);
		g.fill(ex + gutterW, ey, ex + gutterW + 1, ey + eh, Theme.BORDER);

		String pos = "Ln " + (row + 1) + ", Col " + (col + 1) + "   " + lines.size() + " lines   " + scripts().status(scriptId);
		g.text(this.font, pos, ex, ey + eh + 3, Theme.MUTED, true);
		if (!errorMessage.isEmpty()) {
			text(g, errorMessage, ex, ey + eh + 13, Theme.ERROR);
		}
	}

	private String clip(String line, int from, int maxWidth) {
		if (from >= line.length()) {
			return "";
		}
		int end = from;
		while (end < line.length() && this.font.width(line.substring(from, end + 1)) <= maxWidth) {
			end++;
		}
		return line.substring(from, end);
	}
}
