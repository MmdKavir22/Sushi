package ir.mmdkavir.sushi.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves every location Sushi stores data in: {@code <config>/sushi/...}. */
public final class SushiPaths {
	private final Path root;

	public SushiPaths(Path configDir) {
		this.root = configDir.resolve("sushi");
	}

	public Path root() { return root; }
	public Path macrosDir() { return root.resolve("macros"); }
	public Path macrosDir(String profileId) { return macrosDir().resolve(profileId); }
	public Path macroTrashDir() { return macrosDir().resolve(".trash"); }
	public Path scriptsDir() { return root.resolve("scripts"); }
	public Path scriptsTrashDir() { return scriptsDir().resolve(".trash"); }
	public Path scriptDataDir() { return scriptsDir().resolve(".data"); }
	public Path scriptPermissionsFile() { return settingsDir().resolve("script-permissions.json"); }
	public Path imagesDir() { return root.resolve("images"); }
	public Path overlaysDir() { return root.resolve("overlays"); }
	public Path profilesDir() { return root.resolve("profiles"); }
	public Path settingsDir() { return root.resolve("settings"); }
	public Path settingsFile() { return settingsDir().resolve("settings.json"); }
	public Path logsDir() { return root.resolve("logs"); }
	public Path logFile() { return logsDir().resolve("sushi.log"); }

	/** Creates the full directory layout. Safe to call repeatedly. */
	public void ensureDirectories() throws IOException {
		for (Path p : new Path[]{macrosDir(), scriptsDir(), imagesDir(), overlaysDir(),
				profilesDir(), settingsDir(), logsDir()}) {
			Files.createDirectories(p);
		}
	}
}
