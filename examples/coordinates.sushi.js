// @name Coordinates
// @version 1.0.0
// @author Sushi
// @description Shows your block position in the action bar once per second.
// @permissions player.read, ui.message

setInterval(function () {
  if (!Sushi.inGame()) return;
  var p = Player.position();
  UI.actionBar("XYZ: " + Math.floor(p.x) + " " + Math.floor(p.y) + " " + Math.floor(p.z));
}, 1000);
