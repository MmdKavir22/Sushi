package ir.mmdkavir.sushi.script.engine;

/** A script problem with a readable message, the line it happened on (0 = unknown) and a category. */
public final class ScriptFailure extends Exception {
	public enum Kind {
		/** The source could not be parsed. */
		SYNTAX,
		/** The script threw or hit a runtime error such as a TypeError. */
		RUNTIME,
		/** The script ran longer than its time limit; it is always stopped. */
		TIMEOUT,
		/** The script hit another hard limit (recursion depth, memory error); it is always stopped. */
		LIMIT,
		/** A bug in Sushi's own code while serving the script. */
		INTERNAL
	}

	private final Kind kind;
	private final int line;

	public ScriptFailure(Kind kind, String message, int line) {
		super(message, null, false, false);
		this.kind = kind;
		this.line = line;
	}

	public Kind kind() { return kind; }

	/** 1-based line number, or 0 when unknown. */
	public int line() { return line; }

	/** Timeouts and limits cannot be recovered from, so the script is stopped. */
	public boolean fatal() {
		return kind == Kind.TIMEOUT || kind == Kind.LIMIT;
	}

	public String describe() {
		return kind.name().toLowerCase() + " error" + (line > 0 ? " at line " + line : "") + ": " + getMessage();
	}
}
