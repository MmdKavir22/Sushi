package ir.mmdkavir.sushi.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import ir.mmdkavir.sushi.config.JsonStore;
import ir.mmdkavir.sushi.config.SushiPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small key/value storage owned by one script and one profile, at
 * {@code scripts/.data/<profile>/<script id with '/' replaced>.json}. Values are strings, numbers or
 * booleans. Limits: 200 keys, 64-character keys, 64 KB in total.
 */
public final class ScriptDataStore {
	public static final int MAX_KEYS = 200;
	public static final int MAX_KEY_LENGTH = 64;
	public static final int MAX_BYTES = 64 * 1024;

	private final SushiPaths paths;

	public ScriptDataStore(SushiPaths paths) {
		this.paths = paths;
	}

	public Path fileFor(String profileId, String scriptId) {
		String safeProfile = profileId.replaceAll("[^A-Za-z0-9_\\-]", "_");
		String safeScript = scriptId.replace('/', '#').replaceAll("[^A-Za-z0-9_\\-.# ]", "_");
		return paths.scriptDataDir().resolve(safeProfile).resolve(safeScript + ".json");
	}

	private JsonObject load(Path f) {
		try {
			if (Files.exists(f)) {
				JsonElement e = JsonParser.parseString(JsonStore.readString(f));
				if (e.isJsonObject()) {
					return e.getAsJsonObject();
				}
			}
		} catch (IOException | RuntimeException ex) {
			// A corrupt data file is treated as empty; the next successful write replaces it.
		}
		return new JsonObject();
	}

	public Object get(String profileId, String scriptId, String key) {
		JsonElement e = load(fileFor(profileId, scriptId)).get(key);
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return p.getAsBoolean();
		}
		if (p.isNumber()) {
			return p.getAsDouble();
		}
		return p.getAsString();
	}

	/** @param value String, Double or Boolean. Throws IllegalArgumentException for anything else or over a limit. */
	public void set(String profileId, String scriptId, String key, Object value) throws IOException {
		if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
			throw new IllegalArgumentException("Storage keys must be 1-" + MAX_KEY_LENGTH + " characters.");
		}
		Path f = fileFor(profileId, scriptId);
		JsonObject obj = load(f);
		if (value instanceof String s) {
			obj.addProperty(key, s);
		} else if (value instanceof Number n) {
			double d = n.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new IllegalArgumentException("Storage numbers must be finite.");
			}
			obj.addProperty(key, d);
		} else if (value instanceof Boolean b) {
			obj.addProperty(key, b);
		} else {
			throw new IllegalArgumentException("Storage only accepts strings, numbers and booleans.");
		}
		if (obj.size() > MAX_KEYS) {
			throw new IllegalArgumentException("Storage is limited to " + MAX_KEYS + " keys.");
		}
		if (JsonStore.gson().toJson(obj).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw new IllegalArgumentException("Storage is limited to " + (MAX_BYTES / 1024) + " KB.");
		}
		JsonStore.writeAtomic(f, obj);
	}

	public boolean remove(String profileId, String scriptId, String key) throws IOException {
		Path f = fileFor(profileId, scriptId);
		JsonObject obj = load(f);
		boolean had = obj.remove(key) != null;
		if (had) {
			JsonStore.writeAtomic(f, obj);
		}
		return had;
	}

	public List<String> keys(String profileId, String scriptId) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : load(fileFor(profileId, scriptId)).entrySet()) {
			out.add(e.getKey());
		}
		return out;
	}

	/** Moves data when a script is renamed. */
	public void move(String profileId, String oldId, String newId) {
		try {
			Path from = fileFor(profileId, oldId);
			if (Files.exists(from)) {
				Path to = fileFor(profileId, newId);
				Files.createDirectories(to.getParent());
				Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			// Data stays under the old name; nothing is lost.
		}
	}
}
