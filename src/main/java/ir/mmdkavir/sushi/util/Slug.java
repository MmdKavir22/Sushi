package ir.mmdkavir.sushi.util;

import java.util.Locale;
import java.util.Set;

/** Converts display names into safe file-name identifiers. */
public final class Slug {
	private static final int MAX_LENGTH = 40;

	private Slug() {
	}

	public static String of(String input) {
		String s = input == null ? "" : input.toLowerCase(Locale.ROOT);
		s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (s.length() > MAX_LENGTH) {
			s = s.substring(0, MAX_LENGTH).replaceAll("-+$", "");
		}
		return s.isEmpty() ? "item" : s;
	}

	/** Returns a slug that does not collide with any entry of {@code taken}. */
	public static String unique(String input, Set<String> taken) {
		String base = of(input);
		String candidate = base;
		int n = 2;
		while (taken.contains(candidate)) {
			candidate = base + "-" + n++;
		}
		return candidate;
	}
}
