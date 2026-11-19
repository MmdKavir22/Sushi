package ir.mmdkavir.sushi.script.engine;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;

import java.util.Map;

/**
 * One isolated JavaScript world (Mozilla Rhino, interpreted mode) for one script.
 *
 * <p>What this class does to restrict scripts, and what it cannot do:
 * <ul>
 * <li>The global scope comes from {@code initSafeStandardObjects}, which has the ECMAScript built-ins
 *     ({@code Math}, {@code JSON}, {@code Date}, ...) but none of Rhino's Java bridge ({@code Packages},
 *     {@code java}, {@code importClass}, ...).</li>
 * <li>A {@link org.mozilla.javascript.ClassShutter} that hides every Java class is installed as a second layer,
 *     and Sushi never hands Java objects to a script; its API consists of plain JavaScript values and
 *     functions only.</li>
 * <li>Rhino runs in interpreted mode with a call-depth limit. A time limit is enforced by an instruction
 *     observer that throws an {@link Error} scripts cannot catch.</li>
 * <li><b>Not enforced:</b> memory use, and the duration of single long native operations such as a
 *     catastrophic regular expression or {@code "x".repeat(1e9)}: the observer only runs between
 *     interpreted instructions. Rhino is not a hardened sandbox against hostile code.</li>
 * </ul>
 * All methods must be called from one thread (the game thread).
 */
public final class RhinoSandbox {
	/** Fills the global scope with the Sushi API. Runs once inside the sandbox's context. */
	public interface Installer {
		void install(Context cx, ScriptableObject scope);
	}

	private static final int MAX_STACK_DEPTH = 200;
	private static final int OBSERVER_THRESHOLD = 2000;

	private final ContextFactory factory = new Factory();
	private ScriptableObject scope;
	private long deadlineNanos = Long.MAX_VALUE;
	private long limitMillis;
	private boolean closed;

	public RhinoSandbox(Installer installer) {
		Context cx = factory.enterContext();
		try {
			scope = cx.initSafeStandardObjects();
			installer.install(cx, scope);
		} finally {
			Context.exit();
		}
	}

	private final class Factory extends ContextFactory {
		@Override
		protected Context makeContext() {
			Context cx = super.makeContext();
			cx.setOptimizationLevel(-1); // interpreted: required for the instruction observer, and no bytecode is generated
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.setInstructionObserverThreshold(OBSERVER_THRESHOLD);
			cx.setMaximumInterpreterStackDepth(MAX_STACK_DEPTH);
			cx.setClassShutter(className -> false);
			cx.setWrapFactory(new NoJavaWrapFactory());
			return cx;
		}

		@Override
		protected void observeInstructionCount(Context cx, int instructionCount) {
			if (System.nanoTime() > deadlineNanos) {
				// Rhino does not publicly expose the current line here, so timeouts report line 0 (unknown).
				throw new ScriptLimitError("Script ran longer than " + limitMillis + " ms and was stopped", 0);
			}
		}
	}

	/** Never wraps Java objects for scripts: only strings, numbers and booleans may pass through. */
	private static final class NoJavaWrapFactory extends WrapFactory {
		NoJavaWrapFactory() {
			setJavaPrimitiveWrap(false);
		}

		@Override
		public Object wrap(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
			if (obj == null || obj instanceof String || obj instanceof Number || obj instanceof Boolean
					|| obj instanceof Scriptable || obj == Undefined.instance) {
				return obj;
			}
			throw new EvaluatorException("Java objects are not available to scripts");
		}

