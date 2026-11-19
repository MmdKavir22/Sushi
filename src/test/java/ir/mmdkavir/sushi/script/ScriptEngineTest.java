package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.SushiCore;
import ir.mmdkavir.sushi.script.engine.ScriptFailure;
import ir.mmdkavir.sushi.util.ConsoleLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static ir.mmdkavir.sushi.script.ScriptTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs real JavaScript in the real Rhino engine through ScriptManager. Only the game is replaced by plain data. */
class ScriptEngineTest {
	@TempDir
	Path tmp;

	private final TestBridge game = new TestBridge();

	private SushiCore core() throws Exception {
		return ScriptTestSupport.core(tmp, game);
	}

	@Test
	void executesScriptAndPrintsWithScriptName() throws Exception {
		SushiCore core = core();
		String id = run(core, "hello", "print('a', 1 + 2, true); console.warn('careful'); console.error('bad');");
		assertEquals(List.of("a 3 true"), outAt(core, id, ConsoleLog.Level.INFO).stream().filter(m -> m.startsWith("a ")).toList());
		assertEquals(List.of("careful"), outAt(core, id, ConsoleLog.Level.WARN));
		assertTrue(outAt(core, id, ConsoleLog.Level.ERROR).contains("bad"));
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(id));
		assertTrue(core.console().sources().contains(id));
	}

	@Test
	void modernSyntaxWorks() throws Exception {
		SushiCore core = core();
		String id = run(core, "es6", "const f = (a, b) => a * b; let n = f(6, 7); print(`n=${n}`); print(JSON.stringify({a: [1, 2]}));");
		assertTrue(out(core, id).contains("n=42"));
		assertTrue(out(core, id).contains("{\"a\":[1,2]}"));
	}

	@Test
	void syntaxErrorsReportLine() throws Exception {
		SushiCore core = core();
		String id = add(core, "bad", "var a = 1;\nvar = ;\n");
		core.scripts().approve(id);
		assertFalse(core.scripts().start(id).ok());
		assertEquals(ScriptManager.Status.ERROR, core.scripts().status(id));
		ScriptFailure f = core.scripts().lastFailure(id);
		assertEquals(ScriptFailure.Kind.SYNTAX, f.kind());
		assertEquals(2, f.line());
		ScriptFailure check = core.scripts().checkSyntax(id, "var a = ;");
		assertNotNull(check);
		assertEquals(1, check.line());
		assertNull(core.scripts().checkSyntax(id, "var a = 1;"));
	}

	@Test
	void runtimeErrorsReportLineAndDoNotCrash() throws Exception {
		SushiCore core = core();
		String id = add(core, "boom", "var x = 1;\nundefinedFunction();\n");
		core.scripts().approve(id);
		assertFalse(core.scripts().start(id).ok());
		ScriptFailure f = core.scripts().lastFailure(id);
		assertEquals(ScriptFailure.Kind.RUNTIME, f.kind());
		assertEquals(2, f.line());
		assertTrue(f.getMessage().contains("undefinedFunction"), f.getMessage());
		assertEquals(0, core.scripts().runningCount());

		String thrown = add(core, "thrower", "throw new Error('custom failure');");
		assertFalse(core.scripts().start(thrown).ok());
		assertTrue(core.scripts().lastFailure(thrown).getMessage().contains("custom failure"));
	}

	@Test
	@Timeout(30)
	void infiniteLoopsAreStopped() throws Exception {
		SushiCore core = core();
		core.config().get().scriptStartLimitMs = 200;
		String id = add(core, "spin", "while (true) {}");
		assertFalse(core.scripts().start(id).ok());
		assertEquals(ScriptFailure.Kind.TIMEOUT, core.scripts().lastFailure(id).kind());
		assertEquals(0, core.scripts().runningCount());

		String sneaky = add(core, "sneaky", "while (true) { try { while (true) {} } catch (e) {} finally {} }");
		assertFalse(core.scripts().start(sneaky).ok());
		assertEquals(ScriptFailure.Kind.TIMEOUT, core.scripts().lastFailure(sneaky).kind(), "try/catch cannot swallow the timeout");
	}

	@Test
	@Timeout(30)
	void runawayRecursionIsContained() throws Exception {
		SushiCore core = core();
		String id = add(core, "recurse", "function f(){ return f() + 1; } f();");
		assertFalse(core.scripts().start(id).ok());
		assertEquals(0, core.scripts().runningCount());
	}

	@Test
	@Timeout(30)
	void slowCallbackStopsOnlyThatScript() throws Exception {
		SushiCore core = core();
		core.config().get().scriptCallbackLimitMs = 50;
		String bad = run(core, "slow", "Events.on('tick', function () { while (true) {} });");
		String good = run(core, "fine", "var n = 0; Events.on('tick', function () { n++; if (n === 2) print('second tick'); });");
		core.scripts().tick();
		assertEquals(ScriptManager.Status.ERROR, core.scripts().status(bad));
		assertEquals(ScriptFailure.Kind.TIMEOUT, core.scripts().lastFailure(bad).kind());
		core.scripts().tick();
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(good));
		assertTrue(out(core, good).contains("second tick"));
	}

	@Test
	void noJavaAccess() throws Exception {
		SushiCore core = core();
		String id = run(core, "probe",
				"print([typeof Packages, typeof java, typeof Java, typeof importClass, typeof importPackage, typeof load, typeof require, typeof readFile, typeof quit, typeof Sushi.getClass, typeof print.getClass].join(','));");
		assertTrue(out(core, id).contains("undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined"),
				String.valueOf(out(core, id)));
	}

	@Test
	void apiObjectsCannotBeReplaced() throws Exception {
		SushiCore core = core();
		String id = run(core, "tamper",
				"try { Player.name = function () { return 'evil'; }; } catch (e) {}\n"
						+ "try { Sushi = null; } catch (e) {}\n"
						+ "try { delete Events.on; } catch (e) {}\n"
						+ "print(typeof Player.name, typeof Sushi, typeof Events.on);");
		assertTrue(out(core, id).contains("function object function"), String.valueOf(out(core, id)));
	}

	@Test
	void scriptsAreIsolatedFromEachOther() throws Exception {
		SushiCore core = core();
		String a = run(core, "a", "var shared = 42; Object.prototype.polluted = 'yes'; Math.floor = null;");
		String b = run(core, "b", "print(typeof shared, ({}).polluted, typeof Math.floor);");
		assertTrue(out(core, b).contains("undefined undefined function"), String.valueOf(out(core, b)));
		assertEquals(2, core.scripts().runningCount());
		core.scripts().stop(a);
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(b));
	}

	@Test
	void permissionsGateTheApi() throws Exception {
		SushiCore core = core();
		String id = run(core, "noperm",
				"function t(f) { try { f(); return 'allowed'; } catch (e) { return 'denied'; } }\n"
						+ "print(t(function () { Player.name(); }), t(function () { World.time(); }), t(function () { Input.isKeyDown('W'); }),\n"
						+ "  t(function () { Player.chat('hi'); }), t(function () { UI.message('hi'); }), t(function () { Storage.get('k'); }),\n"
						+ "  t(function () { Events.on('chat', function () {}); }), t(function () { Events.on('key', function () {}); }),\n"
						+ "  t(function () { Events.on('tick', function () {}); }));");
		assertTrue(out(core, id).contains("denied denied denied denied denied denied denied denied allowed"), String.valueOf(out(core, id)));
		assertTrue(game.chats.isEmpty());
		assertTrue(game.messages.isEmpty());
		String msg = add(core, "msg", "try { Player.name(); } catch (e) { print(e.message); }");
		core.scripts().start(msg);
		assertTrue(out(core, msg).stream().anyMatch(m -> m.contains("player.read")), String.valueOf(out(core, msg)));
	}

	@Test
	void unapprovedScriptsDoNotStartAndHeaderEditsNeedNewApproval() throws Exception {
		SushiCore core = core();
		String id = add(core, "needs", "// @permissions player.read\nprint(Player.name());");
		assertFalse(core.scripts().start(id).ok());
		assertEquals(ScriptManager.Status.NEEDS_APPROVAL, core.scripts().status(id));
		assertEquals(0, core.scripts().runningCount());
		core.scripts().approve(id);
		assertTrue(core.scripts().start(id).ok());
		assertTrue(out(core, id).contains("Steve"));
		core.scripts().stop(id);

		core.scripts().save(id, "// @permissions player.read, chat.send\nPlayer.chat('x');");
		assertEquals(ScriptManager.Status.NEEDS_APPROVAL, core.scripts().status(id), "new permission needs a new approval");
		assertFalse(core.scripts().start(id).ok());

		core.scripts().save(id, "try { Player.name(); print('allowed'); } catch (e) { print('denied'); }");
		core.scripts().start(id);
		assertTrue(out(core, id).contains("denied"), "approval only applies to permissions the header still asks for");
	}

	@Test
	void playerWorldInputUiAndChatApisWork() throws Exception {
		SushiCore core = core();
		game.down.add(87);
		String id = add(core, "api", "// @permissions player.read, world.read, input.read, ui.message, chat.send\n"
				+ "var p = Player.position(); var r = Player.rotation();\n"
				+ "print(Player.name(), p.x, p.y, p.z, r.yaw, r.pitch, Player.health(), Player.food());\n"
				+ "print(World.dimension(), World.time(), World.isRaining(), World.blockAt(10, 64, -3));\n"
				+ "print(Input.isKeyDown('w'), Input.isKeyDown('S'), Input.keyNames().length > 50);\n"
				+ "UI.message('hello'); UI.actionBar('bar');\n"
				+ "Player.chat('hi all'); Player.command('/home base'); Player.command('time set day');\n"
				+ "print(Sushi.apiVersion, Sushi.scriptId, Sushi.scriptName, Sushi.inGame(), Sushi.hasPermission('chat.send'), Sushi.hasPermission('storage'), Sushi.permissions().length, Sushi.profile());\n");
		core.scripts().approve(id);
		assertTrue(core.scripts().start(id).ok(), String.valueOf(core.scripts().lastFailure(id)));
		List<String> o = out(core, id);
		assertTrue(o.contains("Steve 10.5 64 -3.25 90 10 18 15"), o.toString());
		assertTrue(o.contains("minecraft:overworld 1234 false minecraft:stone"), o.toString());
		assertTrue(o.contains("true false true"), o.toString());
		assertEquals(List.of("chat:hello", "bar:bar"), game.messages);
		assertEquals(List.of("hi all"), game.chats);
		assertEquals(List.of("home base", "time set day"), game.commands);
		assertTrue(o.contains("1 api.sushi.js api true true false 5 Default"), o.toString());
	}

	@Test
	void chatInputValidationAndRateLimit() throws Exception {
		SushiCore core = core();
		String id = add(core, "chatty", "// @permissions chat.send\n"
				+ "function t(f) { try { f(); return 'ok'; } catch (e) { return 'denied'; } }\n"
				+ "print(t(function () { Player.chat('/op me'); }), t(function () { Player.chat(''); }), t(function () { Player.chat('x'.repeat(300)); }), t(function () { Player.command(''); }));\n"
				+ "var ok = 0; for (var i = 0; i < 15; i++) { if (t(function () { Player.chat('m' + i); }) === 'ok') ok++; }\n"
				+ "print('sent', ok);");
		core.scripts().approve(id);
		core.scripts().start(id);
		assertTrue(out(core, id).contains("denied denied denied denied"), out(core, id).toString());
		assertTrue(out(core, id).contains("sent 10"), out(core, id).toString());
		assertEquals(10, game.chats.size());
	}

	@Test
	void worldFunctionsFailCleanlyOutsideAWorld() throws Exception {
		SushiCore core = core();
		game.inGame = false;
		String id = add(core, "lobby", "// @permissions player.read\ntry { Player.name(); print('no'); } catch (e) { print('error: ' + e.message); }\nprint(Sushi.inGame());");
		core.scripts().approve(id);
		assertTrue(core.scripts().start(id).ok());
		assertTrue(out(core, id).stream().anyMatch(m -> m.startsWith("error:") && m.contains("not connected")), out(core, id).toString());
		assertTrue(out(core, id).contains("false"));
	}

	@Test
	void blockLookupIsLimitedToNearbyPositions() throws Exception {
		SushiCore core = core();
		String id = add(core, "far", "// @permissions world.read\n// @permissions player.read\ntry { World.blockAt(1000, 64, 0); print('far ok'); } catch (e) { print('far denied'); }");
		core.scripts().approve(id);
		core.scripts().start(id);
		assertTrue(out(core, id).contains("far denied"));
	}

	@Test
	void eventsReachListenersWithPayloads() throws Exception {
		SushiCore core = core();
		String id = add(core, "ev", "// @permissions chat.read, input.read\n"
				+ "Events.on('tick', function (e) { if (e.tick === 1) print('tick1'); });\n"
				+ "Events.on('join', function () { print('joined'); });\n"
				+ "Events.on('leave', function () { print('left'); });\n"
				+ "Events.on('chat', function (e) { print('chat:' + e.message + ':' + e.kind); });\n"
				+ "Events.on('key', function (e) { print('key:' + e.key + ':' + e.action); });\n"
				+ "Events.once('join', function () { print('once'); });\n"
				+ "var off = Events.on('join', function () { print('never'); }); print(Events.off(off), Events.off(off));\n"
				+ "print(Events.names().join(','));");
		core.scripts().approve(id);
		assertTrue(core.scripts().start(id).ok(), String.valueOf(core.scripts().lastFailure(id)));
		assertTrue(out(core, id).contains("true false"));
		assertTrue(out(core, id).contains("chat,join,key,leave,tick"));
		core.scripts().tick();
		core.scripts().dispatch("join", Map.of());
		core.scripts().dispatch("join", Map.of());
		core.scripts().dispatch("chat", Map.of("message", "hello", "kind", "player"));
		core.scripts().dispatch("key", Map.of("key", "W", "action", "press"));
		core.scripts().dispatch("leave", Map.of());
		List<String> o = out(core, id);
		assertEquals(1, o.stream().filter("tick1"::equals).count());
		assertEquals(2, o.stream().filter("joined"::equals).count());
		assertEquals(1, o.stream().filter("once"::equals).count());
		assertFalse(o.contains("never"));
		assertTrue(o.contains("chat:hello:player"));
		assertTrue(o.contains("key:W:press"));
		assertTrue(o.contains("left"));
	}

	@Test
	void chatAndKeyEventsAreWithheldWithoutPermission() throws Exception {
		SushiCore core = core();
		String id = run(core, "quiet", "Events.on('join', function () { print('join ok'); });");
		core.scripts().dispatch("chat", Map.of("message", "secret"));
		core.scripts().dispatch("key", Map.of("key", "W", "action", "press"));
		core.scripts().dispatch("join", Map.of());
		assertEquals(List.of("join ok"), out(core, id).stream().filter(m -> m.contains("join") || m.contains("secret")).filter(m -> !m.startsWith("Started")).toList());
	}

	@Test
	void unknownEventNamesAreRejected() throws Exception {
		SushiCore core = core();
		String id = run(core, "unk", "try { Events.on('explode', function () {}); print('accepted'); } catch (e) { print('rejected'); }\n"
				+ "try { Events.on('tick', 5); print('accepted'); } catch (e) { print('rejected'); }");
		assertEquals(2, out(core, id).stream().filter("rejected"::equals).count());
	}

	@Test
	void timeoutsAndIntervalsRunOnGameTicks() throws Exception {
		SushiCore core = core();
		String id = run(core, "timers",
				"setTimeout(function (a, b) { print('t100', a, b); }, 100, 'x', 'y');\n"
						+ "setTimeout(function () { print('t0'); }, 0);\n"
						+ "var n = 0; var iv = setInterval(function () { n++; print('iv' + n); if (n === 3) clearInterval(iv); }, 50);\n"
						+ "var gone = setTimeout(function () { print('cancelled'); }, 50); print(clearTimeout(gone));\n"
						+ "Timer.setTimeout(function () { print('viaTimer'); }, 50);\n"
						+ "print(typeof Timer.now(), Timer.ticks());");
		List<String> o0 = out(core, id);
		assertTrue(o0.contains("true"));
		assertTrue(o0.contains("number 0"));
		assertFalse(o0.contains("t0"), "timers never fire during the starting call");
		core.scripts().tick();
		assertTrue(out(core, id).contains("t0"));
		assertTrue(out(core, id).contains("iv1"));
		assertTrue(out(core, id).contains("viaTimer"));
		assertFalse(out(core, id).contains("t100 x y"));
		for (int i = 0; i < 6; i++) {
			core.scripts().tick();
		}
		List<String> o = out(core, id);
		assertTrue(o.contains("t100 x y"));
		assertEquals(1, o.stream().filter("iv3"::equals).count());
		assertFalse(o.contains("iv4"), "cleared interval must stop");
		assertFalse(o.contains("cancelled"));
	}

	@Test
	void stopAndReloadRemoveListenersAndTimers() throws Exception {
		SushiCore core = core();
		String src = "print('boot'); Events.on('tick', function () { print('tick'); }); setInterval(function () { print('interval'); }, 50);\n"
				+ "Events.on('join', function () { print('join'); }); Sushi.onStop(function () { print('bye'); });";
		String id = run(core, "life", src);
		core.scripts().tick();
		ScriptInstance first = core.scripts().instance(id);
		assertEquals(1, first.timerCount());
		assertEquals(2, first.listenerCount());
		long ticksBefore = out(core, id).stream().filter("tick"::equals).count();
		assertEquals(1, ticksBefore);

		assertTrue(core.scripts().reload(id).ok());
		assertTrue(out(core, id).contains("bye"), "onStop handler runs on reload");
		assertFalse(first.isRunning());
		assertEquals(0, first.timerCount());
		assertEquals(0, first.listenerCount());
		core.scripts().tick();
		assertEquals(2, out(core, id).stream().filter("tick"::equals).count(), "exactly one new tick handler after reload");
		assertEquals(2, out(core, id).stream().filter("interval"::equals).count());

		assertTrue(core.scripts().stop(id).ok());
		int total = out(core, id).size();
		core.scripts().tick();
		core.scripts().dispatch("join", Map.of());
		assertEquals(total, out(core, id).size(), "a stopped script receives nothing");
		assertEquals(ScriptManager.Status.STOPPED, core.scripts().status(id));
		assertFalse(core.scripts().stop(id).ok());
	}

	@Test
	void scriptCanStopItself() throws Exception {
		SushiCore core = core();
		String once = run(core, "once", "print('done'); Sushi.stop();");
		assertEquals(0, core.scripts().runningCount());
		assertEquals(ScriptManager.Status.STOPPED, core.scripts().status(once));
		String later = run(core, "later", "var n = 0; Events.on('tick', function () { if (++n === 2) Sushi.stop(); });");
		core.scripts().tick();
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(later));
		core.scripts().tick();
		assertEquals(ScriptManager.Status.STOPPED, core.scripts().status(later));
	}

	@Test
	void handlerErrorsAreReportedAndRepeatedFailureStopsTheScript() throws Exception {
		SushiCore core = core();
		core.config().get().scriptMaxErrors = 3;
		String id = run(core, "flaky", "Events.on('tick', function () { nope(); });");
		core.scripts().tick();
		core.scripts().tick();
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(id), "a single failing callback does not stop the script");
		assertEquals(2, outAt(core, id, ConsoleLog.Level.ERROR).stream().filter(m -> m.contains("nope")).count());
		core.scripts().tick();
		assertEquals(ScriptManager.Status.ERROR, core.scripts().status(id));
		assertNotNull(core.scripts().lastFailure(id));
	}

	@Test
	void failingScriptDoesNotAffectOthers() throws Exception {
		SushiCore core = core();
		String good = run(core, "good", "Events.on('tick', function () { print('alive'); });");
		String bad = run(core, "bad", "Events.on('tick', function () { throw new Error('x'); });");
		core.scripts().tick();
		assertTrue(out(core, good).contains("alive"));
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(bad));
	}

	@Test
	void outputFloodIsLimited() throws Exception {
		SushiCore core = core();
		String id = run(core, "flood", "for (var i = 0; i < 5000; i++) print('line ' + i);");
		assertTrue(out(core, id).size() < 300, "got " + out(core, id).size());
		assertTrue(outAt(core, id, ConsoleLog.Level.WARN).stream().anyMatch(m -> m.contains("Output limit")));
	}

	@Test
	void storageWorksThroughTheApiAndPersists() throws Exception {
		SushiCore core = core();
		String id = add(core, "store", "// @permissions storage\n"
				+ "var runs = Storage.get('runs') || 0; Storage.set('runs', runs + 1); Storage.set('name', 'x'); Storage.set('flag', true);\n"
				+ "print('runs', runs, Storage.keys().join(','), Storage.remove('name'), Storage.get('name'));\n"
				+ "try { Storage.set('o', {a: 1}); print('accepted'); } catch (e) { print('rejected object'); }");
		core.scripts().approve(id);
		core.scripts().start(id);
		assertTrue(out(core, id).contains("runs 0 runs,name,flag true null"), out(core, id).toString());
		assertTrue(out(core, id).contains("rejected object"));
		core.scripts().stop(id);
		SushiCore restarted = ScriptTestSupport.core(tmp, game);
		restarted.scripts().start(id);
		assertTrue(out(restarted, id).contains("runs 1 runs,flag,name true null"), out(restarted, id).toString());
	}

	@Test
	void storageIsSeparatePerProfile() throws Exception {
		SushiCore core = core();
		String id = add(core, "prof", "// @permissions storage\nvar n = Storage.get('n') || 0; Storage.set('n', n + 10); print('n=' + n);");
		core.scripts().approve(id);
		core.scripts().start(id);
		core.scripts().stop(id);
		core.profiles().create("Other");
		String otherId = core.profiles().list().stream().filter(p -> p.name.equals("Other")).findFirst().orElseThrow().id;
		core.profiles().switchTo(otherId);
		core.scripts().start(id);
		assertEquals(2, out(core, id).stream().filter("n=0"::equals).count(), "the other profile starts with empty data");
	}

	@Test
	void profilesAutoStartScripts() throws Exception {
		SushiCore core = core();
		String id = add(core, "auto", "print('auto up');");
		core.scripts().approve(id);
		assertTrue(core.scripts().setAutoStart(id, true).ok());
		assertTrue(core.scripts().isAutoStart(id));
		core.scripts().startProfileScripts();
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(id));

		core.profiles().create("Other");
		String otherId = core.profiles().list().stream().filter(p -> p.name.equals("Other")).findFirst().orElseThrow().id;
		core.profiles().switchTo(otherId);
		core.scripts().onProfileSwitched();
		assertEquals(ScriptManager.Status.STOPPED, core.scripts().status(id), "scripts auto-started by the old profile stop");
		assertFalse(core.scripts().isAutoStart(id));

		core.profiles().switchTo("default");
		core.scripts().onProfileSwitched();
		assertEquals(ScriptManager.Status.RUNNING, core.scripts().status(id));

		SushiCore restarted = ScriptTestSupport.core(tmp, game);
		assertTrue(restarted.scripts().isAutoStart(id), "auto-start list is saved with the profile");
	}

	@Test
	void renameAndDeleteKeepEverythingConsistent() throws Exception {
		SushiCore core = core();
		String id = run(core, "old", "// @permissions storage\nStorage.set('k', 1);");
		core.scripts().setAutoStart(id, true);
		var r = core.scripts().rename(id, "new");
		assertTrue(r.ok());
		assertEquals(0, core.scripts().runningCount(), "a renamed script is stopped");
		assertTrue(core.scripts().isAutoStart("new.sushi.js"));
		assertFalse(core.scripts().isAutoStart(id));
		assertEquals(ScriptManager.Status.STOPPED, core.scripts().status("new.sushi.js"));
		core.scripts().start("new.sushi.js");
		assertTrue(core.scripts().delete("new.sushi.js").ok());
		assertEquals(0, core.scripts().runningCount());
		assertFalse(core.scripts().isAutoStart("new.sushi.js"));
		assertEquals(ScriptManager.Status.MISSING, core.scripts().status("new.sushi.js"));
	}

	@Test
	void manyScriptsRunAtOnceUpToTheLimit() throws Exception {
		SushiCore core = core();
		for (int i = 0; i < ScriptManager.MAX_RUNNING; i++) {
			run(core, "s" + i, "Events.on('tick', function () {});");
		}
		assertEquals(ScriptManager.MAX_RUNNING, core.scripts().runningCount());
		String extra = add(core, "extra", "");
		assertFalse(core.scripts().start(extra).ok());
		core.scripts().stopAll();
		assertEquals(0, core.scripts().runningCount());
	}

	@Test
	void timerAndListenerCountsAreCapped() throws Exception {
		SushiCore core = core();
		String id = run(core, "caps",
				"var t = 0; try { for (var i = 0; i < 1000; i++) { setInterval(function () {}, 1000); t++; } } catch (e) { print('timer cap ' + t); }\n"
						+ "var l = 0; try { for (var i = 0; i < 1000; i++) { Events.on('tick', function () {}); l++; } } catch (e) { print('listener cap ' + l); }");
		assertTrue(out(core, id).contains("timer cap " + ScriptInstance.MAX_TIMERS), out(core, id).toString());
		assertTrue(out(core, id).contains("listener cap " + ScriptInstance.MAX_LISTENERS), out(core, id).toString());
	}

	@Test
	void shippedExampleScriptsParseAndRunWithoutErrors() throws Exception {
		Path dir = Path.of("examples");
		assertTrue(java.nio.file.Files.isDirectory(dir), "examples folder not found from " + Path.of("").toAbsolutePath());
		List<Path> files;
		try (var s = java.nio.file.Files.list(dir)) {
			files = s.filter(p -> p.getFileName().toString().endsWith(".sushi.js")).sorted().toList();
		}
		assertTrue(files.size() >= 6);
		SushiCore core = core();
		for (Path f : files) {
			String name = f.getFileName().toString().replace(".sushi.js", "");
			String src = java.nio.file.Files.readString(f);
			assertTrue(ScriptMetadata.parse(src, "").name().length() > 0, name + " needs @name");
			assertNull(core.scripts().checkSyntax(name, src), name + " has a syntax error");
			String id = add(core, name, src);
			core.scripts().approve(id);
			core.scripts().start(id);
			for (int i = 0; i < 80; i++) {
				core.scripts().tick();
			}
			core.scripts().dispatch("chat", Map.of("message", "hi", "kind", "player"));
			core.scripts().dispatch("key", Map.of("key", "F6", "action", "press"));
			core.scripts().dispatch("key", Map.of("key", "F7", "action", "press"));
			core.scripts().dispatch("join", Map.of());
			assertTrue(outAt(core, id, ConsoleLog.Level.ERROR).isEmpty(), name + " logged errors: " + outAt(core, id, ConsoleLog.Level.ERROR));
			assertNull(core.scripts().lastFailure(id), name + " failed");
		}
		core.scripts().stopAll();
	}
}
