# Sushi Script API (version 1)

Scripts are plain JavaScript files named `*.sushi.js` in `.minecraft/config/sushi/scripts/` (folders allowed).
They run in **Mozilla Rhino 1.7.15** in interpreted mode with `languageVersion = ES6`. This is the Rhino
subset of ES6 (`let`, `const`, arrow functions, template strings, `Map`/`Set`, `Symbol` ...). `import`/`export`,
`async`/`await`, `require` and Node.js or browser APIs (`fetch`, `window`, `document`) do **not** exist.
Everything below is implemented in `SushiApi.java` and covered by the tests in `ScriptEngineTest.java`.

Conventions
- Numbers are JavaScript numbers. Errors are thrown as a JavaScript `TypeError` whose `message` starts with the function name.
- A function that needs a permission throws when the permission is not granted ([SCRIPT_PERMISSIONS.md](SCRIPT_PERMISSIONS.md)).
- "Needs world" means it throws `not connected to a world` when you are in the main menu.
- Every callback runs on the game thread and must finish quickly (default limit 100 ms, see [SCRIPT_LIFECYCLE.md](SCRIPT_LIFECYCLE.md)).

## Metadata header
Comment lines at the top of the file (before any code):
```js
// @name Coordinates
// @version 1.0.0
// @author you
// @description Shows your position
// @permissions player.read, ui.message
```
`@name` defaults to the file name. `@permissions` lists what the script needs; unknown names are ignored with a warning.

## Globals

| Function | Parameters | Returns | Notes |
|---|---|---|---|
| `print(...values)` | any values | `undefined` | Writes one INFO line to the Sushi console, values joined with spaces. |
| `console.log/info(...)` | any values | `undefined` | Same as `print`. |
| `console.warn(...)` | any values | `undefined` | WARN line. |
| `console.error(...)` | any values | `undefined` | ERROR line. |
| `console.debug(...)` | any values | `undefined` | DEBUG line, only shown when *debug logging* is on in Settings. |
| `setTimeout(fn, ms, ...args)` | `fn` function, `ms` number (default 0), extra args passed to `fn` | timer id (number) | Runs `fn` once after about `ms` milliseconds. |
| `setInterval(fn, ms, ...args)` | same | timer id | Runs `fn` repeatedly. |
| `clearTimeout(id)` / `clearInterval(id)` | timer id | `true` if a timer was removed | Safe to call with unknown ids. |

Output limits: 100 lines per second per script (extra lines are dropped with one warning), 2000 characters per line.

**Timers run on game ticks** (20 per second). `ms` is rounded up to whole ticks (minimum 1 tick = 50 ms), so
`setTimeout(f, 0)` runs on the next tick, never immediately. Timers count client ticks, so if the game lags the timers lag with it. At most 100 timers per script.

## `Sushi`

| Member | Returns | Description |
|---|---|---|
| `Sushi.apiVersion` | number (`1`) | API version. |
| `Sushi.scriptId` | string | File id, e.g. `tools/farm.sushi.js`. |
| `Sushi.scriptName` | string | `@name` from the header. |
| `Sushi.scriptVersion` | string | `@version` (empty if missing). |
| `Sushi.inGame()` | boolean | `true` when connected to a world. No permission needed. |
| `Sushi.profile()` | string | Name of the active Sushi profile. |
| `Sushi.hasPermission(name)` | boolean | `name` is a permission id such as `"chat.send"`. |
| `Sushi.permissions()` | string[] | Permissions granted to this run. |
| `Sushi.onStop(fn)` | `undefined` | Runs `fn` when the script stops or reloads (max 20 handlers). Runs with the callback time limit; errors are logged. |
| `Sushi.stop()` | `undefined` | Ends this script after the current callback returns. |

## `Player` (needs world)

| Function | Permission | Returns |
|---|---|---|
| `Player.name()` | `player.read` | string: your player name. |
| `Player.position()` | `player.read` | `{x, y, z}` numbers (feet position). |
| `Player.rotation()` | `player.read` | `{yaw, pitch}` in degrees. |
| `Player.health()` | `player.read` | number (20 = full; 1 heart = 2). |
| `Player.food()` | `player.read` | number 0-20 hunger level. |
| `Player.chat(message)` | `chat.send` | `undefined`. Sends a public chat message as you. `message`: 1-256 characters, no control characters, must not start with `/` (use `Player.command`). |
| `Player.command(command)` | `chat.send` | `undefined`. Runs a command as you. A leading `/` is optional. Same length rules. |

