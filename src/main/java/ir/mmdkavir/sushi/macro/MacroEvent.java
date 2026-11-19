package ir.mmdkavir.sushi.macro;

/**
 * One recorded or hand-written input step. {@code time} is milliseconds since the start of the
 * macro. Fields other than {@code type} and {@code time} are used depending on the type; see
 * {@link MacroEventType}. Phase 2 (recording/playback) will produce and consume these.
 */
public final class MacroEvent {
	public String type;
	public String key;
	public String button;
	public Double amount;
	public Double x;
	public Double y;
	public long time;

	public MacroEvent() {
	}

	public MacroEvent(String type, long time) {
		this.type = type;
		this.time = time;
	}

	public static MacroEvent key(MacroEventType type, String key, long time) {
		MacroEvent e = new MacroEvent(type.name(), time);
		e.key = key;
		return e;
	}
}
