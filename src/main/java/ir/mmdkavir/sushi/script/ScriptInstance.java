package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.script.engine.RhinoSandbox;
import ir.mmdkavir.sushi.script.engine.ScriptFailure;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One running script: its sandbox, event listeners, timers and stop handlers. Everything here is
 * discarded when the script stops or reloads, so listeners and timers can never outlive their script.
 * Not thread-safe: use from the game thread only.
 */
public final class ScriptInstance {
	public static final Set<String> EVENT_NAMES = Set.of("tick", "chat", "join", "leave", "key");
	public static final int MAX_LISTENERS = 100;
	public static final int MAX_TIMERS = 100;
	public static final int MAX_STOP_HANDLERS = 20;
	private static final int LOG_LINES_PER_SECOND = 100;
	private static final int MAX_LOG_LENGTH = 2000;
	private static final int CHATS_PER_WINDOW = 10;
	private static final int CHAT_WINDOW_TICKS = 20;

	private record Listener(int id, Object fn, boolean once) {
	}

	private static final class Timer {
		final int id;
		final Object fn;
		final Object[] args;
		final int intervalTicks;
		final boolean repeat;
		long dueTick;

		Timer(int id, Object fn, Object[] args, int intervalTicks, boolean repeat, long dueTick) {
			this.id = id;
			this.fn = fn;
			this.args = args;
			this.intervalTicks = intervalTicks;
			this.repeat = repeat;
			this.dueTick = dueTick;
		}
	}

	private final ScriptManager owner;
	private final String id;
	private final ScriptMetadata metadata;
	private final Set<ScriptPermission> granted;
	private final String profileId;
	private RhinoSandbox sandbox;

	private final Map<String, List<Listener>> listeners = new LinkedHashMap<>();
	private final List<Timer> timers = new ArrayList<>();
	private final List<Object> stopHandlers = new ArrayList<>();
	private int nextId = 1;
	private long ticks;
	private int errorCount;
	private boolean stopRequested;
	private boolean running = true;

	private long logWindowStart = System.nanoTime();
	private int logCount;
	private boolean logDropWarned;
	private long chatWindowStartTick;
	private int chatCount;

	ScriptInstance(ScriptManager owner, String id, ScriptMetadata metadata, Set<ScriptPermission> granted, String profileId) {
		this.owner = owner;
		this.id = id;
		this.metadata = metadata;
		this.granted = Set.copyOf(granted);
		this.profileId = profileId;
	}

	void attach(RhinoSandbox sandbox) {
		this.sandbox = sandbox;
	}

	// ---- read access ---------------------------------------------------------------------------

	public String id() { return id; }
	public ScriptMetadata metadata() { return metadata; }
	public Set<ScriptPermission> granted() { return granted; }
	public String profileId() { return profileId; }
	public boolean isRunning() { return running; }
	public long ticks() { return ticks; }
	public int listenerCount() { return listeners.values().stream().mapToInt(List::size).sum(); }
	public int timerCount() { return timers.size(); }
	public int errorCount() { return errorCount; }
	boolean stopRequested() { return stopRequested; }
	GameBridge bridge() { return owner.bridge(); }
	ScriptDataStore data() { return owner.dataStore(); }
	String activeProfileName() { return owner.activeProfileName(); }

	boolean has(ScriptPermission p) {
		return granted.contains(p);
	}

	// ---- registration used by the API ----------------------------------------------------------

	int addListener(String event, Object fn, boolean once) {
		if (!EVENT_NAMES.contains(event)) {
			throw new IllegalArgumentException("Unknown event \"" + event + "\". Supported: " + String.join(", ", EVENT_NAMES.stream().sorted().toList()));
		}
		if (listenerCount() >= MAX_LISTENERS) {
			throw new IllegalStateException("Too many event listeners (max " + MAX_LISTENERS + ")");
		}
		int lid = nextId++;
		listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(new Listener(lid, fn, once));
		return lid;
	}

	boolean removeListener(int lid) {
		boolean removed = false;
		for (List<Listener> l : listeners.values()) {
			removed |= l.removeIf(x -> x.id == lid);
		}
		return removed;
	}

	int addTimer(Object fn, double ms, boolean repeat, Object[] args) {
		if (timers.size() >= MAX_TIMERS) {
			throw new IllegalStateException("Too many timers (max " + MAX_TIMERS + ")");
		}
		double clamped = Double.isNaN(ms) ? 0 : Math.max(0, Math.min(ms, 7 * 24 * 3600 * 1000.0));
		int t = (int) Math.max(1, Math.ceil(clamped / 50.0)); // one game tick is 50 ms
		int tid = nextId++;
		timers.add(new Timer(tid, fn, args, t, repeat, ticks + t));
		return tid;
	}

