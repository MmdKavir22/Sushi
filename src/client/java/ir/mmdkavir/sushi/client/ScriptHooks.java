package ir.mmdkavir.sushi.client;

import ir.mmdkavir.sushi.SushiCore;
import ir.mmdkavir.sushi.script.KeyNames;
import ir.mmdkavir.sushi.script.ScriptManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Connects Fabric/Minecraft events to the script engine: tick, chat, join, leave and key. */
public final class ScriptHooks {
	private static final Map<Integer, Boolean> KEY_STATE = new HashMap<>();

	private ScriptHooks() {
	}

	public static void register(MinecraftBridge bridge) {
		ScriptManager scripts = SushiCore.get().scripts();
		scripts.setBridge(bridge);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				pollKeys(bridge, scripts, client.gui.screen() == null);
				scripts.tick();
			} catch (RuntimeException e) {
				SushiCore.get().console().error("Script tick failed", e);
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> fire(scripts, "join", Map.of()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> fire(scripts, "leave", Map.of()));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				fire(scripts, "chat", chat(message.getString(), "game"));
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
				fire(scripts, "chat", chat(message.getString(), "player")));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> scripts.stopAll());
	}

	private static Map<String, Object> chat(String text, String kind) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("message", text);
		m.put("kind", kind);
		return m;
	}

	private static void fire(ScriptManager scripts, String event, Map<String, Object> payload) {
		try {
			scripts.dispatch(event, payload);
		} catch (RuntimeException e) {
			SushiCore.get().console().error("Script event \"" + event + "\" failed", e);
		}
	}

	/** Compares key state with the previous tick and fires {@code key} events. Keys are ignored while a screen is open. */
	private static void pollKeys(MinecraftBridge bridge, ScriptManager scripts, boolean noScreen) {
		if (!bridge.inGame()) {
			return;
		}
		for (Map.Entry<String, Integer> e : KeyNames.all().entrySet()) {
			int code = e.getValue();
			boolean down = noScreen && bridge.isKeyDown(code);
			boolean was = KEY_STATE.getOrDefault(code, false);
			if (down != was) {
				KEY_STATE.put(code, down);
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("key", e.getKey());
				m.put("action", down ? "press" : "release");
				fire(scripts, "key", m);
			}
		}
	}
}
