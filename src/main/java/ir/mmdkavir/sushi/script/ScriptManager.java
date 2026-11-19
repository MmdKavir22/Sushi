package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.config.ConfigManager;
import ir.mmdkavir.sushi.config.SushiPaths;
import ir.mmdkavir.sushi.config.SushiSettings;
import ir.mmdkavir.sushi.profile.Profile;
import ir.mmdkavir.sushi.profile.ProfileManager;
import ir.mmdkavir.sushi.script.engine.RhinoSandbox;
import ir.mmdkavir.sushi.script.engine.ScriptFailure;
import ir.mmdkavir.sushi.util.ActionResult;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starts, stops, reloads and tracks scripts, and delivers game events and ticks to them.
 * Call {@link #tick()} once per game tick and {@link #dispatch} for game events, from the game thread.
 */
public final class ScriptManager {
	public static final int MAX_RUNNING = 16;

	public enum Status { STOPPED, RUNNING, ERROR, NEEDS_APPROVAL, MISSING }

	private final SushiPaths paths;
	private final ConsoleLog console;
	private final ConfigManager config;
	private final ProfileManager profiles;
	private final ScriptStore store;
	private final PermissionStore permissions;
	private final ScriptDataStore data;
	private GameBridge bridge = GameBridge.NONE;

	private final Map<String, ScriptInstance> running = new LinkedHashMap<>();
	private final Map<String, ScriptFailure> failures = new LinkedHashMap<>();
	/** Scripts that were started because the active profile lists them (stopped again when the profile changes). */
	private final Set<String> autoStarted = new HashSet<>();

	public ScriptManager(SushiPaths paths, ConsoleLog console, ConfigManager config, ProfileManager profiles) {
		this.paths = paths;
		this.console = console;
		this.config = config;
		this.profiles = profiles;
		this.store = new ScriptStore(paths);
		this.permissions = new PermissionStore(paths, console);
		this.data = new ScriptDataStore(paths);
	}

	// ---- accessors ---------------------------------------------------------------------------------

	public ScriptStore store() { return store; }
	GameBridge bridge() { return bridge; }
	ScriptDataStore dataStore() { return data; }
	ConsoleLog console() { return console; }
	SushiSettings settings() { return config.get(); }

	public void setBridge(GameBridge bridge) {
		this.bridge = bridge == null ? GameBridge.NONE : bridge;
	}

	String activeProfileName() {
		Profile p = profiles.active();
		return p == null ? "" : p.name;
	}

	private String activeProfileId() {
		Profile p = profiles.active();
		return p == null ? ProfileManager.DEFAULT_ID : p.id;
	}

	public int runningCount() { return running.size(); }

	public ScriptInstance instance(String id) { return running.get(id); }

	public List<String> runningIds() { return new ArrayList<>(running.keySet()); }

	public ScriptFailure lastFailure(String id) { return failures.get(id); }

	public Set<ScriptPermission> granted(String id) { return permissions.granted(id); }

	// ---- status --------------------------------------------------------------------------------------

	public Status status(String id) {
		if (running.containsKey(id)) {
			return Status.RUNNING;
		}
		if (!store.exists(id)) {
			return Status.MISSING;
		}
		if (failures.containsKey(id)) {
			return Status.ERROR;
		}
		return missingPermissions(id).isEmpty() ? Status.STOPPED : Status.NEEDS_APPROVAL;
	}

	/** Permissions the script's header asks for that the user has not approved yet. */
	public Set<ScriptPermission> missingPermissions(String id) {
		try {
			ScriptMetadata meta = metadataOf(id);
			Set<ScriptPermission> missing = new LinkedHashSet<>(meta.permissions());
			missing.removeAll(permissions.granted(id));
			return missing;
		} catch (IOException e) {
			return Set.of();
		}
	}

	public ScriptMetadata metadataOf(String id) throws IOException {
		String file = id.substring(id.lastIndexOf('/') + 1);
		return ScriptMetadata.parse(store.read(id), file.substring(0, file.length() - ScriptStore.EXTENSION.length()));
	}

	// ---- permissions ---------------------------------------------------------------------------------

	/** The user approves everything the script's header currently requests. */
	public ActionResult approve(String id) {
		try {
			ScriptMetadata meta = metadataOf(id);
			if (!meta.unknownPermissions().isEmpty()) {
				console.logFrom(ConsoleLog.Level.WARN, id, "Unknown permission(s) ignored: " + String.join(", ", meta.unknownPermissions()));
			}
			permissions.grant(id, meta.permissions());
			console.logFrom(ConsoleLog.Level.INFO, id, "Permissions approved: "
					+ (meta.permissions().isEmpty() ? "(none requested)" : String.join(", ", ScriptPermission.ids(meta.permissions()))));
			return ActionResult.success("Approved: " + (meta.permissions().isEmpty() ? "no permissions requested" : String.join(", ", ScriptPermission.ids(meta.permissions()))));
		} catch (IOException e) {
			return ActionResult.failure("Could not read the script: " + e.getMessage());
		}
	}

	/** Removes every approval for the script and stops it. */
	public ActionResult revoke(String id) {
		stop(id);
		permissions.revokeAll(id);
		console.logFrom(ConsoleLog.Level.INFO, id, "All permissions revoked");
		return ActionResult.success("Permissions revoked for " + id);
	}

	// ---- lifecycle -------------------------------------------------------------------------------------

	public ActionResult start(String id) {
		if (running.containsKey(id)) {
			return ActionResult.failure(id + " is already running.");
		}
		if (running.size() >= MAX_RUNNING) {
			return ActionResult.failure("At most " + MAX_RUNNING + " scripts can run at once.");
		}
		String source;
		ScriptMetadata meta;
		try {
			source = store.read(id);
			meta = metadataOf(id);
		} catch (IOException | IllegalArgumentException e) {
			return ActionResult.failure("Cannot read " + id + ": " + e.getMessage());
		}
		Set<ScriptPermission> granted = permissions.granted(id);
		Set<ScriptPermission> missing = new LinkedHashSet<>(meta.permissions());
		missing.removeAll(granted);
		if (!missing.isEmpty()) {
			return ActionResult.failure("Needs approval for: " + String.join(", ", ScriptPermission.ids(missing)) + ". Open Script Manager and press Approve.");
		}
		Set<ScriptPermission> effective = new LinkedHashSet<>(meta.permissions()); // only what the header asks for
		effective.retainAll(granted);

		failures.remove(id);
		ScriptInstance inst = new ScriptInstance(this, id, meta, effective, activeProfileId());
		try {
			inst.attach(new RhinoSandbox(SushiApi.installer(inst)));
		} catch (RuntimeException e) {
			console.error("Could not create the script engine for " + id, e);
			return ActionResult.failure("Could not create the script engine: " + e.getMessage());
		}
		running.put(id, inst);
		console.logFrom(ConsoleLog.Level.INFO, id, "Started" + (effective.isEmpty() ? "" : " with permissions: " + String.join(", ", ScriptPermission.ids(effective))));
		try {
			inst.runMain(source);
		} catch (ScriptFailure f) {
			console.logFrom(ConsoleLog.Level.ERROR, id, f.describe());
			failAndStop(id, f);
			return ActionResult.failure(id + " failed: " + f.describe());
		}
		if (inst.stopRequested()) {
			stop(id);
			return ActionResult.success(id + " finished.");
		}
		return ActionResult.success("Started " + id + ".");
	}

	/** Stops the script (running its onStop handlers) and clears its error state. */
	public ActionResult stop(String id) {
		failures.remove(id);
		autoStarted.remove(id);
		ScriptInstance inst = running.remove(id);
		if (inst == null) {
			return ActionResult.failure(id + " is not running.");
		}
		inst.shutdown();
		console.logFrom(ConsoleLog.Level.INFO, id, "Stopped");
		return ActionResult.success("Stopped " + id + ".");
	}

	/** Stops the script after a failure and remembers the failure so the GUI can show it. */
	void failAndStop(String id, ScriptFailure f) {
		ScriptInstance inst = running.remove(id);
		autoStarted.remove(id);
		if (inst != null) {
			inst.shutdown();
		}
		failures.put(id, f);
		console.logFrom(ConsoleLog.Level.ERROR, id, "Stopped because of an error: " + f.describe());
	}

	public ActionResult reload(String id) {
		boolean wasAuto = autoStarted.contains(id);
		boolean was = running.containsKey(id);
		if (was) {
			stop(id);
		}
		ActionResult r = start(id);
		if (r.ok() && wasAuto) {
			autoStarted.add(id);
		}
		if (r.ok()) {
			console.logFrom(ConsoleLog.Level.INFO, id, "Reloaded");
		}
		return r;
	}

	public void stopAll() {
		for (String id : new ArrayList<>(running.keySet())) {
			stop(id);
		}
	}

	// ---- file operations that also keep state consistent --------------------------------------

	public ActionResult create(String name) {
		return store.create(name, null);
	}

	public ActionResult save(String id, String content) {
		return store.save(id, content);
	}

	public ActionResult rename(String id, String newName) {
		boolean wasRunning = running.containsKey(id);
		ActionResult r = store.rename(id, newName);
		if (!r.ok()) {
			return r;
		}
		String newId = r.message();
		if (wasRunning) {
			stop(id);
		}
		failures.remove(id);
		permissions.move(id, newId);
		data.move(activeProfileId(), id, newId);
		profiles.replaceScriptReferences(id, newId);
		console.info("Renamed script " + id + " to " + newId + (wasRunning ? " (it was stopped; start it again)" : ""));
		return ActionResult.success(newId);
	}

	public ActionResult delete(String id) {
		if (running.containsKey(id)) {
			stop(id);
		}
		ActionResult r = store.delete(id);
		if (r.ok()) {
			failures.remove(id);
			permissions.revokeAll(id);
			profiles.replaceScriptReferences(id, null);
			console.info("Deleted script " + id + " (kept in scripts/.trash)");
		}
		return r;
	}

	/** Parses the source to find syntax errors without running anything. Returns null when it is valid. */
	public ScriptFailure checkSyntax(String id, String source) {
		return RhinoSandbox.checkSyntax(source, id);
	}

	// ---- game events and ticks ---------------------------------------------------------------------

	/** Advances all scripts by one game tick: first the {@code tick} event, then due timers. */
	public void tick() {
		for (ScriptInstance inst : new ArrayList<>(running.values())) {
			if (!inst.isRunning()) {
				continue;
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("tick", (double) (inst.ticks() + 1));
			inst.dispatch("tick", payload);
			if (inst.isRunning()) {
				inst.tick();
			}
			if (inst.isRunning() && inst.stopRequested()) {
				stop(inst.id());
			}
		}
	}

	/** Delivers an event ({@code chat}, {@code join}, {@code leave}, {@code key}) to every running script. */
	public void dispatch(String event, Map<String, Object> payload) {
		for (ScriptInstance inst : new ArrayList<>(running.values())) {
			if (!inst.isRunning()) {
				continue;
			}
			if (event.equals("chat") && !inst.has(ScriptPermission.CHAT_READ)) {
				continue;
			}
			if (event.equals("key") && !inst.has(ScriptPermission.INPUT_READ)) {
				continue;
			}
			inst.dispatch(event, new LinkedHashMap<>(payload));
			if (inst.isRunning() && inst.stopRequested()) {
				stop(inst.id());
			}
		}
	}

	// ---- profile integration -------------------------------------------------------------------------

	public boolean isAutoStart(String id) {
		Profile p = profiles.active();
		return p != null && p.autoStartScripts != null && p.autoStartScripts.contains(id);
	}

	public ActionResult setAutoStart(String id, boolean on) {
		Profile p = profiles.active();
		if (p == null) {
			return ActionResult.failure("No active profile.");
		}
		return profiles.setScriptAutoStart(p.id, id, on);
	}

	/** Starts the active profile's auto-start scripts that are not running and have all permissions approved. */
	public void startProfileScripts() {
		Profile p = profiles.active();
		if (p == null || p.autoStartScripts == null) {
			return;
		}
		for (String id : new ArrayList<>(p.autoStartScripts)) {
			if (running.containsKey(id)) {
				continue;
			}
			ActionResult r = start(id);
			if (r.ok()) {
				autoStarted.add(id);
			} else {
				console.logFrom(ConsoleLog.Level.WARN, id, "Auto-start skipped: " + r.message());
			}
		}
	}

	/** Call after the active profile changed: stops scripts the previous profile auto-started, then starts the new profile's. */
	public void onProfileSwitched() {
		for (String id : new ArrayList<>(autoStarted)) {
			stop(id);
		}
		startProfileScripts();
	}
}
