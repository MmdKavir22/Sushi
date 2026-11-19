package ir.mmdkavir.sushi.config;

/** Persistent global settings. Only settings that are implemented in the current phase live here. */
public final class SushiSettings {
	public static final int CURRENT_FORMAT = 1;

	public int formatVersion = CURRENT_FORMAT;
	public String activeProfile = "default";
	public boolean debugLogging = false;
	public boolean confirmDestructiveActions = true;
	/** Maximum time a script's top-level code may run before it is stopped (milliseconds). */
	public int scriptStartLimitMs = 2000;
	/** Maximum time one script callback (event, timer) may run before the script is stopped (milliseconds). */
	public int scriptCallbackLimitMs = 100;
	/** A script that fails this many callbacks is stopped automatically. */
	public int scriptMaxErrors = 20;

	/** Repairs values that are missing or out of range after loading from disk. */
	public void sanitize() {
		formatVersion = CURRENT_FORMAT;
		if (activeProfile == null || activeProfile.isBlank()) {
			activeProfile = "default";
		}
		scriptStartLimitMs = Math.max(100, Math.min(30_000, scriptStartLimitMs == 0 ? 2000 : scriptStartLimitMs));
		scriptCallbackLimitMs = Math.max(10, Math.min(5_000, scriptCallbackLimitMs == 0 ? 100 : scriptCallbackLimitMs));
		scriptMaxErrors = Math.max(1, Math.min(1000, scriptMaxErrors == 0 ? 20 : scriptMaxErrors));
	}
}
