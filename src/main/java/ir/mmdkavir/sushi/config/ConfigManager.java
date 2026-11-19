package ir.mmdkavir.sushi.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves {@link SushiSettings} with automatic recovery from corrupt or newer files. */
public final class ConfigManager {
	private final SushiPaths paths;
	private final ConsoleLog console;
	private SushiSettings settings = new SushiSettings();

	public ConfigManager(SushiPaths paths, ConsoleLog console) {
		this.paths = paths;
		this.console = console;
	}

	public SushiSettings get() {
		return settings;
	}

	public void load() {
		Path file = paths.settingsFile();
		if (!Files.exists(file)) {
			settings = new SushiSettings();
			save();
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(JsonStore.readString(file));
			if (!root.isJsonObject()) {
				throw new JsonParseException("Top-level value is not a JSON object");
			}
			JsonObject obj = root.getAsJsonObject();
			int version = obj.has("formatVersion") && obj.get("formatVersion").isJsonPrimitive()
					? obj.get("formatVersion").getAsInt() : 0;
			if (version > SushiSettings.CURRENT_FORMAT) {
				Path moved = JsonStore.quarantine(file, "newer-version");
				console.warn("settings.json was written by a newer Sushi (format " + version
						+ "). Kept as " + moved.getFileName() + " and created fresh defaults.");
				settings = new SushiSettings();
				save();
				return;
			}
			SushiSettings loaded = JsonStore.gson().fromJson(obj, SushiSettings.class);
			if (loaded == null) {
				throw new JsonParseException("Empty settings");
			}
			loaded.sanitize();
			settings = loaded;
		} catch (IOException | RuntimeException e) {
			try {
				Path moved = JsonStore.quarantine(file, "corrupt");
				console.warn("settings.json was unreadable (" + e.getMessage() + "). Kept as "
						+ moved.getFileName() + " and created fresh defaults.");
			} catch (IOException io) {
				console.error("settings.json is unreadable and could not be moved aside", io);
			}
			settings = new SushiSettings();
			save();
		}
	}

	public boolean save() {
		try {
			JsonStore.writeAtomic(paths.settingsFile(), settings);
			return true;
		} catch (IOException e) {
			console.error("Could not save settings", e);
			return false;
		}
	}
}
