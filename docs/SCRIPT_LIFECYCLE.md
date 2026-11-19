# Script lifecycle

## Files
Scripts live in `.minecraft/config/sushi/scripts/`, with up to three folder levels, named `*.sushi.js`.
The script id is its path inside that folder, for example `farming/wheat.sushi.js`.
Names may contain letters, digits, space, `-`, `_` and `.` (max 48 characters per part). Hidden folders
(`.trash`, `.history`, `.data`) are managed by Sushi and never listed as scripts.

- **Create** never overwrites: an existing name is refused. New scripts start from a short template.
- **Save** keeps the previous version once in `scripts/.history/<id>.prev`.
- **Rename** refuses an existing target, stops the script if it was running, and moves its approvals,
  stored data and profile auto-start entries to the new name.
- **Delete** moves the file to `scripts/.trash/<time>-<name>`; nothing is erased. Approvals and auto-start entries are removed.
- Maximum script size 256 KB. Symbolic links inside the scripts folder are refused.

## States
`stopped` - not running. `RUNNING`. `ERROR` - the last start or run failed (message shown in the manager and editor).
`needs approval` - the header asks for permissions you have not approved. `missing` - file gone.

## Start
1. Read the file and its header. If it asks for permissions that are not approved, nothing runs.
2. Create a fresh, empty JavaScript world (global scope) for this script only. Scripts share nothing: not variables,
   not prototypes, not timers.
3. Install the Sushi API and run the file's top-level code with a **start limit** (default 2000 ms).
4. Errors in step 3 (syntax error, exception, timeout) stop the script and are shown with a line number.
   At most 16 scripts run at once.

## While running
Each game tick, for every running script: the `tick` event, then timers that are due. Game events
(`chat`, `join`, `leave`, `key`) are delivered when they happen. Every callback has a **callback limit**
(default 100 ms). Rules:
- An exception inside a callback is logged with its line and counted. After `scriptMaxErrors` (default 20) errors the script is stopped.
- A callback that exceeds its time limit stops the script immediately.
- One script failing never affects others.
Limits can be changed in `config/sushi/settings/settings.json`: `scriptStartLimitMs`, `scriptCallbackLimitMs`, `scriptMaxErrors`.

## Stop and reload
Stop (button, `Sushi.stop()`, an error, leaving the game, renaming, deleting, or a profile switch for auto-started scripts):
1. `Sushi.onStop` handlers run (with the callback limit).
2. All event listeners and timers of that script are discarded, and its JavaScript world is released.
Nothing survives: a script that was stopped receives no more events or timer calls.

**Reload** = Stop, then read the file again and Start. Because the old world is discarded, you never get duplicate listeners or timers.
Editing a file does not change a running script until you reload it.

## Profiles
Each profile has an *auto-start* list (button **Auto** in the Script Manager). When Sushi starts, and whenever you switch
profile in **Profiles**, scripts auto-started by the previous profile stop and the new profile's auto-start scripts start
(only those whose permissions are all approved; others are skipped with a console warning). `Storage` data is separate per profile.
Scripts you started by hand keep running across a profile switch.

## Console
Everything a script prints, plus start/stop/reload/error lines, goes to the Sushi **Console** with a timestamp and the script id.
Filter by level and by script; Clear empties the view buffer (500 lines). `logs/sushi.log` keeps the full text.
