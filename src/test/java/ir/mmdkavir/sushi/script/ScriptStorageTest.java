package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.SushiCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static ir.mmdkavir.sushi.script.ScriptTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Metadata parsing, script files, path safety, approvals and per-script data. No JavaScript is executed here. */
class ScriptStorageTest {
	@TempDir
	Path tmp;

	@Test
	void metadataIsParsedFromHeaderComments() {
		String src = "\n// @name Coordinates\n// @version 1.2.3\n// @author mmd\n// @description Shows things\n"
				+ "// @permissions player.read, ui.message  bogus.perm\n// plain comment\nvar x = 1;\n// @name Ignored\n";
		ScriptMetadata m = ScriptMetadata.parse(src, "fallback");
		assertEquals("Coordinates", m.name());
		assertEquals("1.2.3", m.version());
		assertEquals("mmd", m.author());
		assertEquals("Shows things", m.description());
		assertEquals(Set.of(ScriptPermission.PLAYER_READ, ScriptPermission.UI_MESSAGE), m.permissions());
		assertEquals(java.util.List.of("bogus.perm"), m.unknownPermissions());
	}

	@Test
	void metadataFallsBackToFileNameAndStopsAtCode() {
		ScriptMetadata m = ScriptMetadata.parse("var a = 1;\n// @name Late\n// @permissions chat.send\n", "my-script");
		assertEquals("my-script", m.name());
		assertTrue(m.permissions().isEmpty(), "header after code must not request permissions");
	}

	@Test
	void createListSaveRenameDelete() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		ScriptStore store = core.scripts().store();
		assertTrue(store.create("alpha", "// @name Alpha\nprint(1);").ok());
		assertTrue(store.create("tools/beta", null).ok(), "folders are created on demand and a template is used");
		assertFalse(store.create("alpha", "x").ok(), "never overwrite on create");

		var list = store.list();
		assertEquals(2, list.size());
		assertEquals("alpha.sushi.js", list.get(0).id());
		assertEquals("Alpha", list.get(0).metadata().name());
		assertEquals("tools", list.get(1).folder());
		assertEquals(2, core.countScriptFiles());

		assertTrue(store.save("alpha.sushi.js", "print(2);").ok());
		assertEquals("print(2);", store.read("alpha.sushi.js"));
		assertEquals("// @name Alpha\nprint(1);",
				Files.readString(tmp.resolve("sushi/scripts/.history/alpha.sushi.js.prev")), "previous version is kept");
		assertFalse(store.save("missing.sushi.js", "x").ok(), "save never creates files");

		assertFalse(store.rename("alpha.sushi.js", "tools/beta").ok(), "never overwrite on rename");
		var renamed = store.rename("alpha.sushi.js", "gamma");
		assertTrue(renamed.ok());
		assertEquals("gamma.sushi.js", renamed.message());
		assertFalse(store.exists("alpha.sushi.js"));

