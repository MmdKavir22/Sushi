package ir.mmdkavir.sushi.macro;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import ir.mmdkavir.sushi.config.JsonStore;
import ir.mmdkavir.sushi.config.SushiPaths;
import ir.mmdkavir.sushi.util.ActionResult;
import ir.mmdkavir.sushi.util.ConsoleLog;
import ir.mmdkavir.sushi.util.Slug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/** Reads, validates and writes macro files for a profile. Corrupt files are reported, never loaded. */
public final class MacroStore {
	/** A macro file on disk. {@code macro} is null when the file could not be parsed. */
	public record Entry(String id, Macro macro, List<String> problems) {
		public boolean valid() {
			return macro != null && problems.isEmpty();
		}

		public String displayName() {
			return macro != null && macro.name != null && !macro.name.isBlank() ? macro.name : id;
		}

		public int eventCount() {
			return macro != null && macro.events != null ? macro.events.size() : 0;
		}
	}

	public record ParseResult(Macro macro, List<String> problems) {
		public boolean ok() {
			return macro != null && problems.isEmpty();
		}
	}

	/**
	 * Upgrade steps keyed by the version they upgrade FROM. Empty while format 1 is the only
	 * format; when format 2 is introduced, add {@code 1 -> (json) -> upgraded json} here.
	 */
	private static final Map<Integer, UnaryOperator<JsonObject>> MIGRATIONS = new HashMap<>();

	private final SushiPaths paths;
	private final ConsoleLog console;

	public MacroStore(SushiPaths paths, ConsoleLog console) {
		this.paths = paths;
		this.console = console;
	}

	/** Parses and validates macro JSON text without touching the disk. */
	public static ParseResult parse(String json) {
		List<String> problems = new ArrayList<>();
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				problems.add("File is not a JSON object.");
				return new ParseResult(null, problems);
			}
			JsonObject obj = root.getAsJsonObject();
			if (!obj.has("formatVersion") || !obj.get("formatVersion").isJsonPrimitive()
					|| !obj.get("formatVersion").getAsJsonPrimitive().isNumber()) {
				problems.add("formatVersion is missing or not a number.");
				return new ParseResult(null, problems);
			}
			int version = obj.get("formatVersion").getAsInt();
			if (version < 1) {
				problems.add("Invalid formatVersion " + version + ".");
				return new ParseResult(null, problems);
			}
			if (version > Macro.CURRENT_FORMAT) {
				problems.add("Created by a newer Sushi (format " + version + "); this build supports up to "
						+ Macro.CURRENT_FORMAT + ".");
				return new ParseResult(null, problems);
			}
			while (version < Macro.CURRENT_FORMAT) {
				UnaryOperator<JsonObject> step = MIGRATIONS.get(version);
				if (step == null) {
					problems.add("No migration path from format " + version + ".");
					return new ParseResult(null, problems);
				}
				obj = step.apply(obj);
				version++;
				obj.addProperty("formatVersion", version);
			}
			Macro macro = JsonStore.gson().fromJson(obj, Macro.class);
			problems.addAll(MacroValidator.validate(macro));
			return new ParseResult(macro, problems);
		} catch (JsonParseException | IllegalStateException | NumberFormatException | ClassCastException e) {
			problems.add("Invalid JSON: " + e.getMessage());
			return new ParseResult(null, problems);
		}
	}

	public List<Entry> list(String profileId) {
		List<Entry> out = new ArrayList<>();
		Path dir = paths.macrosDir(profileId);
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> s = Files.list(dir)) {
			for (Path file : s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
				String id = file.getFileName().toString().replaceFirst("\\.json$", "");
				out.add(readEntry(id, file));
			}
		} catch (IOException e) {
			console.error("Could not list macros", e);
		}
		out.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
		return out;
	}

	private Entry readEntry(String id, Path file) {
		try {
			ParseResult r = parse(JsonStore.readString(file));
			return new Entry(id, r.macro(), r.problems());
		} catch (IOException e) {
			return new Entry(id, null, List.of("Could not read file: " + e.getMessage()));
		}
	}

	public Entry load(String profileId, String id) {
		return readEntry(id, paths.macrosDir(profileId).resolve(id + ".json"));
	}

	/** Creates a new macro with no events (recording is not implemented yet). */
	public ActionResult createEmpty(String profileId, String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			return ActionResult.failure("Enter a macro name first.");
		}
		if (name.length() > MacroValidator.MAX_NAME_LENGTH) {
			return ActionResult.failure("Name is longer than " + MacroValidator.MAX_NAME_LENGTH + " characters.");
		}
		Set<String> taken = new HashSet<>();
		for (Entry e : list(profileId)) {
			taken.add(e.id());
		}
		String id = Slug.unique(name, taken);
		Macro macro = new Macro();
		macro.name = name;
		macro.createdAt = Instant.now().toString();
		ActionResult r = save(profileId, id, macro);
		return r.ok() ? ActionResult.success("Created macro \"" + name + "\".") : r;
	}

	public ActionResult save(String profileId, String id, Macro macro) {
		List<String> problems = MacroValidator.validate(macro);
		if (!problems.isEmpty()) {
			return ActionResult.failure("Macro is invalid: " + problems.get(0));
		}
		macro.updatedAt = Instant.now().toString();
		if (macro.createdAt == null) {
			macro.createdAt = macro.updatedAt;
		}
		try {
			JsonStore.writeAtomic(paths.macrosDir(profileId).resolve(id + ".json"), macro);
		} catch (IOException e) {
			console.error("Could not save macro " + macro.name, e);
			return ActionResult.failure("Could not save the macro: " + e.getMessage());
		}
		console.info("Saved macro \"" + macro.name + "\" (" + macro.events.size() + " events)");
		return ActionResult.success("Saved \"" + macro.name + "\".");
	}

	public ActionResult rename(String profileId, String id, String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			return ActionResult.failure("Enter the new name first.");
		}
		Entry entry = load(profileId, id);
		if (!entry.valid()) {
			return ActionResult.failure("This macro file is invalid and cannot be renamed.");
		}
		entry.macro().name = name;
		ActionResult r = save(profileId, id, entry.macro());
		return r.ok() ? ActionResult.success("Renamed to \"" + name + "\".") : r;
	}

	/** Moves the macro file to {@code macros/.trash/} so an accidental delete can be recovered by hand. */
	public ActionResult delete(String profileId, String id) {
		Path file = paths.macrosDir(profileId).resolve(id + ".json");
		try {
			if (!Files.exists(file)) {
				return ActionResult.failure("Macro file no longer exists.");
			}
			Files.createDirectories(paths.macroTrashDir());
			Files.move(file, paths.macroTrashDir().resolve(profileId + "-" + id + "-" + System.currentTimeMillis() + ".json"));
		} catch (IOException e) {
			console.error("Could not delete macro " + id, e);
			return ActionResult.failure("Could not delete the macro: " + e.getMessage());
		}
		console.info("Deleted macro file " + id + ".json (moved to macros/.trash)");
		return ActionResult.success("Deleted macro. A copy was kept in macros/.trash.");
	}
}
