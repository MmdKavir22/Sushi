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

	public record Entry(long epochMillis, Level level, String message) {
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
		log(level, message, true);
	}

	private void log(Level level, String message, boolean forward) {
		if (level == Level.DEBUG && !debugEnabled) {
			return;
		}
		Entry entry = new Entry(System.currentTimeMillis(), level, message);
		synchronized (entries) {
			entries.addLast(entry);
			while (entries.size() > MAX_ENTRIES) {
				entries.removeFirst();
			}
		}
		if (forward) {
			switch (level) {
				case DEBUG -> Sushi.LOGGER.debug(message);
				case INFO -> Sushi.LOGGER.info(message);
				case WARN -> Sushi.LOGGER.warn(message);
				case ERROR -> Sushi.LOGGER.error(message);
			}
		}
		appendToFile(entry);
	}

	/** Returns entries in chronological order; {@code filter == null} means all levels. */
	public List<Entry> snapshot(Level filter) {
		List<Entry> out = new ArrayList<>();
		synchronized (entries) {
			for (Entry e : entries) {
				if (filter == null || e.level() == filter) {
					out.add(e);
				}
			}
		}
		return out;
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
			String line = Instant.ofEpochMilli(e.epochMillis()) + " [" + e.level() + "] " + e.message() + System.lineSeparator();
			Files.writeString(logFile, line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ex) {
			// Never let logging failures break the mod; report once to the game log only.
			Sushi.LOGGER.warn("Could not write Sushi log file: {}", ex.getMessage());
		}
	}
}
