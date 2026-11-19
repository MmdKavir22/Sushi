package ir.mmdkavir.sushi.profile;

/**
 * A named profile. Each profile owns its own macro folder ({@code macros/<id>/}). Later phases
 * attach scripts, image references, overlay layouts and keybind overrides to the same id.
 */
public final class Profile {
	public static final int CURRENT_FORMAT = 1;

	public int formatVersion = CURRENT_FORMAT;
	public String id;
	public String name;
	public String createdAt;
	/** Script ids started automatically when this profile becomes active (script engine). May be null in old files. */
	public java.util.List<String> autoStartScripts = new java.util.ArrayList<>();
}
