// @name Timer Demo
// @version 1.0.0
// @author Sushi
// @description Shows setTimeout, setInterval and clearInterval. Timers run on game ticks (50 ms each).

print("start");
setTimeout(function () { print("after 1 second"); }, 1000);

var count = 0;
var id = setInterval(function () {
  count++;
  print("interval " + count);
  if (count === 3) {
    clearInterval(id);
    print("interval cleared");
    Sushi.stop(); // this script is finished
  }
}, 500);

Sushi.onStop(function () { print("stopped"); });
