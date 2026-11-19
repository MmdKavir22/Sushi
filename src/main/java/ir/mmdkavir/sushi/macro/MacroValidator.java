package ir.mmdkavir.sushi.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Structural validation for {@link Macro}. Returns human-readable problems; empty means valid. */
public final class MacroValidator {
	public static final int MAX_NAME_LENGTH = 64;
	public static final int MAX_EVENTS = 100_000;
	private static final int MAX_REPORTED = 8;
	private static final Pattern MOUSE_BUTTON = Pattern.compile("LEFT|RIGHT|MIDDLE|BUTTON_[0-9]{1,2}");

	private MacroValidator() {
	}

	public static List<String> validate(Macro macro) {
		List<String> errors = new ArrayList<>();
		if (macro == null) {
			errors.add("Macro is empty.");
			return errors;
		}
		if (macro.formatVersion != Macro.CURRENT_FORMAT) {
			errors.add("Unsupported formatVersion " + macro.formatVersion + ".");
		}
		if (macro.name == null || macro.name.isBlank()) {
			errors.add("Macro name is missing.");
		} else if (macro.name.length() > MAX_NAME_LENGTH) {
			errors.add("Macro name is longer than " + MAX_NAME_LENGTH + " characters.");
		}
		if (macro.events == null) {
			errors.add("The events list is missing.");
			return errors;
		}
		if (macro.events.size() > MAX_EVENTS) {
			errors.add("Too many events (" + macro.events.size() + ", limit " + MAX_EVENTS + ").");
			return errors;
		}
		long previous = 0;
		for (int i = 0; i < macro.events.size() && errors.size() < MAX_REPORTED; i++) {
			MacroEvent e = macro.events.get(i);
			String at = "Event #" + (i + 1) + ": ";
			if (e == null) {
				errors.add(at + "is null.");
				continue;
			}
			Optional<MacroEventType> type = MacroEventType.parse(e.type);
			if (type.isEmpty()) {
				errors.add(at + "unknown type \"" + e.type + "\".");
				continue;
			}
			if (e.time < 0) {
				errors.add(at + "time must not be negative.");
			} else if (e.time < previous) {
				errors.add(at + "time goes backwards (events must be ordered).");
			}
			previous = Math.max(previous, e.time);
			switch (type.get()) {
				case KEY_PRESS, KEY_RELEASE -> {
					if (e.key == null || e.key.isBlank() || e.key.length() > 32) {
						errors.add(at + "needs a key name (1-32 characters).");
					}
				}
				case MOUSE_PRESS, MOUSE_RELEASE -> {
					if (e.button == null || !MOUSE_BUTTON.matcher(e.button).matches()) {
						errors.add(at + "needs a button: LEFT, RIGHT, MIDDLE or BUTTON_n.");
					}
				}
				case MOUSE_SCROLL -> {
					if (e.amount == null || e.amount.isNaN() || e.amount.isInfinite()) {
						errors.add(at + "needs a finite scroll amount.");
					}
				}
				case MOUSE_MOVE -> {
					if (e.x == null || e.y == null || !Double.isFinite(e.x) || !Double.isFinite(e.y)) {
						errors.add(at + "needs finite x and y.");
					}
				}
				case WAIT -> {
					// A WAIT only needs its timestamp.
				}
			}
		}
		return errors;
	}
}
