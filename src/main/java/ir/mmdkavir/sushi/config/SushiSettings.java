package ir.mmdkavir.sushi.config;

/** Persistent global settings. Only settings that are implemented in the current phase live here. */
public final class SushiSettings {
	public static final int CURRENT_FORMAT = 1;

	public int formatVersion = CURRENT_FORMAT;
	public String activeProfile = "default";
	public boolean debugLogging = false;
	public boolean confirmDestructiveActions = true;

	/** Repairs values that are missing or out of range after loading from disk. */
	public void sanitize() {
		formatVersion = CURRENT_FORMAT;
		if (activeProfile == null || activeProfile.isBlank()) {
			activeProfile = "default";
		}
	}
}
