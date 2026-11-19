# Sushi

Sushi is a **client-side** macro automation and scripting mod for Minecraft Java Edition (Fabric).
This repository contains **Phase 1 (foundation)** and **Phase 2 (JavaScript script engine)**.
Macro recording/playback is **not** implemented.

| | |
|---|---|
| Minecraft | **26.2** (exactly; see `fabric.mod.json`) |
| Loader | Fabric Loader 0.19.3+ |
| Fabric API | 0.161.0+26.2 |
| Java | 25 |
| Build | Gradle 9.5.1 + Fabric Loom 1.17-SNAPSHOT (non-remapping, official Mojang names) |
| Script engine | Mozilla Rhino 1.7.15 (bundled in the JAR) |
| Mod ID / package | `sushi` / `ir.mmdkavir.sushi` |

> **Server rules:** macros and automation are prohibited on some multiplayer servers. Sushi only
> works on the client and never sends custom packets, but you are responsible for following the
> rules of every server you play on. Scripts can read chat/keys and send chat **only if you approve those
> permissions**, and cannot move the player or press keys.

## What works in Phase 1

Implemented and unit-tested (the Minecraft-independent core):

- Data folder `config/sushi/{macros,scripts,images,overlays,profiles,settings,logs}`, created on start.
- **Settings** saved to `settings/settings.json` (atomic writes; a corrupt or too-new file is renamed
  to `settings.json.corrupt-<time>` / `.newer-version-<time>` and defaults are used).
- **Profiles**: create, rename, switch, delete (the last profile cannot be deleted; a deleted profile's
  macros are moved to `macros/.trash/`). Stored as `profiles/<id>.json`; the active one persists.
- **Macro files**: versioned JSON (`formatVersion: 1`) in `macros/<profile>/<id>.json`, with validation
  (known event types, ordered non-negative timestamps, required keys/buttons), a migration hook for
  future formats, and safe handling of corrupt files (shown as `[INVALID]`, never loaded).
- **Console log** (ring buffer plus `logs/sushi.log`, rotated at 1 MB).

Implemented in the GUI (needs Minecraft to run; see "Verification status" below):

- Navigation shell with 9 sections. Sections that are not built yet are marked `(P4)`, `(P5)`, `(P6)`
  and show a "NOT AVAILABLE YET" page with **no buttons**.
- Dashboard, Macro Manager (create/rename/delete empty macros, see validation problems),
  Profiles, Settings (two toggles + keybind overview), Console (filters + Clear).
- Keybinds (rebindable in *Options > Controls > Key Binds > Sushi*): **Open Sushi** (default Right Shift),
  **Open Sushi Console** (default Right Ctrl).

Not implemented (nothing pretends to work): macro recording, playback, emergency stop, macro editor,
overlays, GitHub Script Hub, image recognition. The data formats and package layout
(`SushiCore` as composition root, `macro/`, `profile/`, `config/`) are meant to be extended, not rewritten.

## What works in Phase 2: JavaScript scripts

- **Script Manager** (sidebar): lists every `*.sushi.js` in `config/sushi/scripts/` (folders allowed) with name, version, permissions and
  status; **Run, Stop, Reload, Edit, Rename, Delete, Approve/Revoke, Auto-start per profile, Stop all**. Up to 16 scripts run at once.
- **Script Editor**: multi-line editor with line numbers, error-line marker, "UNSAVED" indicator, Save, **Check** (syntax), Run, Stop,
  Rename, Delete. No selection or undo (see "Known limits").
- **Script API** (`Sushi, Player, World, Input, Events, Timer, UI, Storage` + `print`, `console`, `setTimeout`, `setInterval`): see
  [docs/SCRIPT_API.md](docs/SCRIPT_API.md). Events: `tick, chat, join, leave, key`.
- **Console** shows script output with timestamps and script names; filter by level and by script; Clear.
- **Permissions**: per script, approved by you ([docs/SCRIPT_PERMISSIONS.md](docs/SCRIPT_PERMISSIONS.md), including what is *not* guaranteed).
- **Lifecycle**: [docs/SCRIPT_LIFECYCLE.md](docs/SCRIPT_LIFECYCLE.md). **Examples**: [docs/EXAMPLES.md](docs/EXAMPLES.md) and the `examples/` folder.

Quick start: copy `examples/hello.sushi.js` into `.minecraft/config/sushi/scripts/` (or press **New** in the Script Manager), select it,
**Approve**, **Run**, then open the **Console**.

Not implemented: macro recording/playback, overlays, script hub, image recognition.

## Upload the project to GitHub (website only)

1. Create a new repository on github.com (empty; no README needed).
2. **Unzip** `Sushi-phase2.zip` on your computer. GitHub's web uploader does not accept a ZIP.
3. In the repository click **Add file > Upload files**, then drag the **contents** of the unzipped
   folder (not the folder itself) into the page. The files must sit at the repository root, next to
   `build.gradle`.
4. Make sure the hidden `.github` folder came along (macOS: Finder `Cmd+Shift+.` shows hidden
   files; Windows: View > Show > Hidden items). If it did not upload, create the two files by hand:
   **Add file > Create new file**, type `.github/workflows/build.yml` as the name (typing `/` makes
   folders), paste the contents, commit. Repeat for `.github/workflows/release.yml`.
5. Commit to the **main** branch. The default branch must be called `main`.
6. Open the **Actions** tab. If GitHub asks, click "I understand my workflows, go ahead and enable them".

