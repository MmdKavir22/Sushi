// @name Welcome Message
// @version 1.0.0
// @author Sushi
// @description Sends one chat message 3 seconds after you join a world. Sends chat as you: check the server rules first.
// @permissions chat.send

var MESSAGE = "Hello everyone!";

Events.on("join", function () {
  setTimeout(function () {
    Player.chat(MESSAGE);
  }, 3000);
});
