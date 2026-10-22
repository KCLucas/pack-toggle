package com.kclucas.packtoggle.client;

import com.kclucas.packtoggle.client.onboarding.Onboarding;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

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

		Onboarding.register(createTutorial());
	}

	/** Die kurze Tour beim ersten Start in einem Profil, dazu was in welcher Version neu ist (neueste zuerst). */
	private static Onboarding.Tutorial createTutorial() {
		Component toggleKeyName = Component.keybind("key.packtoggle.toggle").withStyle(ChatFormatting.YELLOW);
		return new Onboarding.Tutorial(MOD_ID,
				Component.literal("Pack Toggle"),
				Component.translatable("packtoggle.tutorial.description"),
				Identifier.fromNamespaceAndPath(MOD_ID, "icon.png"),
				List.of(
						new Onboarding.Page(
								Component.translatable("packtoggle.tutorial.save.title"),
								Component.translatable("packtoggle.tutorial.save.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/save.png")),
						new Onboarding.Page(
								Component.translatable("packtoggle.tutorial.switch.title"),
								Component.translatable("packtoggle.tutorial.switch.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/switch.png")),
						new Onboarding.Page(
								Component.translatable("packtoggle.tutorial.hotkey.title", toggleKeyName),
								Component.translatable("packtoggle.tutorial.hotkey.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/hotkey.png"))),
				List.of(
						new Onboarding.Release("1.2.1", List.of(
								Component.translatable("packtoggle.news.1_2_1.hotkey"),
								Component.translatable("packtoggle.news.1_2_1.hints"))),
						new Onboarding.Release("1.2.0", List.of(
								Component.translatable("packtoggle.news.1_2_0.folder"),
								Component.translatable("packtoggle.news.1_2_0.news"))),
						new Onboarding.Release("1.1.1", List.of(
								Component.translatable("packtoggle.news.1_1_1.menu")))));
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
			if (save || toggle) {
				PackManager.toast(client, Component.translatable("packtoggle.toast.still_loading"));
			}
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
