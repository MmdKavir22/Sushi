package ir.mmdkavir.sushi.profile;

import com.google.gson.JsonParseException;
import ir.mmdkavir.sushi.config.ConfigManager;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Creates, renames, deletes and switches profiles. Profiles are stored as {@code profiles/<id>.json}. */
public final class ProfileManager {
	public static final String DEFAULT_ID = "default";
	public static final int MAX_NAME_LENGTH = 32;

	private final SushiPaths paths;
	private final ConfigManager config;
	private final ConsoleLog console;
	private final Map<String, Profile> profiles = new LinkedHashMap<>();

	public ProfileManager(SushiPaths paths, ConfigManager config, ConsoleLog console) {
		this.paths = paths;
		this.config = config;
		this.console = console;
	}

	/** Loads all profiles from disk, skipping unreadable files, and guarantees a valid active profile. */
	public void load() {
		profiles.clear();
		try {
			Files.createDirectories(paths.profilesDir());
			List<Path> files;
			try (Stream<Path> s = Files.list(paths.profilesDir())) {
				files = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
			}
			for (Path file : files) {
				readProfile(file);
			}
		} catch (IOException e) {
			console.error("Could not list profiles", e);
		}
		if (profiles.isEmpty()) {
			createInternal("Default", DEFAULT_ID);
		}
		if (!profiles.containsKey(config.get().activeProfile)) {
			config.get().activeProfile = profiles.keySet().iterator().next();
			config.save();
		}
	}

	private void readProfile(Path file) {
		try {
			Profile p = JsonStore.gson().fromJson(JsonStore.readString(file), Profile.class);
			String expectedId = file.getFileName().toString().replaceFirst("\\.json$", "");
			if (p == null || p.name == null || p.name.isBlank() || p.formatVersion < 1
					|| p.formatVersion > Profile.CURRENT_FORMAT) {
				throw new JsonParseException("Missing fields or unsupported format version");
			}
			p.id = expectedId;
			profiles.put(p.id, p);
		} catch (IOException | RuntimeException e) {
			console.warn("Skipped unreadable profile file " + file.getFileName() + ": " + e.getMessage());
		}
	}

	public List<Profile> list() {
		List<Profile> out = new ArrayList<>(profiles.values());
		out.sort(Comparator.comparing(p -> p.name.toLowerCase()));
		return out;
	}

	public Profile active() {
		return profiles.get(config.get().activeProfile);
	}

	public Profile get(String id) {
		return profiles.get(id);
	}

