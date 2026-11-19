package ir.mmdkavir.sushi.script;

import ir.mmdkavir.sushi.config.SushiPaths;
import ir.mmdkavir.sushi.util.ActionResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Script files under {@code config/sushi/scripts/}. A script id is its path relative to that folder
 * with forward slashes, for example {@code mining/auto.sushi.js}. Every id is validated and resolved
 * so it can never point outside the scripts folder, and nothing is overwritten or deleted silently:
 * creating or renaming onto an existing file fails, saving keeps the previous copy in
 * {@code .history/}, and deleting moves the file to {@code .trash/}.
 */
public final class ScriptStore {
	public static final String EXTENSION = ".sushi.js";
	public static final int MAX_SOURCE_BYTES = 256 * 1024;
	public static final int MAX_DEPTH = 4;
	private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _\\-.]{0,47}");

	public record Entry(String id, String folder, String fileName, long sizeBytes, long modifiedMillis,
			ScriptMetadata metadata) {
	}

	private final SushiPaths paths;

	public ScriptStore(SushiPaths paths) {
		this.paths = paths;
	}

	public static final String TEMPLATE = """
			// @name %s
			// @version 1.0.0
			// @author
			// @description
			// @permissions ui.message
			// Edit this script, then press Run. See docs/SCRIPT_API.md.

			print("Hello from " + Sushi.scriptName);
			UI.message("Hello from " + Sushi.scriptName);
			""";

	// ---- validation -------------------------------------------------------------------------

	/** Returns an error message, or {@code null} if {@code segment} is a valid file or folder name part. */
	public static String validateSegment(String segment) {
		if (segment == null || segment.isBlank()) {
			return "Name cannot be empty.";
		}
		if (!SEGMENT.matcher(segment).matches()) {
			return "Use letters, digits, space, '-', '_' or '.' (max 48 characters, starting with a letter or digit).";
		}
		if (segment.contains("..") || segment.endsWith(".") || segment.endsWith(" ")) {
			return "Name cannot contain '..' or end with '.' or a space.";
		}
		return null;
	}

	/** Splits {@code "folder/name"} (extension optional) and validates it; returns the id or sets the error. */
	public static String normaliseId(String raw) throws IllegalArgumentException {
		if (raw == null) {
			throw new IllegalArgumentException("Name cannot be empty.");
		}
		String s = raw.trim().replace('\\', '/');
		if (s.startsWith("/") || s.endsWith("/") || s.contains("//")) {
			throw new IllegalArgumentException("Use the form folder/name without leading or doubled slashes.");
		}
		if (s.endsWith(EXTENSION)) {
			s = s.substring(0, s.length() - EXTENSION.length());
		}
		String[] parts = s.split("/", -1);
		if (parts.length > MAX_DEPTH) {
			throw new IllegalArgumentException("Folders can be at most " + (MAX_DEPTH - 1) + " levels deep.");
		}
		for (String part : parts) {
			String err = validateSegment(part);
			if (err != null) {
				throw new IllegalArgumentException(err);
			}
		}
		return s + EXTENSION;
	}

	/** Resolves an id to a real path inside the scripts folder; throws if the id is invalid or escapes it. */
	public Path resolve(String id) {
		String normalised = normaliseId(id);
		if (!normalised.equals(id)) {
			throw new IllegalArgumentException("Not a canonical script id: " + id);
		}
		Path root = paths.scriptsDir().toAbsolutePath().normalize();
		Path p = root.resolve(id).normalize();
		if (!p.startsWith(root) || p.equals(root)) {
			throw new IllegalArgumentException("Path escapes the scripts folder.");
		}
		// Reject symlinks anywhere below the root so a link cannot redirect reads or writes elsewhere.
		Path cur = root;
		for (Path part : root.relativize(p)) {
			cur = cur.resolve(part);
			if (Files.isSymbolicLink(cur)) {
				throw new IllegalArgumentException("Symbolic links are not allowed in the scripts folder.");
			}
		}
		return p;
	}

	// ---- listing ----------------------------------------------------------------------------

	public List<Entry> list() {
		List<Entry> out = new ArrayList<>();
		Path root = paths.scriptsDir();
		if (!Files.isDirectory(root)) {
			return out;
		}
		try (Stream<Path> s = Files.walk(root, MAX_DEPTH)) {
			for (Path p : (Iterable<Path>) s::iterator) {
				if (!Files.isRegularFile(p) || Files.isSymbolicLink(p) || !p.getFileName().toString().endsWith(EXTENSION)) {
					continue;
				}
				Path rel = root.relativize(p);
				if (hiddenSegment(rel)) {
					continue;
				}
				String id = rel.toString().replace('\\', '/');
				try {
					normaliseId(id);
				} catch (IllegalArgumentException e) {
					continue; // odd file names are ignored rather than half-supported
				}
				String folder = id.contains("/") ? id.substring(0, id.lastIndexOf('/')) : "";
				String file = id.substring(id.lastIndexOf('/') + 1);
				String fallback = file.substring(0, file.length() - EXTENSION.length());
				ScriptMetadata meta = ScriptMetadata.parse(readHead(p), fallback);
				out.add(new Entry(id, folder, file, Files.size(p), Files.getLastModifiedTime(p).toMillis(), meta));
			}
		} catch (IOException e) {
			// An unreadable folder yields a partial list; callers see what could be read.
		}
		out.sort(Comparator.comparing((Entry e) -> e.id().toLowerCase()));
		return out;
	}

	private static boolean hiddenSegment(Path rel) {
		for (Path part : rel) {
			if (part.toString().startsWith(".")) {
				return true;
			}
		}
		return false;
	}

	private String readHead(Path p) {
		try {
			byte[] all = Files.readAllBytes(p);
			int n = Math.min(all.length, 8192);
			return new String(all, 0, n, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	public boolean exists(String id) {
		try {
			return Files.isRegularFile(resolve(id));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public String read(String id) throws IOException {
		Path p = resolve(id);
		if (!Files.isRegularFile(p)) {
			throw new IOException("Script not found: " + id);
		}
		if (Files.size(p) > MAX_SOURCE_BYTES) {
			throw new IOException("Script is larger than " + (MAX_SOURCE_BYTES / 1024) + " KB.");
		}
		return Files.readString(p, StandardCharsets.UTF_8);
	}

	// ---- changes ----------------------------------------------------------------------------

	/** Creates a new script. Fails if the id is invalid or the file already exists. */
	public ActionResult create(String rawName, String content) {
		String id;
		try {
			id = normaliseId(rawName);
		} catch (IllegalArgumentException e) {
			return ActionResult.failure(e.getMessage());
		}
		try {
			Path p = resolve(id);
			if (Files.exists(p)) {
				return ActionResult.failure("A script named \"" + id + "\" already exists.");
			}
			String body = content != null ? content
					: TEMPLATE.formatted(id.substring(id.lastIndexOf('/') + 1, id.length() - EXTENSION.length()));
			if (body.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
				return ActionResult.failure("Script is larger than " + (MAX_SOURCE_BYTES / 1024) + " KB.");
			}
			writeAtomic(p, body);
			return ActionResult.success(id);
		} catch (IOException | IllegalArgumentException e) {
			return ActionResult.failure("Could not create the script: " + e.getMessage());
		}
	}

	/** Saves over an existing script, keeping the previous version in {@code .history/<id>.prev}. */
	public ActionResult save(String id, String content) {
		try {
			Path p = resolve(id);
			if (!Files.isRegularFile(p)) {
				return ActionResult.failure("Script no longer exists on disk: " + id);
			}
			if (content.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
				return ActionResult.failure("Script is larger than " + (MAX_SOURCE_BYTES / 1024) + " KB.");
			}
			Path prev = paths.scriptsDir().resolve(".history").resolve(id + ".prev");
			Files.createDirectories(prev.getParent());
			Files.copy(p, prev, StandardCopyOption.REPLACE_EXISTING);
			writeAtomic(p, content);
			return ActionResult.success("Saved " + id);
		} catch (IOException | IllegalArgumentException e) {
			return ActionResult.failure("Could not save: " + e.getMessage());
		}
	}

	/** Renames or moves a script. The target must not exist. Returns the new id in the message on success. */
	public ActionResult rename(String id, String rawNewName) {
		String newId;
		try {
			newId = normaliseId(rawNewName);
		} catch (IllegalArgumentException e) {
			return ActionResult.failure(e.getMessage());
		}
		try {
			Path from = resolve(id);
			Path to = resolve(newId);
			if (!Files.isRegularFile(from)) {
				return ActionResult.failure("Script not found: " + id);
			}
			if (newId.equals(id)) {
				return ActionResult.failure("That is already the name.");
			}
			if (Files.exists(to)) {
				return ActionResult.failure("A script named \"" + newId + "\" already exists.");
			}
			Files.createDirectories(to.getParent());
			Files.move(from, to);
			return ActionResult.success(newId);
		} catch (IOException | IllegalArgumentException e) {
			return ActionResult.failure("Could not rename: " + e.getMessage());
		}
	}

	/** Moves the script to {@code .trash/} instead of erasing it. */
	public ActionResult delete(String id) {
		try {
			Path p = resolve(id);
			if (!Files.isRegularFile(p)) {
				return ActionResult.failure("Script not found: " + id);
			}
			Path trash = paths.scriptsTrashDir().resolve(System.currentTimeMillis() + "-" + id.replace('/', '_'));
			Files.createDirectories(trash.getParent());
			Files.move(p, trash);
			return ActionResult.success("Moved " + id + " to scripts/.trash.");
		} catch (IOException | IllegalArgumentException e) {
			return ActionResult.failure("Could not delete: " + e.getMessage());
		}
	}

	private static void writeAtomic(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
