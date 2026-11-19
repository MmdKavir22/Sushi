package ir.mmdkavir.sushi;

import ir.mmdkavir.sushi.config.ConfigManager;
import ir.mmdkavir.sushi.config.SushiPaths;
import ir.mmdkavir.sushi.macro.MacroStore;
import ir.mmdkavir.sushi.profile.ProfileManager;
import ir.mmdkavir.sushi.script.ScriptManager;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Composition root for everything that does not depend on Minecraft. Later phases add their
 * services here (macro playback, ...) so the GUI has one place to look.
 */
public final class SushiCore {
	private static SushiCore instance;

	private final SushiPaths paths;
	private final ConsoleLog console;
	private final ConfigManager config;
	private final ProfileManager profiles;
	private final MacroStore macros;
	private final ScriptManager scripts;

	private SushiCore(Path configDir) throws IOException {
		this.paths = new SushiPaths(configDir);
		this.paths.ensureDirectories();
		this.console = new ConsoleLog(paths.logFile());
		this.config = new ConfigManager(paths, console);
		this.config.load();
		this.console.setDebugEnabled(config.get().debugLogging);
		this.profiles = new ProfileManager(paths, config, console);
		this.profiles.load();
		this.macros = new MacroStore(paths, console);
		this.scripts = new ScriptManager(paths, console, config, profiles);
	}

	/** Creates the core for the given config directory (tests use a temp directory). */
	public static SushiCore create(Path configDir) throws IOException {
		return new SushiCore(configDir);
	}

	public static synchronized void init(Path configDir) throws IOException {
		instance = new SushiCore(configDir);
		instance.console.info("Sushi core initialised. Data folder: " + instance.paths.root());
	}

	public static SushiCore get() {
		if (instance == null) {
			throw new IllegalStateException("Sushi core is not initialised");
		}
		return instance;
	}

	public SushiPaths paths() { return paths; }
	public ConsoleLog console() { return console; }
	public ConfigManager config() { return config; }
	public ProfileManager profiles() { return profiles; }
	public MacroStore macros() { return macros; }
	public ScriptManager scripts() { return scripts; }

	/** Counts {@code *.sushi.js} files in the scripts folder (including sub-folders). */
	public int countScriptFiles() {
		return scripts.store().list().size();
	}
}
