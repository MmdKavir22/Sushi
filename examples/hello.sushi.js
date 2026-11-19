// @name Hello
// @version 1.0.0
// @author Sushi
// @description Prints a greeting to the Sushi console and your chat.
// @permissions ui.message

print("Hello from " + Sushi.scriptName + " (API v" + Sushi.apiVersion + ")");
if (Sushi.inGame()) {
  UI.message("Hello from " + Sushi.scriptName);
}
