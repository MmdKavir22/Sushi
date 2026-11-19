// @name World Info
// @version 1.0.0
// @author Sushi
// @description Prints dimension, time, weather and the block below you when you press F7.
// @permissions world.read, player.read, input.read

Events.on("key", function (e) {
  if (e.key !== "F7" || e.action !== "press" || !Sushi.inGame()) return;
  var p = Player.position();
  var below = World.blockAt(Math.floor(p.x), Math.floor(p.y) - 1, Math.floor(p.z));
  print("Dimension: " + World.dimension() + ", game time: " + World.time() +
        ", raining: " + World.isRaining() + ", block below: " + below);
});
