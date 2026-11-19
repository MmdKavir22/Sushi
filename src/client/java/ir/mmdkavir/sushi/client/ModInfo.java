package ir.mmdkavir.sushi.client;

import ir.mmdkavir.sushi.Sushi;
import net.fabricmc.loader.api.FabricLoader;

/** Version strings read from Fabric Loader's mod metadata. */
public final class ModInfo {
	private ModInfo() {
	}

	public static String sushiVersion() {
		return versionOf(Sushi.MOD_ID);
	}

	public static String minecraftVersion() {
		return versionOf("minecraft");
	}

	private static String versionOf(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