	public static String validateName(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Name cannot be empty.";
		}
		String name = raw.trim();
		if (name.length() > MAX_NAME_LENGTH) {
			return "Name is longer than " + MAX_NAME_LENGTH + " characters.";
		}
		for (int i = 0; i < name.length(); i++) {
			if (Character.isISOControl(name.charAt(i))) {
				return "Name contains control characters.";
			}
		}
		return null;
	}

	private boolean nameTaken(String name, String exceptId) {
		for (Profile p : profiles.values()) {
			if (!p.id.equals(exceptId) && p.name.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	public ActionResult create(String rawName) {
		String error = validateName(rawName);
		if (error != null) {
			return ActionResult.failure(error);
		}
		String name = rawName.trim();
		if (nameTaken(name, null)) {
			return ActionResult.failure("A profile named \"" + name + "\" already exists.");
		}
		String id = Slug.unique(name, new HashSet<>(profiles.keySet()));
		return createInternal(name, id);
	}

	private ActionResult createInternal(String name, String id) {
		Profile p = new Profile();
		p.id = id;
		p.name = name;
		p.createdAt = Instant.now().toString();
		try {
			JsonStore.writeAtomic(paths.profilesDir().resolve(id + ".json"), p);
			Files.createDirectories(paths.macrosDir(id));
		} catch (IOException e) {
			console.error("Could not create profile " + name, e);
			return ActionResult.failure("Could not save the profile: " + e.getMessage());
		}
		profiles.put(id, p);
		console.info("Created profile \"" + name + "\"");
		return ActionResult.success("Created profile \"" + name + "\".");
	}

	public ActionResult rename(String id, String rawName) {
		Profile p = profiles.get(id);
		if (p == null) {
			return ActionResult.failure("Profile not found.");
		}
		String error = validateName(rawName);
		if (error != null) {
			return ActionResult.failure(error);
		}
		String name = rawName.trim();
		if (nameTaken(name, id)) {
			return ActionResult.failure("A profile named \"" + name + "\" already exists.");
		}
		String old = p.name;
		p.name = name;
		try {
			JsonStore.writeAtomic(paths.profilesDir().resolve(id + ".json"), p);
		} catch (IOException e) {
			p.name = old;
			console.error("Could not rename profile", e);
			return ActionResult.failure("Could not save the profile: " + e.getMessage());
		}
		console.info("Renamed profile \"" + old + "\" to \"" + name + "\"");
		return ActionResult.success("Renamed to \"" + name + "\".");
	}

	public ActionResult switchTo(String id) {
		Profile p = profiles.get(id);
		if (p == null) {
			return ActionResult.failure("Profile not found.");
		}
		config.get().activeProfile = id;
		config.save();
		console.info("Switched to profile \"" + p.name + "\"");
		return ActionResult.success("Active profile is now \"" + p.name + "\".");
	}

	/** Deletes a profile. Its macros are moved to {@code macros/.trash/} instead of being erased. */
	public ActionResult delete(String id) {
		Profile p = profiles.get(id);
		if (p == null) {
			return ActionResult.failure("Profile not found.");
		}
		if (profiles.size() <= 1) {
			return ActionResult.failure("You cannot delete the last remaining profile.");
		}
		try {
			Path macros = paths.macrosDir(id);
			if (Files.isDirectory(macros)) {
				Files.createDirectories(paths.macroTrashDir());
				Files.move(macros, paths.macroTrashDir().resolve(id + "-" + System.currentTimeMillis()));
			}
			Files.deleteIfExists(paths.profilesDir().resolve(id + ".json"));
		} catch (IOException e) {
			console.error("Could not delete profile " + p.name, e);
			return ActionResult.failure("Could not delete the profile: " + e.getMessage());
		}
		profiles.remove(id);
		if (id.equals(config.get().activeProfile)) {
			config.get().activeProfile = list().get(0).id;
			config.save();
		}
		console.info("Deleted profile \"" + p.name + "\" (its macros were moved to macros/.trash)");
		return ActionResult.success("Deleted \"" + p.name + "\". Its macros were moved to macros/.trash.");
	}

	/** Adds or removes a script id in the profile's auto-start list and saves the profile. */
	public ActionResult setScriptAutoStart(String profileId, String scriptId, boolean enabled) {
		Profile p = profiles.get(profileId);
		if (p == null) {
			return ActionResult.failure("Profile not found.");
		}
		if (p.autoStartScripts == null) {
			p.autoStartScripts = new ArrayList<>();
		}
		boolean changed = enabled ? (!p.autoStartScripts.contains(scriptId) && p.autoStartScripts.add(scriptId))
				: p.autoStartScripts.remove(scriptId);
		if (changed) {
			try {
				JsonStore.writeAtomic(paths.profilesDir().resolve(profileId + ".json"), p);
			} catch (IOException e) {
				console.error("Could not save profile " + p.name, e);
				return ActionResult.failure("Could not save the profile: " + e.getMessage());
			}
		}
		return ActionResult.success(enabled ? "Auto-start on for profile \"" + p.name + "\"."
				: "Auto-start off for profile \"" + p.name + "\".");
	}

	/** Replaces a script id in every profile's auto-start list (used when a script is renamed or deleted; newId may be null). */
	public void replaceScriptReferences(String oldId, String newId) {
		for (Profile p : profiles.values()) {
			if (p.autoStartScripts != null && p.autoStartScripts.contains(oldId)) {
				p.autoStartScripts.remove(oldId);
				if (newId != null && !p.autoStartScripts.contains(newId)) {
					p.autoStartScripts.add(newId);
				}
				try {
					JsonStore.writeAtomic(paths.profilesDir().resolve(p.id + ".json"), p);
				} catch (IOException e) {
					console.error("Could not update profile " + p.name, e);
				}
			}
		}
	}

	public Set<String> ids() {
		return profiles.keySet();
	}
}