## Automatic builds

Every push to `main` starts **Sushi Build** (also startable manually: *Actions > Sushi Build > Run workflow*).
It installs Java 25 and Gradle, runs `gradle build` (compiles + runs unit tests), checks that the JAR exists and
contains a valid `fabric.mod.json` and the client entrypoint, and uploads it.

**Download a build:** *Actions > the run > Artifacts > `sushi-jar`* (a ZIP containing the `.jar`).
Artifacts are for testing; they expire. Use Releases for real versions.

## Publish a release

1. *Actions > **Sushi Release** > Run workflow* (branch **main**).
2. Enter the **version** (for example `1.0.0`, no `v`), choose **Stable Release** or **Pre-release**,
   optionally type release notes (empty = auto-generated notes), click **Run workflow**.
3. The workflow validates the version, builds, tests and verifies the JAR, and only then creates a
   draft release, attaches `sushi-<version>.jar`, and publishes it. The tag `v<version>` is created by
   GitHub at that moment, so a failed build never leaves a tag behind.
4. Download from the repository's **Releases** page.

Safety rules built in: duplicate versions are rejected, only `main` can release, the built-in
`GITHUB_TOKEN` is used (no secrets), only the publish job has `contents: write`, and an incomplete
draft is deleted if publishing fails.

## Install the mod

Install Fabric Loader for **Minecraft 26.2**, put **Fabric API (0.161.0+26.2 or newer for 26.2)** and
`sushi-<version>.jar` into your `mods` folder, start the game, press **Right Shift**.

## Troubleshooting failed builds

Open the failed run, click the red step, and read the last 30 lines of its log. Also download the
`sushi-build-logs` artifact (test reports). Common causes:

| Symptom | Fix |
|---|---|
| `error: cannot find symbol` / `package net.minecraft... does not exist` | Minecraft 26.x renames things between releases. Copy the error and the file name; the fix is usually a one-word rename in one file. |
| `Gradle version ... is not supported` / Loom asks for a newer Gradle | Edit `GRADLE_VERSION` (top of both workflow files) to the version Loom asks for. |
| `Could not find net.fabricmc.fabric-loom` | Check `loom_version` in `gradle.properties` against https://fabricmc.net/develop (26.2) and `settings.gradle` plugin repositories. |
| `Could not find org.mozilla:rhino` | Maven Central must be reachable; check `repositories { mavenCentral() }` in `build.gradle` and `rhino_version` in `gradle.properties`. |
| `Could not find fabric-api:...` | Check `fabric_api_version` against https://fabricmc.net/develop. |
| Test failure | Open `sushi-build-logs` > `build/reports/tests/test/index.html`. |
| Release says "Tag already exists" | Pick a higher version number. |
| Release job fails with a permissions error | *Settings > Actions > General > Workflow permissions*: allow workflows to run; the release job requests `contents: write` itself. |

## Configure

- Minecraft/Fabric/Rhino versions live in `gradle.properties`; the `depends` block of `src/main/resources/fabric.mod.json` must match the Minecraft version.
- `src/main` = Minecraft-independent logic (settings, profiles, macros, **script engine**). `src/client` = GUI, keybinds and the real
  game connection (`MinecraftBridge`, `ScriptHooks`). Everything scripts can do in the game goes through `GameBridge`, so Minecraft API changes are fixed in one file.
- Script limits: `config/sushi/settings/settings.json` > `scriptStartLimitMs` (2000), `scriptCallbackLimitMs` (100), `scriptMaxErrors` (20).
- There is no Gradle wrapper (it cannot be generated offline). CI installs Gradle itself. To build locally install JDK 25 and Gradle, then run `gradle build`.

## Known limits (Phase 2)

- Rhino is **not a hardened sandbox**: memory use is not limited and one long native operation cannot be interrupted. Only run scripts you trust.
- The editor has no text selection, undo, syntax colouring (only `//` comments are dimmed) or auto-complete; Ctrl+V pastes, Ctrl+C copies the current line.
- Timers are tick-based (50 ms resolution).
- Per-profile data: `Storage` is per profile; scripts and approvals are global.

## Verification status (honest summary)

Tested **here** (JDK 21, no network): the Minecraft-independent storage layer (script files, names/path-traversal rules, metadata,
approvals, per-script data) and all Phase 1 tests ran and passed using a small test runner.

**NOT tested here**, because the sandbox could not download Rhino, Minecraft, Fabric, JUnit or JDK 25:
- Anything that executes JavaScript: `ScriptEngineTest` (about 30 tests covering execution, errors, timeouts, permissions, events, timers, lifecycle,
  isolation, storage, profiles and the shipped examples) has **never been run**. `RhinoSandbox` and `SushiApi` were only type-checked against
  hand-written stubs of the Rhino API, which proves nothing about the real library.
- The Minecraft-facing code (`MinecraftBridge`, `ScriptHooks`, the new screens, the editor's key handling, clipboard calls) has never been compiled.
  It uses Minecraft 26.x names from memory and documentation; compile errors there are possible.
- Nothing has been run inside Minecraft, and the full `gradle build` has not been run.
The first **Sushi Build** run on GitHub is the real check. If it fails, send the log.

## License

No license has been chosen (`All-Rights-Reserved` in `fabric.mod.json`). Add a `LICENSE` file and update
that field when you decide.
