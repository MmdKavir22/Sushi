package ir.mmdkavir.sushi;

import ir.mmdkavir.sushi.config.ConfigManager;
import ir.mmdkavir.sushi.config.SushiPaths;
import ir.mmdkavir.sushi.macro.MacroStore;
import ir.mmdkavir.sushi.profile.ProfileManager;
import ir.mmdkavir.sushi.util.ConsoleLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Composition root for everything that does not depend on Minecraft. Later phases add their
 * services here (recorder, playback engine, script engine, ...) so the GUI has one place to look.
 */
public final class SushiCore {
	private static SushiCore instance;

	private final SushiPaths paths;
	private final ConsoleLog console;
	private final ConfigManager config;
	private final ProfileManager profiles;
	private final MacroStore macros;

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

	/** Counts {@code *.sushi.js} files in the scripts folder. Scripts cannot run until Phase 4. */
	public int countScriptFiles() {
		try (Stream<Path> s = Files.list(paths.scriptsDir())) {
			return (int) s.filter(p -> p.getFileName().toString().endsWith(".sushi.js")).count();
		} catch (IOException e) {
			return 0;
		}
	}
}
