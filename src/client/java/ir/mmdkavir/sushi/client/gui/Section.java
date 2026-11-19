package ir.mmdkavir.sushi.client.gui;

/** Main navigation sections. {@code phase == 0} means usable in this build; otherwise the phase that delivers it. */
public enum Section {
	DASHBOARD("Dashboard", 0),
	MACROS("Macro Manager", 0),
	SCRIPTS("Script Manager", 4),
	HUB("Script Hub", 5),
	IMAGES("Image Recognition", 6),
	OVERLAYS("Overlay Manager", 4),
	SETTINGS("Settings", 0),
	PROFILES("Profiles", 0),
	CONSOLE("Console", 0);

	final String label;
	final int phase;

	Section(String label, int phase) {
		this.label = label;
		this.phase = phase;
	}

	String buttonLabel() {
		return phase == 0 ? label : label + " (P" + phase + ")";
	}

	SushiScreen create() {
		return switch (this) {
			case DASHBOARD -> new DashboardScreen();
			case MACROS -> new MacroManagerScreen();
			case SETTINGS -> new SettingsScreen();
			case PROFILES -> new ProfilesScreen();
			case CONSOLE -> new ConsoleScreen();
			case SCRIPTS -> new PlannedScreen(this, "Planned for Phase 4: script engine",
					"Run your own .sushi.js scripts in a sandbox with permissions and limits.",
					"Script files placed in the scripts folder are already counted on the Dashboard,",
					"but nothing can be run, edited or installed from here yet.");
			case HUB -> new PlannedScreen(this, "Planned for Phase 5: GitHub Script Hub",
					"Browse a GitHub repository's manifest, then download, update and remove scripts.",
					"Downloaded scripts will never run without your explicit approval.",
					"No network access is implemented in this build.");
			case IMAGES -> new PlannedScreen(this, "Planned for Phase 6: image recognition",
					"Import reference images and match them against real client screenshots",
					"to trigger macros. Nothing is recognised or scanned in this build.");
			case OVERLAYS -> new PlannedScreen(this, "Planned for Phase 4: overlays",
					"Script-driven, movable and persistent on-screen panels, counters and timers.",
					"No overlays are drawn by this build.");
		};
	}
}
