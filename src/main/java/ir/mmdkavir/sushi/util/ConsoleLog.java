package ir.mmdkavir.sushi.util;

import ir.mmdkavir.sushi.Sushi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffer of log entries (shown in the Sushi console) that also forwards to SLF4J
 * and appends to {@code config/sushi/logs/sushi.log}. Thread-safe.
 */
public final class ConsoleLog {
	public enum Level { DEBUG, INFO, WARN, ERROR }

	/** {@code source} is empty for Sushi itself and the script id for messages written by a script. */
	public record Entry(long epochMillis, Level level, String source, String message) {
		public Entry(long epochMillis, Level level, String message) {
			this(epochMillis, level, "", message);
		}
	}

	private static final int MAX_ENTRIES = 500;
	private static final long MAX_FILE_BYTES = 1_000_000L;

	private final Deque<Entry> entries = new ArrayDeque<>();
	private final Path logFile;
	private volatile boolean debugEnabled;

	public ConsoleLog(Path logFile) {
		this.logFile = logFile;
	}

	public void setDebugEnabled(boolean enabled) {
		this.debugEnabled = enabled;
	}

	public void debug(String message) {
		log(Level.DEBUG, message);
	}

	public void info(String message) {
		log(Level.INFO, message);
	}

	public void warn(String message) {
		log(Level.WARN, message);
	}

	public void error(String message) {
		log(Level.ERROR, message);
	}

	public void error(String message, Throwable t) {
		Sushi.LOGGER.error(message, t);
		log(Level.ERROR, message + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")", false);
	}

	public void log(Level level, String message) {
		log(level, "", message, true);
	}

	/** Logs a message that belongs to a script (shown with the script id and filterable by it). */
	public void logFrom(Level level, String source, String message) {
		log(level, source == null ? "" : source, message, true);
	}

	private void log(Level level, String message, boolean forward) {
		log(level, "", message, forward);
	}

	private void log(Level level, String source, String message, boolean forward) {
		if (level == Level.DEBUG && !debugEnabled) {
			return;
		}
		Entry entry = new Entry(System.currentTimeMillis(), level, source, message);
		synchronized (entries) {
			entries.addLast(entry);
			while (entries.size() > MAX_ENTRIES) {
				entries.removeFirst();
			}
		}
		if (forward) {
			String shown = source.isEmpty() ? message : "[" + source + "] " + message;
			switch (level) {
				case DEBUG -> Sushi.LOGGER.debug(shown);
				case INFO -> Sushi.LOGGER.info(shown);
				case WARN -> Sushi.LOGGER.warn(shown);
				case ERROR -> Sushi.LOGGER.error(shown);
			}
		}
		appendToFile(entry);
	}

	/** Returns entries in chronological order; {@code filter == null} means all levels. */
	public List<Entry> snapshot(Level filter) {
		return snapshot(filter, null);
	}

	/** Filters by level ({@code null} = all) and source ({@code null} = all, {@code ""} = Sushi itself). */
	public List<Entry> snapshot(Level filter, String source) {
		List<Entry> out = new ArrayList<>();
		synchronized (entries) {
			for (Entry e : entries) {
				if ((filter == null || e.level() == filter) && (source == null || e.source().equals(source))) {
					out.add(e);
				}
			}
		}
		return out;
	}

	/** Distinct non-empty sources currently in the buffer, sorted. */
	public List<String> sources() {
		java.util.TreeSet<String> set = new java.util.TreeSet<>();
		synchronized (entries) {
			for (Entry e : entries) {
				if (!e.source().isEmpty()) {
					set.add(e.source());
				}
			}
		}
		return new ArrayList<>(set);
	}

	public void clear() {
		synchronized (entries) {
			entries.clear();
		}
	}

	private synchronized void appendToFile(Entry e) {
		if (logFile == null) {
			return;
		}
		try {
			Files.createDirectories(logFile.getParent());
			if (Files.exists(logFile) && Files.size(logFile) > MAX_FILE_BYTES) {
				Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".old"),
						StandardCopyOption.REPLACE_EXISTING);
			}
			String src = e.source().isEmpty() ? "" : "[" + e.source() + "] ";
			String line = Instant.ofEpochMilli(e.epochMillis()) + " [" + e.level() + "] " + src + e.message() + System.lineSeparator();
			Files.writeString(logFile, line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ex) {
			// Never let logging failures break the mod; report once to the game log only.
			Sushi.LOGGER.warn("Could not write Sushi log file: {}", ex.getMessage());
		}
	}
}
