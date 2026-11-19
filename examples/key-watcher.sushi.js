// @name Key Watcher
// @version 1.0.0
// @author Sushi
// @description Logs presses of F6 and shows whether W is held. Only watches keys while no screen is open.
// @permissions input.read, ui.message

Events.on("key", function (e) {
  if (e.key === "F6" && e.action === "press") {
    UI.message("F6 pressed, W is " + (Input.isKeyDown("W") ? "held" : "not held"));
  }
});
