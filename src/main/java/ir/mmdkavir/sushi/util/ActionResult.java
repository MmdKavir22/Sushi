package ir.mmdkavir.sushi.util;

/** Outcome of a user-triggered operation, with a message that is safe to show in the GUI. */
public record ActionResult(boolean ok, String message) {
	public static ActionResult success(String message) {
		return new ActionResult(true, message);
	}

	public static ActionResult failure(String message) {
		return new ActionResult(false, message);
	}
}