Chat/command sending is limited to 10 per second per script; the 11th call in that second throws.
Servers may forbid automated chat; you are responsible for the rules of the server you play on.

## `World` (needs world, permission `world.read`)

| Function | Parameters | Returns |
|---|---|---|
| `World.dimension()` | none | string id, e.g. `"minecraft:overworld"`. |
| `World.time()` | none | number: game time in ticks since the world was created. |
| `World.isRaining()` | none | boolean. |
| `World.blockAt(x, y, z)` | numbers (floored to block coordinates) | block id string such as `"minecraft:stone"`, or `null` if that chunk is not loaded. Throws if the position is more than 64 blocks from you on any axis. |

## `Input` (permission `input.read`)

| Function | Parameters | Returns |
|---|---|---|
| `Input.isKeyDown(key)` | key name, case-insensitive (see `Input.keyNames()`) | boolean. Reads the physical key; it also reads while a screen is open. Throws for unknown names. |
| `Input.keyNames()` | none | string[] of all supported names: `A`-`Z`, `0`-`9`, `F1`-`F12`, `KP_0`-`KP_9`, `SPACE`, `COMMA`, `MINUS`, `PERIOD`, `SLASH`, `ESCAPE`, `ENTER`, `TAB`, `BACKSPACE`, `INSERT`, `DELETE`, `LEFT`, `RIGHT`, `UP`, `DOWN`, `PAGE_UP`, `PAGE_DOWN`, `HOME`, `END`, `LSHIFT`, `RSHIFT`, `LCTRL`, `RCTRL`, `LALT`, `RALT`. |

Sushi reads keys; it cannot press keys, move the mouse or control the player.

## `Events`

| Function | Parameters | Returns |
|---|---|---|
| `Events.on(name, fn)` | event name, function | listener id (number). |
| `Events.once(name, fn)` | same | listener id; removed after the first call. |
| `Events.off(id)` | listener id | `true` if a listener was removed. |
| `Events.names()` | none | `["chat","join","key","leave","tick"]`. |

An unknown event name or a non-function throws. At most 100 listeners per script. `fn` receives one object:

| Event | When | Payload | Permission to listen |
|---|---|---|---|
| `tick` | end of every client tick | `{tick}`: ticks since this script started (1, 2, 3 ...) | none |
| `join` | you joined a world/server | `{}` | none |
| `leave` | you disconnected | `{}` | none |
| `chat` | a chat or game message was received (action-bar overlay messages are skipped) | `{message, kind}`; `kind` is `"player"` or `"game"`; `message` is the plain text | `chat.read` |
| `key` | a supported key went down or up (not reported while a screen is open) | `{key, action}`; `key` is a name from `Input.keyNames()`, `action` is `"press"` or `"release"` | `input.read` |

## `Timer`

| Function | Parameters | Returns |
|---|---|---|
| `Timer.setTimeout(fn, ms, ...args)` | as the global | timer id |
| `Timer.setInterval(fn, ms, ...args)` | as the global | timer id |
| `Timer.clear(id)` | timer id | boolean |
| `Timer.now()` | none | number: system clock in milliseconds since 1970. |
| `Timer.ticks()` | none | number: game ticks since this script started. |

## `UI` (needs world, permission `ui.message`)

| Function | Parameters | Returns |
|---|---|---|
| `UI.message(text)` | string (truncated to 256 characters, control characters replaced by spaces) | `undefined`. Shows a line in **your own** chat; nobody else sees it. |
| `UI.actionBar(text)` | same | `undefined`. Shows text above the hotbar, locally. |

Sushi has no overlay/HUD drawing API in this version.

## `Storage` (permission `storage`)
Small key/value store private to this script **and the active profile** (`scripts/.data/<profile>/...json`).
Values are strings, numbers or booleans. Limits: 200 keys, keys 1-64 characters, 64 KB total.

| Function | Parameters | Returns |
|---|---|---|
| `Storage.get(key)` | string | the stored value, or `null`. |
| `Storage.set(key, value)` | string, string/number/boolean | `undefined`. Throws for other types or over a limit. |
| `Storage.remove(key)` | string | `true` if the key existed. |
| `Storage.keys()` | none | string[] |

## What does not exist
No file, network, process, clipboard, screenshot, mouse, movement, inventory or entity functions. No `Packages`,
`java`, `importClass`, `load`, `require`. The API namespaces are sealed: they cannot be replaced or deleted.
