# Script permissions and security

## What permissions are
A script lists what it wants in its header: `// @permissions player.read, chat.send`.
Sushi will not run a script until **you press Approve** in the Script Manager (or the editor) for exactly those permissions.
If you later edit the header to request more, the script needs approval again. Approvals are stored per script id in
`config/sushi/settings/script-permissions.json`; a script gets only permissions that its **current** header asks for **and** you approved.
The **Revoke** button (it replaces **Approve** once everything is approved) removes all approvals of that script and stops it. A corrupt approvals file grants nothing.

| Permission | What it allows | Marked sensitive |
|---|---|---|
| `player.read` | Player name, position, rotation, health, food | no |
| `world.read` | Dimension, time, weather, blocks within 64 blocks | no |
| `input.read` | Read key state and receive key events (can see what you press while playing) | **yes** |
| `chat.read` | Receive chat and game messages (can read all chat) | **yes** |
| `chat.send` | Send chat messages and commands as you | **yes** |
| `ui.message` | Show messages in your own chat / action bar | no |
| `storage` | Keep small key/value data for the script | no |

`tick`, `join`, `leave`, timers, `print`, and `Sushi.*` need no permission.
There are no permissions for files, network, processes or Java because those functions are simply not provided.

## What Sushi does to restrict scripts
- Scripts run in Rhino's *safe* global scope: the Java bridge (`Packages`, `java`, `importClass`, `load`, ...) is not defined.
- A class shutter that hides every Java class is installed, and Sushi never gives scripts Java objects: the API consists
  of plain JavaScript functions returning strings, numbers, booleans and plain objects/arrays.
- API objects are sealed (cannot be overwritten or deleted by the script).
- Each script has its own global scope; scripts cannot see each other's data or prototypes.
- **Limits**: time per top-level run and per callback (stops the script; `try/catch` cannot swallow it), interpreter call depth 200,
  max 100 timers, 100 listeners, 16 running scripts, 100 output lines per second, chat 10 per second, 64 KB storage, 256 KB script size,
  block lookups within 64 blocks.
- File access by Sushi itself is confined to the scripts folder: names are validated, `..`, absolute paths and symlinks are refused, nothing is overwritten silently.

## What this does NOT guarantee
Sushi uses Mozilla Rhino. **Rhino is not a hardened sandbox, and Sushi does not claim that hostile scripts are harmless.**
- **Memory is not limited.** A script can allocate until the game runs low on memory (for example `new Array(1e8).fill(0)` or building huge strings).
- **Time limits are checked between interpreted instructions.** A single long native operation (a catastrophic regular expression,
  `"x".repeat(1e9)`, sorting a gigantic array) cannot be interrupted and can freeze the game until it finishes.
- A bug in Rhino or in Sushi could allow a script to do more than described here. Vulnerabilities in the engine are outside Sushi's control.
- Permissions control which *Sushi functions* work; they cannot control what a bug could expose.
- A script with `chat.send` can send anything you could type, and a server may punish that.
- Sushi does not inspect script behaviour or check that a script does what its description says.

**Practical advice:** only approve scripts you wrote or have read. Treat a script from someone else like a program you are about to run.
Read the header before pressing Approve, and be extra careful with the sensitive permissions.
