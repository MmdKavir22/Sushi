# Examples

Ready-to-use scripts are in the `examples/` folder of the repository (they are also run by the automated tests, so they are known to
parse and run without errors against the real engine). Copy a file into `.minecraft/config/sushi/scripts/`
(or create a script in the Script Manager and paste the code), open **Script Manager**, select it, press **Approve**, then **Run**.

| File | Permissions | What it does |
|---|---|---|
| `hello.sushi.js` | `ui.message` | Prints a greeting to the console and your chat. Good first test. |
| `coordinates.sushi.js` | `player.read`, `ui.message` | Shows your block position in the action bar every second. |
| `low-health-warning.sushi.js` | `player.read`, `ui.message` | Action-bar warning at 3 hearts or less, at most every 5 seconds. |
| `chat-logger.sushi.js` | `chat.read`, `storage` | Copies chat to the console and keeps a message counter between sessions. |
| `key-watcher.sushi.js` | `input.read`, `ui.message` | Reports F6 presses and whether W is held. |
| `world-info.sushi.js` | `world.read`, `player.read`, `input.read` | F7 prints dimension, time, weather and the block under you. |
| `timer-demo.sushi.js` | none | `setTimeout`, `setInterval`, `clearInterval`, `Sushi.stop`, `Sushi.onStop`. |
| `welcome.sushi.js` | `chat.send` | Sends one chat message 3 seconds after joining. Check server rules first. |

## Patterns

Run something every second only while in a world:
```js
setInterval(function () {
  if (!Sushi.inGame()) return;
  // ... use Player / World here
}, 1000);
```

React to chat:
```js
// @permissions chat.read, ui.message
Events.on("chat", function (e) {
  if (e.message.indexOf("Steve") !== -1) UI.actionBar("Someone mentioned Steve");
});
```

Remember a value between sessions:
```js
// @permissions storage
var runs = (Storage.get("runs") || 0) + 1;
Storage.set("runs", runs);
print("This script has started " + runs + " times in this profile");
```

Clean up on stop (timers and listeners are removed automatically; use this only for your own state):
```js
Sushi.onStop(function () { print("bye"); });
```

Debug a script: use `print(...)`, open **Console** and filter by the script name. Use **Check** in the editor for syntax errors;
runtime errors show the line number in red in the editor and in the manager.
