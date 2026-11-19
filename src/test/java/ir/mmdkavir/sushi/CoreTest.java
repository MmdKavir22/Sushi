package ir.mmdkavir.sushi;

import ir.mmdkavir.sushi.macro.Macro;
import ir.mmdkavir.sushi.macro.MacroEvent;
import ir.mmdkavir.sushi.macro.MacroEventType;
import ir.mmdkavir.sushi.macro.MacroStore;
import ir.mmdkavir.sushi.profile.Profile;
import ir.mmdkavir.sushi.util.ActionResult;
import ir.mmdkavir.sushi.util.Slug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {
	@TempDir
	Path tmp;

	@Test
	void slugsAreSafeAndUnique() {
		assertEquals("my-macro-1", Slug.of("  My Macro #1!  "));
		assertEquals("item", Slug.of("???"));
		assertEquals("a-2", Slug.unique("A", Set.of("a")));
	}

	@Test
	void freshStartCreatesLayoutAndDefaultProfile() throws Exception {
		SushiCore core = SushiCore.create(tmp);
		assertTrue(Files.isDirectory(tmp.resolve("sushi/macros")));
		assertTrue(Files.isDirectory(tmp.resolve("sushi/scripts")));
		assertTrue(Files.isDirectory(tmp.resolve("sushi/logs")));
		assertEquals("Default", core.profiles().active().name);
		assertTrue(Files.exists(tmp.resolve("sushi/settings/settings.json")));
	}

	@Test
	void profileLifecycle() throws Exception {
		SushiCore core = SushiCore.create(tmp);
		ActionResult created = core.profiles().create("Mining");
		assertTrue(created.ok());
		assertFalse(core.profiles().create("mining").ok(), "names are unique ignoring case");
		assertFalse(core.profiles().create("   ").ok());

		String id = core.profiles().list().stream().filter(p -> p.name.equals("Mining")).findFirst().orElseThrow().id;
		assertTrue(core.profiles().rename(id, "Farming").ok());
		assertTrue(core.profiles().switchTo(id).ok());
		assertEquals("Farming", core.profiles().active().name);

		// Settings survive a restart.
		SushiCore restarted = SushiCore.create(tmp);
		assertEquals(id, restarted.profiles().active().id);

		assertTrue(restarted.profiles().delete(id).ok());
		assertEquals("default", restarted.profiles().active().id);
		assertFalse(restarted.profiles().delete("default").ok(), "last profile cannot be deleted");
	}

	@Test
	void macroSaveLoadRoundTrip() throws Exception {
		SushiCore core = SushiCore.create(tmp);
		String profile = core.profiles().active().id;
		Macro m = new Macro();
		m.name = "Walk";
		m.events.add(MacroEvent.key(MacroEventType.KEY_PRESS, "W", 1000));
		m.events.add(MacroEvent.key(MacroEventType.KEY_RELEASE, "W", 2000));
		assertTrue(core.macros().save(profile, "walk", m).ok());

		List<MacroStore.Entry> list = core.macros().list(profile);
		assertEquals(1, list.size());
		assertTrue(list.get(0).valid());
		assertEquals(2, list.get(0).eventCount());
		assertEquals(1000, list.get(0).macro().events.get(0).time);
	}

	@Test
	void macroValidationRejectsBadData() {
		assertNull(MacroStore.parse("not json at all").macro());
		assertFalse(MacroStore.parse("[]").ok());
		assertFalse(MacroStore.parse("{\"name\":\"x\",\"events\":[]}").ok(), "missing formatVersion");
		assertFalse(MacroStore.parse("{\"formatVersion\":99,\"name\":\"x\",\"events\":[]}").ok(), "newer format");

		String badType = "{\"formatVersion\":1,\"name\":\"x\",\"events\":[{\"type\":\"EXPLODE\",\"time\":1}]}";
		assertFalse(MacroStore.parse(badType).ok());

		String backwards = "{\"formatVersion\":1,\"name\":\"x\",\"events\":["
				+ "{\"type\":\"KEY_PRESS\",\"key\":\"W\",\"time\":500},"
				+ "{\"type\":\"KEY_RELEASE\",\"key\":\"W\",\"time\":100}]}";
		assertFalse(MacroStore.parse(backwards).ok());

		String good = "{\"formatVersion\":1,\"name\":\"x\",\"events\":["
				+ "{\"type\":\"KEY_PRESS\",\"key\":\"W\",\"time\":100},"
				+ "{\"type\":\"MOUSE_PRESS\",\"button\":\"LEFT\",\"time\":150},"
				+ "{\"type\":\"KEY_RELEASE\",\"key\":\"W\",\"time\":500}]}";
		MacroStore.ParseResult r = MacroStore.parse(good);
		assertTrue(r.ok(), () -> String.valueOf(r.problems()));
	}

	@Test
	void corruptFilesAreReportedNotFatal() throws Exception {
		SushiCore core = SushiCore.create(tmp);
		String profile = core.profiles().active().id;
		Files.writeString(tmp.resolve("sushi/macros/" + profile + "/broken.json"), "{ nope");
		Files.writeString(tmp.resolve("sushi/profiles/bad.json"), "garbage");
		core.macros().createEmpty(profile, "Fine");

		List<MacroStore.Entry> list = core.macros().list(profile);
		assertEquals(2, list.size());
		assertEquals(1, list.stream().filter(MacroStore.Entry::valid).count());

		SushiCore again = SushiCore.create(tmp); // must not throw on the bad profile file
		assertNotNull(again.profiles().active());
	}

	@Test
	void corruptSettingsAreQuarantinedAndReset() throws Exception {
		SushiCore.create(tmp);
		Files.writeString(tmp.resolve("sushi/settings/settings.json"), "}{ broken");
		SushiCore core = SushiCore.create(tmp);
		assertEquals("default", core.config().get().activeProfile);
		try (var s = Files.list(tmp.resolve("sushi/settings"))) {
			assertTrue(s.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")));
		}
	}

	@Test
	void profileModelHasVersion() {
		assertEquals(1, new Profile().formatVersion);
	}
}
