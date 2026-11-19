package ir.mmdkavir.sushi.macro;

import java.util.ArrayList;
import java.util.List;

/** A versioned macro document. This is exactly what is stored in {@code macros/<profile>/<id>.json}. */
public final class Macro {
	public static final int CURRENT_FORMAT = 1;

	public int formatVersion = CURRENT_FORMAT;
	public String name;
	public String description = "";
	public String createdAt;
	public String updatedAt;
	public List<MacroEvent> events = new ArrayList<>();
}
