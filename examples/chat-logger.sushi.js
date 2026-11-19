// @name Chat Logger
// @version 1.0.0
// @author Sushi
// @description Copies chat messages to the Sushi console and counts them across sessions.
// @permissions chat.read, storage

var total = Storage.get("total") || 0;
print("Messages counted so far: " + total);

Events.on("chat", function (e) {
  total++;
  print("[" + e.kind + "] " + e.message);
  if (total % 10 === 0) {
    Storage.set("total", total);
  }
});

Sushi.onStop(function () {
  Storage.set("total", total);
  print("Saved total: " + total);
});
