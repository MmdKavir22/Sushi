package ir.mmdkavir.sushi.script;

/**
 * Everything scripts can read from or do to the game. The client module implements this with real
 * Minecraft calls; tests implement it with plain data. All methods are called on the game thread.
 * Methods may throw {@link IllegalStateException} with a readable message when the game is not in a world.
 */
public interface GameBridge {
	boolean inGame();

	String playerName();

	/** {x, y, z}. */
	double[] playerPosition();

	/** {yaw, pitch} in degrees. */
	double[] playerRotation();

	double playerHealth();

	int playerFood();

	String dimension();

	long gameTime();

	boolean raining();

	/** Block id such as {@code minecraft:stone}, or {@code null} when that position is not loaded. */
	String blockAt(int x, int y, int z);

	boolean isKeyDown(int glfwKeyCode);

	void sendChat(String message);

	/** {@code command} has no leading slash. */
	void sendCommand(String command);

	/** Shows text only to the local player. */
	void showMessage(String text, boolean actionBar);

	/** Used when no game is attached (startup, unit tests). */
	GameBridge NONE = new GameBridge() {
		private IllegalStateException no() {
			return new IllegalStateException("Not connected to a world");
		}

		@Override public boolean inGame() { return false; }
		@Override public String playerName() { throw no(); }
		@Override public double[] playerPosition() { throw no(); }
		@Override public double[] playerRotation() { throw no(); }
		@Override public double playerHealth() { throw no(); }
		@Override public int playerFood() { throw no(); }
		@Override public String dimension() { throw no(); }
		@Override public long gameTime() { throw no(); }
		@Override public boolean raining() { throw no(); }
		@Override public String blockAt(int x, int y, int z) { throw no(); }
		@Override public boolean isKeyDown(int glfwKeyCode) { return false; }
		@Override public void sendChat(String message) { throw no(); }
		@Override public void sendCommand(String command) { throw no(); }
		@Override public void showMessage(String text, boolean actionBar) { throw no(); }
	};
}
