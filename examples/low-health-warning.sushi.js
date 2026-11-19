// @name Low Health Warning
// @version 1.0.0
// @author Sushi
// @description Warns in the action bar (at most every 5 seconds) when health is 6 or lower (3 hearts).
// @permissions player.read, ui.message

var THRESHOLD = 6;
var lastWarning = -1000;

Events.on("tick", function (e) {
  if (!Sushi.inGame()) return;
  if (Player.health() <= THRESHOLD && e.tick - lastWarning >= 100) {
    lastWarning = e.tick;
    UI.actionBar("LOW HEALTH: " + Player.health());
  }
});
