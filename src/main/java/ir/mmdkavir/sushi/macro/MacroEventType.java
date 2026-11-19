package ir.mmdkavir.sushi.macro;

import java.util.Arrays;
import java.util.Optional;

/** Event types accepted by the macro format (version 1). */
public enum MacroEventType {
	KEY_PRESS,
	KEY_RELEASE,
	MOUSE_PRESS,
	MOUSE_RELEASE,
	MOUSE_SCROLL,
	MOUSE_MOVE,
	WAIT;

	public static Optional<MacroEventType> parse(String name) {
		return Arrays.stream(values()).filter(t -> t.name().equals(name)).findFirst();
	}
}
