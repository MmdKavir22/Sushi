package ir.mmdkavir.sushi.script.engine;

/**
 * Thrown from the engine's instruction observer when a script exceeds its time limit. It is a
 * {@link Error}, not an exception, because Rhino does not let JavaScript {@code try/catch} intercept
 * Java errors; a script therefore cannot swallow its own timeout.
 */
final class ScriptLimitError extends Error {
	private final int line;

	ScriptLimitError(String message, int line) {
		super(message, null, false, false);
		this.line = line;
	}

	int line() { return line; }
}
