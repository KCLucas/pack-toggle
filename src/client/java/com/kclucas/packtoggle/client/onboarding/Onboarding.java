package com.kclucas.packtoggle.client.onboarding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Willkommens- und Tutorial-Screen für alle Mods von KCLucas.
 *
 * <p>Diese Klasse steckt als Kopie in jeder Mod (Vorlage: {@code kclucas-onboarding/template}). Damit trotzdem nur
 * EIN Screen erscheint, wenn mehrere Mods installiert sind, melden sich alle Kopien über die ObjectShare des
 * Fabric Loaders an einer gemeinsamen Stelle an. Dort liegen nur Typen, die alle Mods gemeinsam haben
 * (Java- und Minecraft-Klassen), nie eigene Klassen. Die Kopie mit der höchsten {@link #CODE_VERSION} zeigt den Screen.
 *
 * <p>Welche Tutorials schon gesehen wurden, steht in {@code config/kclucas-mods.json} – pro Profil genau einmal.
 */
public final class Onboarding {

	/** Version dieses Codes. Bei jeder Änderung an Vorlage oder Screen erhöhen. */
	public static final int CODE_VERSION = 1;

	private static final String SHARE_KEY = "kclucas:onboarding";
	private static final Logger LOGGER = LoggerFactory.getLogger("kclucas-onboarding");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SEEN_FILE = FabricLoader.getInstance().getConfigDir().resolve("kclucas-mods.json");

	/** Eine Seite: Überschrift, Erklärung und optional ein Bild im Format 16:9. */
	public record Page(Component title, Component text, @Nullable Identifier image) {
	}

	/**
	 * Das Tutorial einer Mod. Wird {@code version} erhöht, sehen es auch Spieler erneut, die es schon kannten –
	 * gedacht für große Updates.
	 */
	public record Tutorial(String id, Component name, Component description, Identifier icon, int version,
			List<Page> pages) {
	}

	/** Gespeicherter Stand: Mod-ID → zuletzt gesehene Tutorial-Version. */
	private static final class SeenFile {
		Map<String, Integer> tutorialsSeen = new HashMap<>();
	}

	private Onboarding() {
	}

	/** Meldet das Tutorial einer Mod an. Einmal im Client-Initializer aufrufen. */
	public static void register(Tutorial tutorial) {
		Map<String, Object> shared = shared();
		tutorialList(shared).add(encode(tutorial));

		if (!(shared.get("presenterVersion") instanceof Integer version) || version < CODE_VERSION) {
			shared.put("presenterVersion", CODE_VERSION);
			shared.put("presenter", (Runnable) Onboarding::presentUnseen);
			shared.put("opener", (Consumer<String>) Onboarding::presentSingle);
		}

		ClientTickEvents.END_CLIENT_TICK.register(Onboarding::onTick);
	}

	/** Zeigt das Tutorial einer Mod erneut, z. B. über einen Knopf in ihren Einstellungen. */
	@SuppressWarnings("unchecked")
	public static void open(String modId) {
		if (shared().get("opener") instanceof Consumer<?> opener) {
			((Consumer<String>) opener).accept(modId);
		}
	}

	private static void onTick(Minecraft client) {
		Map<String, Object> shared = shared();
		if (Boolean.TRUE.equals(shared.get("presented")) || client.getOverlay() != null) {
			return;
		}
		// Normalerweise im Hauptmenü. Ersetzt ein Client das Hauptmenü durch ein eigenes, dann beim ersten Betreten einer Welt.
		boolean atTitle = client.screen instanceof TitleScreen;
		boolean inWorld = client.level != null && client.screen == null;
		if (!atTitle && !inWorld) {
			return;
		}
		shared.put("presented", Boolean.TRUE);
		if (shared.get("presenter") instanceof Runnable presenter) {
			presenter.run();
		}
	}

	private static void presentUnseen() {
		Map<String, Integer> seen = loadSeen().tutorialsSeen;
		List<Tutorial> unseen = new ArrayList<>();
		for (Tutorial tutorial : registered()) {
			if (tutorial.version() > seen.getOrDefault(tutorial.id(), 0)) {
				unseen.add(tutorial);
			}
		}
		if (unseen.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new OnboardingScreen(client.screen, unseen, true));
	}

	private static void presentSingle(String modId) {
		for (Tutorial tutorial : registered()) {
			if (tutorial.id().equals(modId)) {
				Minecraft client = Minecraft.getInstance();
				client.setScreen(new OnboardingScreen(client.screen, List.of(tutorial), false));
				return;
			}
		}
	}

	/** Merkt sich, dass diese Tutorials gezeigt (oder übersprungen) wurden. */
	static void markSeen(List<Tutorial> tutorials) {
		SeenFile file = loadSeen();
		for (Tutorial tutorial : tutorials) {
			file.tutorialsSeen.merge(tutorial.id(), tutorial.version(), Math::max);
		}
		try {
			Files.createDirectories(SEEN_FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(SEEN_FILE)) {
				GSON.toJson(file, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Konnte {} nicht speichern", SEEN_FILE, e);
		}
	}

	private static SeenFile loadSeen() {
		if (Files.exists(SEEN_FILE)) {
			try (Reader reader = Files.newBufferedReader(SEEN_FILE)) {
				SeenFile file = GSON.fromJson(reader, SeenFile.class);
				if (file != null && file.tutorialsSeen != null) {
					return file;
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Konnte {} nicht lesen", SEEN_FILE, e);
			}
		}
		return new SeenFile();
	}

	// ---- gemeinsame Ablage zwischen den Mods ----

	@SuppressWarnings("unchecked")
	private static Map<String, Object> shared() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.putIfAbsent(SHARE_KEY, new ConcurrentHashMap<String, Object>());
		return (Map<String, Object>) share.get(SHARE_KEY);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> tutorialList(Map<String, Object> shared) {
		return (List<Map<String, Object>>) shared.computeIfAbsent("tutorials", key -> new ArrayList<Map<String, Object>>());
	}

	private static List<Tutorial> registered() {
		List<Tutorial> tutorials = new ArrayList<>();
		for (Map<String, Object> entry : tutorialList(shared())) {
			Tutorial tutorial = decode(entry);
			if (tutorial != null && tutorials.stream().noneMatch(other -> other.id().equals(tutorial.id()))) {
				tutorials.add(tutorial);
			}
		}
		tutorials.sort(Comparator.comparing(tutorial -> tutorial.name().getString()));
		return tutorials;
	}

	private static Map<String, Object> encode(Tutorial tutorial) {
		List<Map<String, Object>> pages = new ArrayList<>();
		for (Page page : tutorial.pages()) {
			Map<String, Object> entry = new HashMap<>();
			entry.put("title", page.title());
			entry.put("text", page.text());
			if (page.image() != null) {
				entry.put("image", page.image());
			}
			pages.add(entry);
		}
		Map<String, Object> entry = new HashMap<>();
		entry.put("id", tutorial.id());
		entry.put("name", tutorial.name());
		entry.put("description", tutorial.description());
		entry.put("icon", tutorial.icon());
		entry.put("version", tutorial.version());
		entry.put("pages", pages);
		return entry;
	}

	/** Liest einen Eintrag, den vielleicht eine andere (ältere oder neuere) Kopie dieser Klasse geschrieben hat. */
	private static @Nullable Tutorial decode(Map<String, Object> entry) {
		if (!(entry.get("id") instanceof String id) || !(entry.get("name") instanceof Component name)
				|| !(entry.get("icon") instanceof Identifier icon) || !(entry.get("pages") instanceof List<?> rawPages)) {
			return null;
		}
		Component description = entry.get("description") instanceof Component component ? component : Component.empty();
		int version = entry.get("version") instanceof Integer number ? number : 1;

		List<Page> pages = new ArrayList<>();
		for (Object raw : rawPages) {
			if (raw instanceof Map<?, ?> page && page.get("title") instanceof Component title
					&& page.get("text") instanceof Component text) {
				pages.add(new Page(title, text, page.get("image") instanceof Identifier image ? image : null));
			}
		}
		return pages.isEmpty() ? null : new Tutorial(id, name, description, icon, version, pages);
	}
}
