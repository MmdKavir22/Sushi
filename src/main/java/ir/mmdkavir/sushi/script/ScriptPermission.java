package ir.mmdkavir.sushi.script;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Permissions a script can request with {@code // @permissions a, b} and that the user must approve.
 * A permission only controls which Sushi API functions work for that script. There is deliberately
 * no permission for files, network, processes or Java access: those APIs do not exist.
 */
public enum ScriptPermission {
	PLAYER_READ("player.read", "Read your player: name, position, rotation, health, hunger.", false),
	WORLD_READ("world.read", "Read world info: dimension, time, weather and blocks near you.", false),
	INPUT_READ("input.read", "Read keyboard state and receive key events (can observe what you press while playing).", true),
	CHAT_READ("chat.read", "Receive chat and game messages (can read everything shown in chat).", true),
	CHAT_SEND("chat.send", "Send chat messages and commands as you (can act on your behalf on servers).", true),
	UI_MESSAGE("ui.message", "Show messages in your own chat and action bar (local only).", false),
	STORAGE("storage", "Store small key/value data for this script in the Sushi folder.", false);

	public final String id;
	public final String description;
	/** True for permissions that let a script observe input or act on the server; shown with a warning. */
	public final boolean sensitive;

	ScriptPermission(String id, String description, boolean sensitive) {
		this.id = id;
		this.description = description;
		this.sensitive = sensitive;
	}

	public static ScriptPermission byId(String id) {
		if (id == null) {
			return null;
		}
		String s = id.trim().toLowerCase(Locale.ROOT);
		for (ScriptPermission p : values()) {
			if (p.id.equals(s)) {
				return p;
			}
		}
		return null;
	}

	public static List<String> ids(Set<ScriptPermission> set) {
		List<String> out = new ArrayList<>();
		for (ScriptPermission p : values()) {
			if (set.contains(p)) {
				out.add(p.id);
			}
		}
		return out;
	}

	public static Set<ScriptPermission> parseAll(Iterable<String> ids) {
		Set<ScriptPermission> out = new LinkedHashSet<>();
		if (ids != null) {
			for (String id : ids) {
				ScriptPermission p = byId(id);
				if (p != null) {
					out.add(p);
				}
			}
		}
		return out;
	}
}