	boolean clearTimer(int tid) {
		return timers.removeIf(t -> t.id == tid);
	}

	void addStopHandler(Object fn) {
		if (stopHandlers.size() >= MAX_STOP_HANDLERS) {
			throw new IllegalStateException("Too many onStop handlers");
		}
		stopHandlers.add(fn);
	}

	void requestStop() {
		stopRequested = true;
	}

	/** Returns false when the script has used up its chat allowance for this moment. */
	boolean allowChat() {
		if (ticks - chatWindowStartTick >= CHAT_WINDOW_TICKS) {
			chatWindowStartTick = ticks;
			chatCount = 0;
		}
		return ++chatCount <= CHATS_PER_WINDOW;
	}

	// ---- logging ---------------------------------------------------------------------------------

	/** Writes a line to the Sushi console under this script's name; excess output is dropped. */
	public void log(ConsoleLog.Level level, String message) {
		long now = System.nanoTime();
		if (now - logWindowStart > 1_000_000_000L) {
			logWindowStart = now;
			logCount = 0;
			logDropWarned = false;
		}
		if (++logCount > LOG_LINES_PER_SECOND) {
			if (!logDropWarned) {
				logDropWarned = true;
				owner.console().logFrom(ConsoleLog.Level.WARN, id, "Output limit reached (" + LOG_LINES_PER_SECOND + " lines/second); further lines are dropped.");
			}
			return;
		}
		String m = message.length() > MAX_LOG_LENGTH ? message.substring(0, MAX_LOG_LENGTH) + "... (truncated)" : message;
		owner.console().logFrom(level, id, m);
	}

	// ---- running code ----------------------------------------------------------------------------

	/** Runs the script's top-level code. */
	void runMain(String source) throws ScriptFailure {
		sandbox.evaluate(source, id, owner.settings().scriptStartLimitMs);
	}

	/** Calls a script function with the callback time limit. Returns false if it failed; fatal failures stop the script. */
	private boolean invoke(Object fn, Object[] args, String what) {
		if (!running) {
			return false;
		}
		try {
			sandbox.call(fn, args, owner.settings().scriptCallbackLimitMs);
			return true;
		} catch (ScriptFailure f) {
			errorCount++;
			log(ConsoleLog.Level.ERROR, what + " failed - " + f.describe());
			if (f.fatal()) {
				owner.failAndStop(id, f);
			} else if (errorCount >= owner.settings().scriptMaxErrors) {
				owner.failAndStop(id, new ScriptFailure(ScriptFailure.Kind.LIMIT,
						"Stopped after " + errorCount + " errors", 0));
			}
			return false;
		}
	}

	/** Delivers an event to this script's listeners. */
	void dispatch(String event, Map<String, Object> payload) {
		List<Listener> list = listeners.get(event);
		if (list == null || list.isEmpty()) {
			return;
		}
		for (Listener l : new ArrayList<>(list)) {
			if (!running) {
				return;
			}
			if (l.once) {
				list.remove(l);
			} else if (!list.contains(l)) {
				continue; // removed by an earlier handler during this dispatch
			}
			invoke(l.fn, new Object[]{payload}, "Event \"" + event + "\" handler");
		}
	}

	/** Advances this script by one game tick: counts it and runs due timers. */
	void tick() {
		ticks++;
		for (Timer t : new ArrayList<>(timers)) {
			if (!running) {
				return;
			}
			if (t.dueTick > ticks || !timers.contains(t)) {
				continue;
			}
			if (t.repeat) {
				t.dueTick = ticks + t.intervalTicks;
			} else {
				timers.remove(t);
			}
			invoke(t.fn, t.args, (t.repeat ? "Interval" : "Timeout") + " callback");
		}
	}

	/** Runs onStop handlers, then discards the sandbox, listeners and timers. Safe to call twice. */
	void shutdown() {
		if (!running) {
			return;
		}
		List<Object> handlers = new ArrayList<>(stopHandlers);
		// Handlers run while still "running" so errors are reported, but they cannot schedule anything lasting.
		for (Object h : handlers) {
			try {
				sandbox.call(h, new Object[0], owner.settings().scriptCallbackLimitMs);
			} catch (ScriptFailure f) {
				log(ConsoleLog.Level.ERROR, "onStop handler failed - " + f.describe());
			}
		}
		running = false;
		listeners.clear();
		timers.clear();
		stopHandlers.clear();
		sandbox.close();
	}
}
