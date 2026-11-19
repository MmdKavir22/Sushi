package ir.mmdkavir.sushi.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Key names scripts may use, mapped to GLFW key codes (which Minecraft uses as well). */
public final class KeyNames {
	private static final Map<String, Integer> CODES = new LinkedHashMap<>();

	static {
		for (char c = 'A'; c <= 'Z'; c++) {
			CODES.put(String.valueOf(c), (int) c);
		}
		for (char c = '0'; c <= '9'; c++) {
			CODES.put(String.valueOf(c), (int) c);
		}
		for (int i = 1; i <= 12; i++) {
			CODES.put("F" + i, 289 + i);
		}
		for (int i = 0; i <= 9; i++) {
			CODES.put("KP_" + i, 320 + i);
		}
		CODES.put("SPACE", 32);
		CODES.put("COMMA", 44);
		CODES.put("MINUS", 45);
		CODES.put("PERIOD", 46);
		CODES.put("SLASH", 47);
		CODES.put("ESCAPE", 256);
		CODES.put("ENTER", 257);
		CODES.put("TAB", 258);
		CODES.put("BACKSPACE", 259);
		CODES.put("INSERT", 260);
		CODES.put("DELETE", 261);
		CODES.put("RIGHT", 262);
		CODES.put("LEFT", 263);
		CODES.put("DOWN", 264);
		CODES.put("UP", 265);
		CODES.put("PAGE_UP", 266);
		CODES.put("PAGE_DOWN", 267);
		CODES.put("HOME", 268);
		CODES.put("END", 269);
		CODES.put("LSHIFT", 340);
		CODES.put("LCTRL", 341);
		CODES.put("LALT", 342);
		CODES.put("RSHIFT", 344);
		CODES.put("RCTRL", 345);
		CODES.put("RALT", 346);
	}

	private KeyNames() {
	}

	/** GLFW code for a name (case-insensitive), or -1 if the name is unknown. */
	public static int code(String name) {
		if (name == null) {
			return -1;
		}
		Integer c = CODES.get(name.trim().toUpperCase(Locale.ROOT));
		return c == null ? -1 : c;
	}

	public static Map<String, Integer> all() {
		return Collections.unmodifiableMap(CODES);
	}
}
