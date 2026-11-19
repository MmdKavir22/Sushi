package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.SushiCore;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared helpers: a real core in a temp folder and a plain-data {@link GameBridge} (the JavaScript engine is the real one). */
final class ScriptTestSupport {
	private ScriptTestSupport() {
	}

	static final class TestBridge implements GameBridge {
		boolean inGame = true;
		String name = "Steve";
		double[] pos = {10.5, 64, -3.25};
		final List<String> chats = new ArrayList<>();
		final List<String> commands = new ArrayList<>();
		final List<String> messages = new ArrayList<>();
		final Set<Integer> down = new HashSet<>();

		@Override public boolean inGame() { return inGame; }
		@Override public String playerName() { return name; }
		@Override public double[] playerPosition() { return pos; }
		@Override public double[] playerRotation() { return new double[]{90, 10}; }
		@Override public double playerHealth() { return 18; }
		@Override public int playerFood() { return 15; }
		@Override public String dimension() { return "minecraft:overworld"; }
		@Override public long gameTime() { return 1234; }
		@Override public boolean raining() { return false; }
		@Override public String blockAt(int x, int y, int z) { return "minecraft:stone"; }
		@Override public boolean isKeyDown(int code) { return down.contains(code); }
		@Override public void sendChat(String m) { chats.add(m); }
		@Override public void sendCommand(String c) { commands.add(c); }
		@Override public void showMessage(String t, boolean bar) { messages.add((bar ? "bar:" : "chat:") + t); }
	}

	static SushiCore core(Path tmp, TestBridge bridge) throws Exception {
		SushiCore core = SushiCore.create(tmp);
		core.scripts().setBridge(bridge);
		return core;
	}

	/** Creates a script file and returns its id. */
	static String add(SushiCore core, String name, String source) {
		var r = core.scripts().store().create(name, source);
		if (!r.ok()) {
			throw new AssertionError("create failed: " + r.message());
		}
		return r.message();
	}

	/** Creates, approves and starts a script; returns its id. */
	static String run(SushiCore core, String name, String source) {
		String id = add(core, name, source);
		core.scripts().approve(id);
		var r = core.scripts().start(id);
		if (!r.ok()) {
			throw new AssertionError("start failed: " + r.message());
		}
		return id;
	}

	static List<String> out(SushiCore core, String id) {
		return core.console().snapshot(null, id).stream().map(ConsoleLog.Entry::message).toList();
	}

	static List<String> outAt(SushiCore core, String id, ConsoleLog.Level level) {
		return core.console().snapshot(level, id).stream().map(ConsoleLog.Entry::message).toList();
	}
}
