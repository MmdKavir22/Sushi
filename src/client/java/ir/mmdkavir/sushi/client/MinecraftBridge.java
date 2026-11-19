package ir.mmdkavir.sushi.client;

import com.mojang.blaze3d.platform.InputConstants;
import ir.mmdkavir.sushi.script.GameBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Real Minecraft implementation of {@link GameBridge}. This is the only place where script
 * functions touch Minecraft classes, so API changes in a Minecraft update are fixed here.
 * All calls happen on the client (game) thread.
 */
public final class MinecraftBridge implements GameBridge {
	private final Minecraft mc = Minecraft.getInstance();

	private LocalPlayer player() {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			throw new IllegalStateException("Not connected to a world");
		}
		return p;
	}

	@Override
	public boolean inGame() {
		return mc.player != null && mc.level != null;
	}

	@Override
	public String playerName() {
		return player().getName().getString();
	}

	@Override
	public double[] playerPosition() {
		LocalPlayer p = player();
		return new double[]{p.getX(), p.getY(), p.getZ()};
	}

	@Override
	public double[] playerRotation() {
		LocalPlayer p = player();
		return new double[]{p.getYRot(), p.getXRot()};
	}

	@Override
	public double playerHealth() {
		return player().getHealth();
	}

	@Override
	public int playerFood() {
		return player().getFoodData().getFoodLevel();
	}

	@Override
	public String dimension() {
		player();
		// ResourceKey prints as "ResourceKey[minecraft:dimension / minecraft:overworld]"; keep only the id.
		String s = mc.level.dimension().toString();
		int slash = s.lastIndexOf(" / ");
		int end = s.lastIndexOf(']');
		return slash >= 0 && end > slash ? s.substring(slash + 3, end) : s;
	}

	@Override
	public long gameTime() {
		player();
		return mc.level.getGameTime();
	}

	@Override
	public boolean raining() {
		player();
		return mc.level.isRaining();
	}

	@Override
	public String blockAt(int x, int y, int z) {
		player();
		BlockPos pos = new BlockPos(x, y, z);
		if (!mc.level.hasChunkAt(pos)) {
			return null;
		}
		BlockState state = mc.level.getBlockState(pos);
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	@Override
	public boolean isKeyDown(int glfwKeyCode) {
		return InputConstants.isKeyDown(mc.getWindow(), glfwKeyCode);
	}

	@Override
	public void sendChat(String message) {
		player().connection.sendChat(message);
	}

	@Override
	public void sendCommand(String command) {
		player().connection.sendCommand(command);
	}

	@Override
	public void showMessage(String text, boolean actionBar) {
		LocalPlayer p = player();
		Component c = Component.literal(text);
		if (actionBar) {
			mc.gui.setOverlayMessage(c, false);
		} else {
			p.sendSystemMessage(c);
		}
	}
}
