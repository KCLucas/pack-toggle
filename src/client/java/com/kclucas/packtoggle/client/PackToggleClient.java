package com.kclucas.packtoggle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PackToggleClient implements ClientModInitializer {

	public static final String MOD_ID = "packtoggle";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping toggleKey;
	private static KeyMapping saveKey;
	private static PackToggleConfig config = new PackToggleConfig();

	public static PackToggleConfig getConfig() {
		return config;
	}

	@Override
	public void onInitializeClient() {
		config = PackToggleConfig.load();

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.packtoggle.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));
		saveKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.packtoggle.save", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void onClientTick(Minecraft client) {
		boolean toggle = false;
		while (toggleKey.consumeClick()) {
			toggle = true;
		}
		boolean save = false;
		while (saveKey.consumeClick()) {
			save = true;
		}

		// Während die Ressourcen noch neu laden, würde ein weiterer Wechsel nur einen zweiten Ladevorgang anstoßen.
		if (client.getOverlay() != null) {
			return;
		}
		if (save) {
			PackManager.save(client);
		}
		if (toggle) {
			PackManager.toggle(client);
		}
	}
}
