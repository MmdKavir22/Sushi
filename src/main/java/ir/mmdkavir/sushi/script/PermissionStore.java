package ir.mmdkavir.sushi.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import ir.mmdkavir.sushi.config.JsonStore;
import ir.mmdkavir.sushi.config.SushiPaths;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The permissions the user has approved for each script id, saved in {@code settings/script-permissions.json}. */
public final class PermissionStore {
	static final class FileModel {
		int formatVersion = 1;
		Map<String, List<String>> approved = new LinkedHashMap<>();
	}

	private final SushiPaths paths;
	private final ConsoleLog console;
	private FileModel model = new FileModel();

	public PermissionStore(SushiPaths paths, ConsoleLog console) {
		this.paths = paths;
		this.console = console;
		load();
	}

	private Path file() {
		return paths.scriptPermissionsFile();
	}

	private void load() {
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try {
			FileModel m = JsonStore.gson().fromJson(JsonStore.readString(f), FileModel.class);
			if (m == null || m.approved == null || m.formatVersion != 1) {
				throw new JsonParseException("Unsupported or empty permissions file");
			}
			model = m;
		} catch (IOException | RuntimeException e) {
			try {
				Path moved = JsonStore.quarantine(f, "corrupt");
				console.warn("script-permissions.json was unreadable; all script approvals were reset. Kept as "
						+ moved.getFileName());
			} catch (IOException io) {
				console.error("script-permissions.json is unreadable", io);
			}
			model = new FileModel();
		}
	}

	private void save() {
		try {
			JsonStore.writeAtomic(file(), model);
		} catch (IOException e) {
			console.error("Could not save script permissions", e);
		}
	}

	public Set<ScriptPermission> granted(String scriptId) {
		return ScriptPermission.parseAll(model.approved.get(scriptId));
	}

	public void grant(String scriptId, Set<ScriptPermission> permissions) {
		Set<ScriptPermission> all = new LinkedHashSet<>(granted(scriptId));
		all.addAll(permissions);
		model.approved.put(scriptId, new ArrayList<>(ScriptPermission.ids(all)));
		save();
	}

	public void revokeAll(String scriptId) {
		if (model.approved.remove(scriptId) != null) {
			save();
		}
	}

	public void move(String oldId, String newId) {
		List<String> v = model.approved.remove(oldId);
		if (v != null) {
			model.approved.put(newId, v);
			save();
		}
	}
}
