package ir.mmdkavir.sushi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Shared JSON helpers: pretty printing, atomic writes and quarantine of unreadable files. */
public final class JsonStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonStore() {
	}

	public static Gson gson() {
		return GSON;
	}

	/** Writes JSON to a temp file first, then moves it over the target so a crash cannot truncate it. */
	public static void writeAtomic(Path file, Object value) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static String readString(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	/**
	 * Renames a file that could not be used to {@code <name>.<reason>-<timestamp>} so the user's
	 * data is preserved and a fresh file can be written in its place.
	 */
	public static Path quarantine(Path file, String reason) throws IOException {
		Path target = file.resolveSibling(file.getFileName() + "." + reason + "-" + System.currentTimeMillis());
		return Files.move(file, target);
	}
}