		@Override
		public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class<?> staticType) {
			throw new EvaluatorException("Java objects are not available to scripts");
		}
	}

	/** Parses without running, to report syntax errors with a line number. Returns {@code null} when valid. */
	public static ScriptFailure checkSyntax(String source, String name) {
		ContextFactory f = new ContextFactory();
		Context cx = f.enterContext();
		try {
			cx.setOptimizationLevel(-1);
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.compileString(source, name, 1, null);
			return null;
		} catch (RhinoException e) {
			return new ScriptFailure(ScriptFailure.Kind.SYNTAX, e.details(), e.lineNumber());
		} catch (RuntimeException e) {
			return new ScriptFailure(ScriptFailure.Kind.INTERNAL, String.valueOf(e.getMessage()), 0);
		} finally {
			Context.exit();
		}
	}

	/** Runs the script's top-level code. */
	public void evaluate(String source, String name, long limitMs) throws ScriptFailure {
		if (closed) {
			throw new ScriptFailure(ScriptFailure.Kind.INTERNAL, "Script is not running", 0);
		}
		Context cx = factory.enterContext();
		begin(limitMs);
		try {
			Script compiled;
			try {
				compiled = cx.compileString(source, name, 1, null);
			} catch (RhinoException e) {
				throw new ScriptFailure(ScriptFailure.Kind.SYNTAX, e.details(), e.lineNumber());
			}
			compiled.exec(cx, scope);
		} catch (ScriptFailure f) {
			throw f;
		} catch (Throwable t) {
			throw convert(t);
		} finally {
			end();
			Context.exit();
		}
	}

	/**
	 * Calls a JavaScript function value previously received from the script. A {@code Map} among the
	 * arguments is converted to a plain JavaScript object (values: String, Number, Boolean).
	 */
	public void call(Object fn, Object[] args, long limitMs) throws ScriptFailure {
		if (closed) {
			return;
		}
		if (!(fn instanceof Function f)) {
			throw new ScriptFailure(ScriptFailure.Kind.INTERNAL, "Not a function", 0);
		}
		Context cx = factory.enterContext();
		begin(limitMs);
		try {
			Object[] converted = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				converted[i] = args[i] instanceof Map<?, ?> m ? toJsObject(cx, m) : args[i];
			}
			f.call(cx, scope, scope, converted);
		} catch (Throwable t) {
			throw convert(t);
		} finally {
			end();
			Context.exit();
		}
	}

	private Scriptable toJsObject(Context cx, Map<?, ?> map) {
		Scriptable obj = cx.newObject(scope);
		for (Map.Entry<?, ?> e : map.entrySet()) {
			ScriptableObject.putProperty(obj, String.valueOf(e.getKey()), e.getValue());
		}
		return obj;
	}

	/** Evaluates an expression and returns its result as a string (used by tests and diagnostics). */
	public String evaluateToString(String source, String name, long limitMs) throws ScriptFailure {
		if (closed) {
			throw new ScriptFailure(ScriptFailure.Kind.INTERNAL, "Script is not running", 0);
		}
		Context cx = factory.enterContext();
		begin(limitMs);
		try {
			Object r = cx.evaluateString(scope, source, name, 1, null);
			return r == null ? "null" : Context.toString(r);
		} catch (Throwable t) {
			throw convert(t);
		} finally {
			end();
			Context.exit();
		}
	}

	private void begin(long limitMs) {
		limitMillis = limitMs;
		deadlineNanos = System.nanoTime() + limitMs * 1_000_000L;
	}

	private void end() {
		deadlineNanos = Long.MAX_VALUE;
	}

	/** Releases the scope. The sandbox cannot be used afterwards. */
	public void close() {
		closed = true;
		scope = null;
	}

	public boolean isClosed() {
		return closed;
	}

	static ScriptFailure convert(Throwable t) {
		if (t instanceof ScriptFailure f) {
			return f;
		}
		if (t instanceof ScriptLimitError e) {
			return new ScriptFailure(ScriptFailure.Kind.TIMEOUT, e.getMessage(), e.line());
		}
		if (t instanceof EcmaError e) {
			return new ScriptFailure(ScriptFailure.Kind.RUNTIME, e.getName() + ": " + e.getErrorMessage(), e.lineNumber());
		}
		if (t instanceof RhinoException e) {
			return new ScriptFailure(ScriptFailure.Kind.RUNTIME, e.details(), e.lineNumber());
		}
		if (t instanceof StackOverflowError) {
			return new ScriptFailure(ScriptFailure.Kind.LIMIT, "Stack overflow (recursion too deep)", 0);
		}
		if (t instanceof OutOfMemoryError) {
			return new ScriptFailure(ScriptFailure.Kind.LIMIT, "Out of memory", 0);
		}
		if (t instanceof Error e) {
			throw e; // anything else (for example LinkageError) is not a script problem; do not hide it
		}
		return new ScriptFailure(ScriptFailure.Kind.INTERNAL, t.getClass().getSimpleName() + ": " + t.getMessage(), 0);
	}
}
