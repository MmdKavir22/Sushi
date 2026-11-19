package ir.mmdkavir.sushi.script;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Metadata read from {@code // @key value} comment lines at the top of a script:
 * <pre>
 * // @name Coordinates
 * // @version 1.0.0
 * // @author you
 * // @description Shows your position
 * // @permissions player.read, ui.message
 * </pre>
 * Parsing stops at the first line that is neither blank nor a {@code //} comment. Metadata is only
 * data: it is never executed.
 */
public record ScriptMetadata(String name, String version, String author, String description,
		Set<ScriptPermission> permissions, List<String> unknownPermissions) {

	public static ScriptMetadata parse(String source, String fallbackName) {
		String name = null;
		String version = "";
		String author = "";
		String description = "";
		Set<ScriptPermission> perms = new LinkedHashSet<>();
		List<String> unknown = new ArrayList<>();
		if (source != null) {
			for (String raw : source.split("\r?\n", -1)) {
				String line = raw.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (!line.startsWith("//")) {
					break;
				}
				String body = line.substring(2).trim();
				if (!body.startsWith("@")) {
					continue;
				}
				int sp = indexOfSpace(body);
				String key = (sp < 0 ? body.substring(1) : body.substring(1, sp)).toLowerCase(Locale.ROOT);
				String value = sp < 0 ? "" : body.substring(sp).trim();
				if (value.length() > 300) {
					value = value.substring(0, 300);
				}
				switch (key) {
					case "name" -> name = value;
					case "version" -> version = value;
					case "author" -> author = value;
					case "description" -> description = value;
					case "permissions", "permission" -> {
						for (String part : value.split("[,\\s]+")) {
							if (part.isBlank()) {
								continue;
							}
							ScriptPermission p = ScriptPermission.byId(part);
							if (p != null) {
								perms.add(p);
							} else if (!unknown.contains(part)) {
								unknown.add(part);
							}
						}
					}
					default -> {
					}
				}
			}
		}
		if (name == null || name.isBlank()) {
			name = fallbackName;
		}
		return new ScriptMetadata(name, version, author, description, perms, unknown);
	}

	private static int indexOfSpace(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) {
				return i;
			}
		}
		return -1;
	}
}
