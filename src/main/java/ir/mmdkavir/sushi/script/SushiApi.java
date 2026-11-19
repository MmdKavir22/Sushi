package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.script.engine.RhinoSandbox;
import ir.mmdkavir.sushi.util.ConsoleLog;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Builds the JavaScript API exposed to scripts: global helpers plus the {@code Sushi, Player, World,
 * Input, Events, Timer, UI} and {@code Storage} namespaces. Every function is a plain JavaScript
 * function object backed by Java code in this file; no Java object or class is ever visible to a
 * script. Functions that need a permission throw a JavaScript {@code TypeError} when it was not granted.
 * docs/SCRIPT_API.md documents each function and must be kept in sync with this file.
 */
final class SushiApi {
	static final int API_VERSION = 1;

	private interface Body {
		Object run(Context cx, Object[] args);
	}

	private static final class Fn extends BaseFunction {
		private final String name;
		private final Body body;

		Fn(String name, Scriptable scope, Body body) {
			this.name = name;
			this.body = body;
			setPrototype(ScriptableObject.getFunctionPrototype(scope));
			setParentScope(scope);
		}

		@Override
		public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
			try {
				Object r = body.run(cx, args);
				return r == null ? null : r;
			} catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
				throw ScriptRuntime.typeError(name + ": " + e.getMessage());
			}
		}

		@Override
		public String getFunctionName() {
			return name;
		}
	}

	private final ScriptInstance inst;
	private final Scriptable scope;

	private SushiApi(ScriptInstance inst, Scriptable scope) {
		this.inst = inst;
		this.scope = scope;
	}

	static RhinoSandbox.Installer installer(ScriptInstance inst) {
		return (cx, scope) -> new SushiApi(inst, scope).install(cx, scope);
	}

	// ---- argument helpers ------------------------------------------------------------------------

	private static boolean present(Object[] a, int i) {
		return i < a.length && a[i] != null && !Undefined.isUndefined(a[i]);
	}

	private static String str(Object[] a, int i, String what) {
		if (!present(a, i)) {
			throw new IllegalArgumentException(what + " is required");
		}
		return Context.toString(a[i]);
	}

	private static double num(Object[] a, int i, String what) {
		if (!present(a, i)) {
			throw new IllegalArgumentException(what + " is required");
		}
		double d = Context.toNumber(a[i]);
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			throw new IllegalArgumentException(what + " must be a finite number");
		}
		return d;
	}

	private static Object fn(Object[] a, int i, String what) {
		if (!present(a, i) || !(a[i] instanceof Function)) {
			throw new IllegalArgumentException(what + " must be a function");
		}
		return a[i];
	}

	private static String joinArgs(Object[] a) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(Undefined.isUndefined(a[i]) ? "undefined" : Context.toString(a[i]));
		}
		return sb.toString();
	}

	private void need(ScriptPermission p) {
		if (!inst.has(p)) {
			throw new IllegalStateException("permission \"" + p.id + "\" is not granted to this script");
		}
	}

	private GameBridge game() {
		GameBridge b = inst.bridge();
		if (!b.inGame()) {
			throw new IllegalStateException("not connected to a world");
		}
		return b;
	}

	private void def(ScriptableObject target, String name, Body body) {
		target.defineProperty(name, new Fn(name, scope, body), ScriptableObject.READONLY | ScriptableObject.PERMANENT);
	}

	private ScriptableObject namespace(Context cx, ScriptableObject global, String name) {
		ScriptableObject ns = (ScriptableObject) cx.newObject(scope);
		global.defineProperty(name, ns, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
		return ns;
	}

	private Scriptable obj(Context cx, Object... kv) {
		Scriptable o = cx.newObject(scope);
		for (int i = 0; i < kv.length; i += 2) {
			ScriptableObject.putProperty(o, (String) kv[i], kv[i + 1]);
		}
		return o;
	}

	private Object array(Context cx, List<?> items) {
		return cx.newArray(scope, items.toArray());
	}

	// ---- installation ------------------------------------------------------------------------------

	private void install(Context cx, ScriptableObject global) {
		installGlobals(cx, global);
		installSushi(cx, global);
		installPlayer(cx, global);
		installWorld(cx, global);
		installInput(cx, global);
		installEvents(cx, global);
		installTimer(cx, global);
		installUi(cx, global);
		installStorage(cx, global);
	}

	private void installGlobals(Context cx, ScriptableObject global) {
		def(global, "print", (c, a) -> {
			inst.log(ConsoleLog.Level.INFO, joinArgs(a));
			return Undefined.instance;
		});
		ScriptableObject console = namespace(cx, global, "console");
		def(console, "log", (c, a) -> { inst.log(ConsoleLog.Level.INFO, joinArgs(a)); return Undefined.instance; });
		def(console, "info", (c, a) -> { inst.log(ConsoleLog.Level.INFO, joinArgs(a)); return Undefined.instance; });
		def(console, "warn", (c, a) -> { inst.log(ConsoleLog.Level.WARN, joinArgs(a)); return Undefined.instance; });
		def(console, "error", (c, a) -> { inst.log(ConsoleLog.Level.ERROR, joinArgs(a)); return Undefined.instance; });
		def(console, "debug", (c, a) -> { inst.log(ConsoleLog.Level.DEBUG, joinArgs(a)); return Undefined.instance; });
		console.sealObject();

		def(global, "setTimeout", (c, a) -> timer(a, false));
		def(global, "setInterval", (c, a) -> timer(a, true));
		def(global, "clearTimeout", (c, a) -> clear(a));
		def(global, "clearInterval", (c, a) -> clear(a));
	}

	private Object timer(Object[] a, boolean repeat) {
		Object f = fn(a, 0, "callback");
		double ms = present(a, 1) ? Context.toNumber(a[1]) : 0;
		Object[] extra = a.length > 2 ? java.util.Arrays.copyOfRange(a, 2, a.length) : new Object[0];
		return (double) inst.addTimer(f, ms, repeat, extra);
	}

	private Object clear(Object[] a) {
		if (!present(a, 0)) {
			return false;
		}
		double d = Context.toNumber(a[0]);
		return !Double.isNaN(d) && inst.clearTimer((int) d);
	}

	private void installSushi(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "Sushi");
		ns.defineProperty("apiVersion", (double) API_VERSION, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
		ns.defineProperty("scriptId", inst.id(), ScriptableObject.READONLY | ScriptableObject.PERMANENT);
		ns.defineProperty("scriptName", inst.metadata().name(), ScriptableObject.READONLY | ScriptableObject.PERMANENT);
		ns.defineProperty("scriptVersion", inst.metadata().version(), ScriptableObject.READONLY | ScriptableObject.PERMANENT);
		def(ns, "inGame", (c, a) -> inst.bridge().inGame());
		def(ns, "profile", (c, a) -> inst.activeProfileName());
		def(ns, "hasPermission", (c, a) -> {
			ScriptPermission p = ScriptPermission.byId(str(a, 0, "permission"));
			return p != null && inst.has(p);
		});
		def(ns, "permissions", (c, a) -> array(c, ScriptPermission.ids(inst.granted())));
		def(ns, "onStop", (c, a) -> {
			inst.addStopHandler(fn(a, 0, "handler"));
			return Undefined.instance;
		});
		def(ns, "stop", (c, a) -> {
			inst.requestStop();
			return Undefined.instance;
		});
		ns.sealObject();
	}

	private void installPlayer(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "Player");
		def(ns, "name", (c, a) -> {
			need(ScriptPermission.PLAYER_READ);
			return game().playerName();
		});
		def(ns, "position", (c, a) -> {
			need(ScriptPermission.PLAYER_READ);
			double[] p = game().playerPosition();
			return obj(c, "x", p[0], "y", p[1], "z", p[2]);
		});
		def(ns, "rotation", (c, a) -> {
			need(ScriptPermission.PLAYER_READ);
			double[] r = game().playerRotation();
			return obj(c, "yaw", r[0], "pitch", r[1]);
		});
		def(ns, "health", (c, a) -> {
			need(ScriptPermission.PLAYER_READ);
			return game().playerHealth();
		});
		def(ns, "food", (c, a) -> {
			need(ScriptPermission.PLAYER_READ);
			return (double) game().playerFood();
		});
		def(ns, "chat", (c, a) -> {
			need(ScriptPermission.CHAT_SEND);
			String m = checkChat(str(a, 0, "message"));
			if (m.startsWith("/")) {
				throw new IllegalArgumentException("messages cannot start with \"/\"; use Player.command()");
			}
			sendLimited(() -> game().sendChat(m));
			return Undefined.instance;
		});
		def(ns, "command", (c, a) -> {
			need(ScriptPermission.CHAT_SEND);
			String m = checkChat(str(a, 0, "command"));
			String cmd = m.startsWith("/") ? m.substring(1) : m;
			if (cmd.isBlank()) {
				throw new IllegalArgumentException("command is empty");
			}
			sendLimited(() -> game().sendCommand(cmd));
			return Undefined.instance;
		});
		ns.sealObject();
	}

	private static String checkChat(String m) {
		if (m.isBlank() || m.length() > 256) {
			throw new IllegalArgumentException("text must be 1-256 characters");
		}
		for (int i = 0; i < m.length(); i++) {
			if (Character.isISOControl(m.charAt(i))) {
				throw new IllegalArgumentException("text contains control characters");
			}
		}
		return m;
	}

	private void sendLimited(Runnable send) {
		if (!inst.allowChat()) {
			throw new IllegalStateException("chat rate limit exceeded (10 messages per second)");
		}
		send.run();
	}

	private void installWorld(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "World");
		def(ns, "dimension", (c, a) -> { need(ScriptPermission.WORLD_READ); return game().dimension(); });
		def(ns, "time", (c, a) -> { need(ScriptPermission.WORLD_READ); return (double) game().gameTime(); });
		def(ns, "isRaining", (c, a) -> { need(ScriptPermission.WORLD_READ); return game().raining(); });
		def(ns, "blockAt", (c, a) -> {
			need(ScriptPermission.WORLD_READ);
			int x = (int) Math.floor(num(a, 0, "x"));
			int y = (int) Math.floor(num(a, 1, "y"));
			int z = (int) Math.floor(num(a, 2, "z"));
			GameBridge g = game();
			double[] p = g.playerPosition();
			if (Math.abs(x - p[0]) > 64 || Math.abs(y - p[1]) > 64 || Math.abs(z - p[2]) > 64) {
				throw new IllegalArgumentException("position is more than 64 blocks from the player");
			}
			return g.blockAt(x, y, z);
		});
		ns.sealObject();
	}

	private void installInput(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "Input");
		def(ns, "isKeyDown", (c, a) -> {
			need(ScriptPermission.INPUT_READ);
			String name = str(a, 0, "key");
			int code = KeyNames.code(name);
			if (code < 0) {
				throw new IllegalArgumentException("unknown key \"" + name + "\" (see Input.keyNames())");
			}
			return inst.bridge().isKeyDown(code);
		});
		def(ns, "keyNames", (c, a) -> array(c, List.copyOf(KeyNames.all().keySet())));
		ns.sealObject();
	}

	private void installEvents(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "Events");
		def(ns, "on", (c, a) -> (double) listen(a, false));
		def(ns, "once", (c, a) -> (double) listen(a, true));
		def(ns, "off", (c, a) -> {
			double d = num(a, 0, "id");
			return inst.removeListener((int) d);
		});
		def(ns, "names", (c, a) -> array(c, ScriptInstance.EVENT_NAMES.stream().sorted().toList()));
		ns.sealObject();
	}

	private int listen(Object[] a, boolean once) {
		String event = str(a, 0, "event").toLowerCase(Locale.ROOT);
		Object f = fn(a, 1, "handler");
		if (event.equals("chat")) {
			need(ScriptPermission.CHAT_READ);
		} else if (event.equals("key")) {
			need(ScriptPermission.INPUT_READ);
		}
		return inst.addListener(event, f, once);
	}

	private void installTimer(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "Timer");
		def(ns, "setTimeout", (c, a) -> timer(a, false));
		def(ns, "setInterval", (c, a) -> timer(a, true));
		def(ns, "clear", (c, a) -> clear(a));
		def(ns, "now", (c, a) -> (double) System.currentTimeMillis());
		def(ns, "ticks", (c, a) -> (double) inst.ticks());
		ns.sealObject();
	}

	private void installUi(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "UI");
		def(ns, "message", (c, a) -> {
			need(ScriptPermission.UI_MESSAGE);
			game().showMessage(checkText(str(a, 0, "text")), false);
			return Undefined.instance;
		});
		def(ns, "actionBar", (c, a) -> {
			need(ScriptPermission.UI_MESSAGE);
			game().showMessage(checkText(str(a, 0, "text")), true);
			return Undefined.instance;
		});
		ns.sealObject();
	}

	private static String checkText(String t) {
		if (t.length() > 256) {
			t = t.substring(0, 256);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < t.length(); i++) {
			char ch = t.charAt(i);
			sb.append(Character.isISOControl(ch) ? ' ' : ch);
		}
		return sb.toString();
	}

	private void installStorage(Context cx, ScriptableObject global) {
		ScriptableObject ns = namespace(cx, global, "Storage");
		def(ns, "get", (c, a) -> {
			need(ScriptPermission.STORAGE);
			return inst.data().get(inst.profileId(), inst.id(), str(a, 0, "key"));
		});
		def(ns, "set", (c, a) -> {
			need(ScriptPermission.STORAGE);
			String key = str(a, 0, "key");
			if (!present(a, 1)) {
				throw new IllegalArgumentException("value is required (string, number or boolean)");
			}
			Object v = a[1];
			Object stored;
			if (v instanceof Boolean || v instanceof Number) {
				stored = v;
			} else if (v instanceof CharSequence) {
				stored = v.toString();
			} else {
				throw new IllegalArgumentException("value must be a string, number or boolean");
			}
			try {
				inst.data().set(inst.profileId(), inst.id(), key, stored);
			} catch (IOException e) {
				throw new IllegalStateException("could not write storage: " + e.getMessage());
			}
			return Undefined.instance;
		});
		def(ns, "remove", (c, a) -> {
			need(ScriptPermission.STORAGE);
			try {
				return inst.data().remove(inst.profileId(), inst.id(), str(a, 0, "key"));
			} catch (IOException e) {
				throw new IllegalStateException("could not write storage: " + e.getMessage());
			}
		});
		def(ns, "keys", (c, a) -> {
			need(ScriptPermission.STORAGE);
			return array(c, inst.data().keys(inst.profileId(), inst.id()));
		});
		ns.sealObject();
	}
}
