package ir.mmdkavir.sushi.client;

import ir.mmdkavir.sushi.Sushi;
import ir.mmdkavir.sushi.SushiCore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;

/** Client entrypoint: starts the Minecraft-independent core, then registers keybinds and script hooks. */
public final class SushiClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		try {
			SushiCore.init(FabricLoader.getInstance().getConfigDir());
		} catch (IOException | RuntimeException e) {
			Sushi.LOGGER.error("Sushi could not initialise its data folder; the Sushi GUI is disabled.", e);
			return;
		}
		SushiKeys.register();
		ScriptHooks.register(new MinecraftBridge());
		SushiCore.get().scripts().startProfileScripts();
		Sushi.LOGGER.info("Sushi client initialised.");
	}
}
