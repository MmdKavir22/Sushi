package ir.mmdkavir.sushi.client;

import com.mojang.blaze3d.platform.InputConstants;
import ir.mmdkavir.sushi.Sushi;
import ir.mmdkavir.sushi.SushiCore;
import ir.mmdkavir.sushi.client.gui.ConsoleScreen;
import ir.mmdkavir.sushi.client.gui.DashboardScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Keybinds registered with vanilla's key mapping system, so they appear in
 * Options > Controls > Key Binds under "Sushi" and can be rebound there.
 */
public final class SushiKeys {
	public static KeyMapping openGui;
	public static KeyMapping openConsole;

	private SushiKeys() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(Sushi.MOD_ID, "main"));

		openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.sushi.open_gui", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
		openConsole = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.sushi.open_console", InputConstants.Type.KEYSYM, InputConstants.KEY_RCONTROL, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				// Always drain the queues, but only react while no other screen is open.
				while (openGui.consumeClick()) {
					if (client.gui.screen() == null) {
						client.gui.setScreen(new DashboardScreen());
					}
				}
				while (openConsole.consumeClick()) {
					if (client.gui.screen() == null) {
						client.gui.setScreen(new ConsoleScreen());
					}
				}
			} catch (RuntimeException e) {
				SushiCore.get().console().error("Keybind handler failed", e);
			}
		});
	}
}
