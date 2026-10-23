package com.kclucas.packtoggle.client.onboarding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
 * Willkommens-Tour und "Was ist neu" für alle Mods von KCLucas.
 *
 * <ul>
 *   <li>Ist eine Mod neu in einem Profil, erscheint ihre Tour.</li>
 *   <li>Wurde sie seit dem letzten Start aktualisiert, erscheinen die Änderungen seit der zuletzt gesehenen Version.</li>
 * </ul>
 * Gemerkt wird pro Mod die zuletzt gesehene Mod-Version, in {@code config/KCMods/seen.json}.
 *
 * <p>Diese Klasse steckt als Kopie in jeder Mod (Vorlage: {@code kclucas-onboarding/template}). Damit trotzdem nur
 * EIN Screen erscheint, melden sich alle Kopien über die ObjectShare des Fabric Loaders an einer gemeinsamen Stelle an.
 * Dort liegen nur Typen, die alle Mods gemeinsam haben (Java- und Minecraft-Klassen), nie eigene Klassen.
 * Die Kopie mit der höchsten {@link #CODE_VERSION} zeigt den Screen und liest dabei auch Einträge älterer Kopien.
 */
public final class Onboarding {

	/** Version dieses Codes. Bei jeder Änderung an Vorlage oder Screen erhöhen. */
	public static final int CODE_VERSION = 4;

	private static final String SHARE_KEY = "kclucas:onboarding";
	private static final Logger LOGGER = LoggerFactory.getLogger("kclucas-onboarding");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int STABLE_TICKS = 10;
	/** Steht für "schon gesehen, Version unbekannt" (aus der alten Datei übernommen). */
	private static final String UNKNOWN_VERSION = "0";

	private static @Nullable Screen stableScreen;
	private static int stableTicks;

	/** Eine Seite der Tour: Überschrift, Erklärung und optional ein Bild im Format 16:9. */
	public record Page(Component title, Component text, @Nullable Identifier image) {
	}

	/** Was in einer Version neu ist, als kurze Stichpunkte. */
	public record Release(String version, List<Component> notes) {
	}

	/**
	 * Tour und Änderungen einer Mod. {@code releases} am besten neueste zuerst; gezeigt werden nach einem Update
	 * alle Einträge, die neuer sind als die zuletzt gesehene Version.
	 */
	public record Tutorial(String id, Component name, Component description, Identifier icon, List<Page> pages,
			List<Release> releases) {

		/** Die installierte Version dieser Mod, laut Fabric Loader. */
		public String modVersion() {
			return FabricLoader.getInstance().getModContainer(this.id)
					.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
					.orElse(UNKNOWN_VERSION);
		}
	}

	/** Was für eine Mod gezeigt wird: ganze Tour, oder nur was seit {@code seenVersion} neu ist. */
	public record Pending(Tutorial tutorial, @Nullable String seenVersion, List<Release> news) {
		public boolean isUpdate() {
			return this.seenVersion != null;
		}
	}

	/** Gespeicherter Stand: Mod-ID → zuletzt gesehene Mod-Version. */
	private static final class SeenFile {
		Map<String, String> modVersions = new HashMap<>();
	}

	private Onboarding() {
	}

	/** Meldet eine Mod an. Einmal im Client-Initializer aufrufen. */
	public static void register(Tutorial tutorial) {
		Map<String, Object> shared = shared();
		tutorialList(shared).add(encode(tutorial));

		if (!(shared.get("presenterVersion") instanceof Integer version) || version < CODE_VERSION) {
			shared.put("presenterVersion", CODE_VERSION);
			shared.put("presenter", (Runnable) Onboarding::presentPending);
			shared.put("opener", (Consumer<String>) Onboarding::presentSingle);
		}

		ClientTickEvents.END_CLIENT_TICK.register(Onboarding::onTick);
	}

	/** Zeigt die Tour einer Mod erneut, z. B. über einen Knopf in ihren Einstellungen. */
	@SuppressWarnings("unchecked")
	public static void open(String modId) {
		if (shared().get("opener") instanceof Consumer<?> opener) {
			((Consumer<String>) opener).accept(modId);
		}
	}

	/** Prüft sofort noch einmal, ob es etwas zu zeigen gibt (Tour oder Neuigkeiten). */
	public static void showPending() {
		if (shared().get("presenter") instanceof Runnable presenter) {
			presenter.run();
		}
	}

	private static void onTick(Minecraft client) {
		Map<String, Object> shared = shared();
		if (Boolean.TRUE.equals(shared.get("presented")) || client.getOverlay() != null) {
			return;
		}
		if (!isGoodMoment(client)) {
			return;
		}
		shared.put("presented", Boolean.TRUE);
		showPending();
	}

	/**
	 * Der richtige Moment ist das Hauptmenü. Manche Clients (z. B. NoRisk) ersetzen es durch ein eigenes, deshalb
	 * zählt außerhalb einer Welt auch jeder Screen, der nicht von Minecraft selbst stammt – aber erst, wenn er
	 * eine halbe Sekunde lang offen ist, damit kurz aufblitzende Zwischen-Screens nicht auslösen.
	 * Greift beides nicht, erscheint der Screen beim ersten Betreten einer Welt.
	 */
	private static boolean isGoodMoment(Minecraft client) {
		Screen screen = client.screen;
		if (screen != stableScreen) {
			stableScreen = screen;
			stableTicks = 0;
		} else {
			stableTicks++;
		}

		if (client.level != null) {
			return screen == null;
		}
		if (screen == null || stableTicks < STABLE_TICKS) {
			return false;
		}
		return screen instanceof TitleScreen || !screen.getClass().getName().startsWith("net.minecraft.");
	}

	private static void presentPending() {
		Map<String, String> seen = loadSeen().modVersions;
		List<Pending> pending = new ArrayList<>();
		List<Tutorial> silentlySeen = new ArrayList<>();
		for (Tutorial tutorial : registered()) {
			String current = tutorial.modVersion();
			String last = seen.get(tutorial.id());
			if (last == null) {
				pending.add(new Pending(tutorial, null, List.of()));
			} else if (!last.equals(current)) {
				List<Release> news = new ArrayList<>();
				for (Release release : tutorial.releases()) {
					if (compareVersions(release.version(), last) > 0 && compareVersions(release.version(), current) <= 0) {
						news.add(release);
					}
				}
				news.sort((a, b) -> compareVersions(b.version(), a.version()));
				if (last.equals(UNKNOWN_VERSION) && news.size() > 1) {
					// Aus der alten Datei übernommen: welche Version das war, weiß niemand – dann nur die neueste.
					news = new ArrayList<>(news.subList(0, 1));
				}
				if (news.isEmpty()) {
					silentlySeen.add(tutorial);
				} else {
					pending.add(new Pending(tutorial, last, news));
				}
			}
		}
		if (!silentlySeen.isEmpty()) {
			markSeen(silentlySeen);
		}
		if (pending.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new OnboardingScreen(client.screen, pending, true));
	}

	private static void presentSingle(String modId) {
		for (Tutorial tutorial : registered()) {
			if (tutorial.id().equals(modId)) {
				Minecraft client = Minecraft.getInstance();
				client.setScreen(new OnboardingScreen(client.screen, List.of(new Pending(tutorial, null, List.of())), false));
				return;
			}
		}
	}

	/** Merkt sich die jetzt installierte Version dieser Mods als gesehen. */
	static void markSeen(List<Tutorial> tutorials) {
		SeenFile file = loadSeen();
		for (Tutorial tutorial : tutorials) {
			file.modVersions.put(tutorial.id(), tutorial.modVersion());
		}
		Path path = seenFilePath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(file, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Konnte {} nicht speichern", path, e);
		}
	}

	/** {@code config/KCMods/seen.json}: welche Mod-Version zuletzt gesehen wurde. */
	public static Path seenFilePath() {
		return KcFiles.ROOT.resolve("seen.json");
	}

	/** Liest den Stand; ältere Versionen hatten {@code config/kclucas-mods.json}, die wird übernommen. */
	private static SeenFile loadSeen() {
		Path path = seenFilePath();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				SeenFile file = GSON.fromJson(reader, SeenFile.class);
				if (file != null && file.modVersions != null) {
					return file;
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Konnte {} nicht lesen", path, e);
			}
		}
		SeenFile file = new SeenFile();
		Path old = FabricLoader.getInstance().getConfigDir().resolve("kclucas-mods.json");
		if (Files.exists(old)) {
			try (Reader reader = Files.newBufferedReader(old)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("tutorialsSeen")) {
					json.getAsJsonObject("tutorialsSeen").keySet().forEach(id -> file.modVersions.put(id, UNKNOWN_VERSION));
				}
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Konnte {} nicht übernehmen", old, e);
			}
		}
		return file;
	}

	/** Vergleicht Versionen wie "1.3.1" und "1.10.0" Zahl für Zahl; Zusätze wie "-beta" zählen nicht. */
	static int compareVersions(String a, String b) {
		String[] left = a.split("[^0-9]+");
		String[] right = b.split("[^0-9]+");
		for (int i = 0; i < Math.max(left.length, right.length); i++) {
			long x = i < left.length && !left[i].isEmpty() ? Long.parseLong(left[i]) : 0;
			long y = i < right.length && !right[i].isEmpty() ? Long.parseLong(right[i]) : 0;
			if (x != y) {
				return Long.compare(x, y);
			}
		}
		return 0;
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
		List<Map<String, Object>> releases = new ArrayList<>();
		for (Release release : tutorial.releases()) {
			Map<String, Object> entry = new HashMap<>();
			entry.put("version", release.version());
			entry.put("notes", List.copyOf(release.notes()));
			releases.add(entry);
		}
		Map<String, Object> entry = new HashMap<>();
		entry.put("id", tutorial.id());
		entry.put("name", tutorial.name());
		entry.put("description", tutorial.description());
		entry.put("icon", tutorial.icon());
		entry.put("pages", pages);
		entry.put("releases", releases);
		return entry;
	}

	/**
	 * Liest einen Eintrag, den vielleicht eine andere (ältere oder neuere) Kopie dieser Klasse geschrieben hat.
	 * Ältere Kopien schicken noch keine Änderungsliste mit - dann gibt es für die Mod eben nur die Tour.
	 */
	private static @Nullable Tutorial decode(Map<String, Object> entry) {
		if (!(entry.get("id") instanceof String id) || !(entry.get("name") instanceof Component name)
				|| !(entry.get("icon") instanceof Identifier icon) || !(entry.get("pages") instanceof List<?> rawPages)) {
			return null;
		}
		Component description = entry.get("description") instanceof Component component ? component : Component.empty();

		List<Page> pages = new ArrayList<>();
		for (Object raw : rawPages) {
			if (raw instanceof Map<?, ?> page && page.get("title") instanceof Component title
					&& page.get("text") instanceof Component text) {
				pages.add(new Page(title, text, page.get("image") instanceof Identifier image ? image : null));
			}
		}
		List<Release> releases = new ArrayList<>();
		if (entry.get("releases") instanceof List<?> rawReleases) {
			for (Object raw : rawReleases) {
				if (raw instanceof Map<?, ?> release && release.get("version") instanceof String version
						&& release.get("notes") instanceof List<?> rawNotes) {
					List<Component> notes = new ArrayList<>();
					for (Object note : rawNotes) {
						if (note instanceof Component component) {
							notes.add(component);
						}
					}
					releases.add(new Release(version, notes));
				}
			}
		}
		return pages.isEmpty() ? null : new Tutorial(id, name, description, icon, pages, releases);
	}
}