		assertTrue(store.delete("gamma.sushi.js").ok());
		assertFalse(store.exists("gamma.sushi.js"));
		try (var s = Files.list(tmp.resolve("sushi/scripts/.trash"))) {
			assertEquals(1L, s.count(), "deleted scripts are moved to .trash, not erased");
		}
		assertEquals(1, store.list().size(), "hidden folders are not listed");
	}

	@Test
	void pathTraversalAndBadNamesAreRejected() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		ScriptStore store = core.scripts().store();
		for (String bad : new String[]{"../evil", "a/../../evil", "/abs", "a//b", "..", ".hidden", "a/.hidden/b",
				"C:\\temp\\x", "name:with:colon", "", "   ", "a/b/c/d/e", "bad*name", "trail."}) {
			assertFalse(store.create(bad, "x").ok(), "should reject: " + bad);
		}
		assertThrows(IllegalArgumentException.class, () -> store.resolve("../x.sushi.js"));
		assertThrows(IllegalArgumentException.class, () -> store.resolve("a/../../x.sushi.js"));
		assertThrows(IllegalArgumentException.class, () -> store.resolve("notascript.txt"));
		assertFalse(store.exists("../../etc/passwd"));
		assertFalse(Files.exists(tmp.resolve("evil.sushi.js")));
		assertTrue(store.create("ok name-1_2.v3", "x").ok(), "spaces, dash, underscore and dots are fine");
	}

	@Test
	void oversizedScriptsAreRejected() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		String big = "x".repeat(ScriptStore.MAX_SOURCE_BYTES + 1);
		assertFalse(core.scripts().store().create("big", big).ok());
		add(core, "small", "x");
		assertFalse(core.scripts().store().save("small.sushi.js", big).ok());
	}

	@Test
	void approvalsPersistAndFollowRenames() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		String id = add(core, "p", "// @permissions player.read, chat.send\n");
		assertEquals(ScriptManager.Status.NEEDS_APPROVAL, core.scripts().status(id));
		assertEquals(Set.of(ScriptPermission.PLAYER_READ, ScriptPermission.CHAT_SEND), core.scripts().missingPermissions(id));
		assertTrue(core.scripts().approve(id).ok());
		assertEquals(ScriptManager.Status.STOPPED, core.scripts().status(id));

		SushiCore restarted = core(tmp, new TestBridge());
		assertEquals(Set.of(ScriptPermission.PLAYER_READ, ScriptPermission.CHAT_SEND), restarted.scripts().granted(id));

		var r = restarted.scripts().rename(id, "q");
		assertTrue(r.ok());
		assertEquals(Set.of(ScriptPermission.PLAYER_READ, ScriptPermission.CHAT_SEND), restarted.scripts().granted("q.sushi.js"));
		assertTrue(restarted.scripts().granted(id).isEmpty());

		restarted.scripts().revoke("q.sushi.js");
		assertTrue(restarted.scripts().granted("q.sushi.js").isEmpty());
	}

	@Test
	void corruptPermissionFileResetsApprovalsSafely() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		String id = add(core, "p", "// @permissions player.read\n");
		core.scripts().approve(id);
		Files.writeString(tmp.resolve("sushi/settings/script-permissions.json"), "{ broken");
		SushiCore again = core(tmp, new TestBridge());
		assertTrue(again.scripts().granted(id).isEmpty(), "unreadable approvals must never grant anything");
	}

	@Test
	void scriptDataIsPerScriptAndPerProfile() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		ScriptDataStore data = new ScriptDataStore(core.paths());
		data.set("default", "a.sushi.js", "n", 5.0);
		data.set("default", "a.sushi.js", "s", "hello");
		data.set("default", "a.sushi.js", "b", true);
		data.set("other", "a.sushi.js", "n", 99.0);
		data.set("default", "dir/b.sushi.js", "n", 1.0);

		assertEquals(5.0, data.get("default", "a.sushi.js", "n"));
		assertEquals("hello", data.get("default", "a.sushi.js", "s"));
		assertEquals(true, data.get("default", "a.sushi.js", "b"));
		assertEquals(99.0, data.get("other", "a.sushi.js", "n"));
		assertEquals(1.0, data.get("default", "dir/b.sushi.js", "n"));
		assertNull(data.get("default", "a.sushi.js", "missing"));
		assertEquals(java.util.List.of("n", "s", "b"), data.keys("default", "a.sushi.js"));
		assertTrue(data.remove("default", "a.sushi.js", "s"));
		assertNull(data.get("default", "a.sushi.js", "s"));
		assertTrue(data.fileFor("default", "../../x.sushi.js").normalize().startsWith(core.paths().scriptDataDir()),
				"data files cannot escape the data folder");
	}

	@Test
	void scriptDataLimitsAreEnforced() throws Exception {
		SushiCore core = core(tmp, new TestBridge());
		ScriptDataStore data = new ScriptDataStore(core.paths());
		assertThrows(IllegalArgumentException.class, () -> data.set("default", "a.sushi.js", "", "x"));
		assertThrows(IllegalArgumentException.class, () -> data.set("default", "a.sushi.js", "k".repeat(65), "x"));
		assertThrows(IllegalArgumentException.class, () -> data.set("default", "a.sushi.js", "k", new Object()));
		assertThrows(IllegalArgumentException.class, () -> data.set("default", "a.sushi.js", "k", Double.NaN));
		assertThrows(IllegalArgumentException.class,
				() -> data.set("default", "a.sushi.js", "big", "x".repeat(ScriptDataStore.MAX_BYTES + 1)));
	}
}
