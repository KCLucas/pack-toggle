package com.kclucas.packtoggle.test;

import com.kclucas.packtoggle.client.PackManager;
import com.kclucas.packtoggle.client.PackToggleClient;
import com.kclucas.packtoggle.client.onboarding.OnboardingScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prüft Speichern, Entfernen und Wiederherstellen – über die Knöpfe im Ressourcenpaket-Menü und über den Hotkey.
 * Dafür legt der Test drei kleine Beispiel-Pakete im Ressourcenpaket-Ordner an, so wie echte Pakete dort liegen.
 */
public class PackToggleClientTest implements FabricClientGameTest {

	/** Name, Beschreibung und Farbe des Paket-Bilds – in der Reihenfolge von unten nach oben. */
	private static final String[][] PACKS = {
			{"Faithful PvP 32x", "Sharper swords and short fire", "4C8DFF"},
			{"Clear Glass Overlay", "Borderless glass and ice", "B36BFF"},
			{"Low Shield + Totem", "Smaller shield and totem pop", "FF9F43"},
	};
	private static final List<String> ORDER = Arrays.stream(PACKS).map(pack -> "file/" + pack[0]).toList();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(2560, 1440);
		context.runOnClient(c -> {
			c.options.guiScale().set(3);
			c.resizeDisplay();
		});

		// Erster Start im Profil: Der Willkommens-Screen erscheint von selbst. Hier mit nur einer Mod.
		context.waitForScreen(OnboardingScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("onboarding-1-willkommen");
		for (int step = 0; step < 3; step++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
			context.waitTicks(5);
			context.takeScreenshot("onboarding-" + (step + 2) + "-schritt");
		}
		// Überspringen (Esc) schließt den Screen sofort.
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		if (context.computeOnClient(c -> c.screen instanceof OnboardingScreen)) {
			throw new AssertionError("Esc sollte den Tutorial-Screen schließen");
		}

		// Ausgangslage: drei Pakete aktiv, in einer festen Reihenfolge.
		context.runOnClient(c -> {
			createSamplePacks(c);
			PackRepository repository = c.getResourcePackRepository();
			repository.reload();
			List<String> selection = new ArrayList<>(repository.getSelectedIds());
			selection.addAll(ORDER);
			repository.setSelected(selection);
			c.options.updateResourcePacks(repository);
		});
		waitForReload(context);
		expectActive(context, ORDER, "Ausgangslage");

		// --- Knöpfe im Ressourcenpaket-Menü ---
		openPackScreen(context);
		context.takeScreenshot("packtoggle-1-menue");
		context.clickScreenButton("packtoggle.button.save");
		expectSaved(context, ORDER);

		context.clickScreenButton("packtoggle.button.remove");
		context.waitTicks(5);
		context.takeScreenshot("packtoggle-2-entfernt");
		context.clickScreenButton("gui.done");
		waitForReload(context);
		expectActive(context, List.of(), "nach Entfernen im Menü");

		openPackScreen(context);
		context.clickScreenButton("packtoggle.button.restore");
		context.waitTicks(5);
		context.takeScreenshot("packtoggle-3-hinzugefuegt");
		context.clickScreenButton("gui.done");
		waitForReload(context);
		expectActive(context, ORDER, "nach Hinzufügen im Menü");

		// --- Hotkey im Spiel ---
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			context.waitTicks(20);

			context.getInput().pressKey(GLFW.GLFW_KEY_F8);
			context.waitTicks(5);
			waitForReload(context);
			expectActive(context, List.of(), "nach erstem F8");
			context.takeScreenshot("packtoggle-4-hotkey-aus");

			context.getInput().pressKey(GLFW.GLFW_KEY_F8);
			context.waitTicks(5);
			waitForReload(context);
			expectActive(context, ORDER, "nach zweitem F8");
			context.takeScreenshot("packtoggle-5-hotkey-an");

			// Nach dem Speichern die Auswahl ändern (ein Paket raus, Reihenfolge anders): F8 merkt sich den neuen
			// Stand, statt beim nächsten Druck den alten zurückzubringen.
			List<String> changed = List.of(ORDER.get(2), ORDER.get(0));
			context.runOnClient(c -> {
				PackRepository repository = c.getResourcePackRepository();
				List<String> selection = new ArrayList<>(repository.getSelectedIds());
				selection.removeAll(ORDER);
				selection.addAll(changed);
				repository.setSelected(selection);
				c.options.updateResourcePacks(repository);
			});
			waitForReload(context);
			expectActive(context, changed, "nach dem Ändern");
			context.getInput().pressKey(GLFW.GLFW_KEY_F8);
			context.waitTicks(5);
			waitForReload(context);
			expectActive(context, List.of(), "nach F8 mit geänderter Auswahl");
			expectSaved(context, changed);
			context.getInput().pressKey(GLFW.GLFW_KEY_F8);
			context.waitTicks(5);
			waitForReload(context);
			expectActive(context, changed, "nach erneutem F8");
		}

		// Aufräumen, damit der nächste Lauf wieder bei null anfängt.
		context.runOnClient(c -> {
			PackManager.store(List.of());
			PackManager.removeAll(c);
		});
		waitForReload(context);
	}

	private static void createSamplePacks(Minecraft client) {
		try {
			writeSamplePacks(client);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void writeSamplePacks(Minecraft client) throws IOException {
		int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
		for (String[] pack : PACKS) {
			Path dir = client.getResourcePackDirectory().resolve(pack[0]);
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"" + pack[1]
					+ "\",\"min_format\":" + format + ",\"max_format\":" + format + "}}");

			int color = 0xFF000000 | Integer.parseInt(pack[2], 16);
			BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					boolean border = x == 0 || y == 0 || x == 15 || y == 15;
					boolean mark = x >= 5 && x <= 10 && y >= 5 && y <= 10;
					icon.setRGB(x, y, border ? 0xFF10141D : mark ? 0xFFFFFFFF : color);
				}
			}
			ImageIO.write(icon, "png", dir.resolve("pack.png").toFile());
		}
	}

	private static void openPackScreen(ClientGameTestContext context) {
		context.setScreen(() -> {
			Minecraft c = Minecraft.getInstance();
			return new PackSelectionScreen(c.getResourcePackRepository(), c.options::updateResourcePacks,
					c.getResourcePackDirectory(), Component.translatable("resourcePack.title"));
		});
		context.waitTicks(10);
	}

	private static void waitForReload(ClientGameTestContext context) {
		context.waitTicks(5);
		context.waitFor(c -> c.getOverlay() == null, 1200);
		context.waitTicks(10);
	}

	private static void expectActive(ClientGameTestContext context, List<String> expected, String step) {
		List<String> active = context.computeOnClient(PackManager::activeUserPacks);
		if (!active.equals(expected)) {
			throw new AssertionError(step + ": erwartet " + expected + ", aktiv sind " + active);
		}
	}

	private static void expectSaved(ClientGameTestContext context, List<String> expected) {
		List<String> saved = context.computeOnClient(c -> List.copyOf(PackToggleClient.getConfig().savedPacks));
		if (!saved.equals(expected)) {
			throw new AssertionError("Gespeichert: erwartet " + expected + ", bekommen " + saved);
		}
	}
}
