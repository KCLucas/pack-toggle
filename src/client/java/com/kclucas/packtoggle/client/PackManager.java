package com.kclucas.packtoggle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Speichert, entfernt und stellt die Ressourcenpakete des Spielers wieder her.
 * Pakete, die Minecraft selbst erzwingt (Standard-Texturen, Mod-Ressourcen, Server-Paket), bleiben immer unberührt.
 */
public final class PackManager {

	private static final Component TOAST_TITLE = Component.translatable("packtoggle.toast.title");

	private PackManager() {
	}

	/** Die frei wählbaren, gerade aktiven Pakete – von unten nach oben. */
	public static List<String> activeUserPacks(Minecraft client) {
		List<String> ids = new ArrayList<>();
		for (Pack pack : client.getResourcePackRepository().getSelectedPacks()) {
			if (isUserPack(pack)) {
				ids.add(pack.getId());
			}
		}
		return ids;
	}

	private static boolean isUserPack(Pack pack) {
		return !pack.isRequired() && !pack.isFixedPosition();
	}

	/** Merkt sich die aktiven Pakete samt Reihenfolge. */
	public static void save(Minecraft client) {
		List<String> active = activeUserPacks(client);
		if (active.isEmpty()) {
			toast(client, Component.translatable("packtoggle.toast.nothing_active"));
			return;
		}
		store(active);
		toast(client, Component.translatable("packtoggle.toast.saved", active.size()));
	}

	/** Übernimmt eine Liste (von unten nach oben) als gespeicherten Stand. */
	public static void store(List<String> packs) {
		PackToggleConfig config = PackToggleClient.getConfig();
		config.savedPacks = new ArrayList<>(packs);
		config.save();
	}

	/** Nimmt alle frei wählbaren Pakete raus und lädt die Ressourcen neu. */
	public static void removeAll(Minecraft client) {
		int removed = activeUserPacks(client).size();
		apply(client, List.of());
		toast(client, Component.translatable("packtoggle.toast.removed", removed));
	}

	/** Setzt die gespeicherten Pakete in der gespeicherten Reihenfolge wieder ein. */
	public static void restore(Minecraft client) {
		List<String> saved = PackToggleClient.getConfig().savedPacks;
		if (saved.isEmpty()) {
			toast(client, Component.translatable("packtoggle.toast.nothing_saved"));
			return;
		}

		PackRepository repository = client.getResourcePackRepository();
		// Neu einlesen, falls Pakete seit dem Speichern in den Ordner gelegt oder gelöscht wurden.
		repository.reload();
		List<String> available = new ArrayList<>();
		for (String id : saved) {
			if (repository.isAvailable(id)) {
				available.add(id);
			}
		}

		apply(client, available);
		int missing = saved.size() - available.size();
		toast(client, missing == 0
				? Component.translatable("packtoggle.toast.restored", available.size())
				: Component.translatable("packtoggle.toast.restored_missing", available.size(), missing));
	}

	/**
	 * Der Hotkey: Sind Pakete aktiv, werden sie entfernt, sonst die gespeicherten wieder eingesetzt.
	 * Wurde noch nie gespeichert, sichert der erste Druck die aktiven Pakete automatisch, damit nichts verloren geht.
	 */
	public static void toggle(Minecraft client) {
		List<String> active = activeUserPacks(client);
		if (active.isEmpty()) {
			restore(client);
			return;
		}
		if (PackToggleClient.getConfig().savedPacks.isEmpty()) {
			store(active);
			apply(client, List.of());
			toast(client, Component.translatable("packtoggle.toast.saved_and_removed", active.size()));
			return;
		}
		removeAll(client);
	}

	/** Aktiviert genau diese frei wählbaren Pakete (von unten nach oben) über den erzwungenen. */
	private static void apply(Minecraft client, List<String> userPacks) {
		PackRepository repository = client.getResourcePackRepository();
		List<String> selection = new ArrayList<>();
		for (Pack pack : repository.getSelectedPacks()) {
			if (!isUserPack(pack)) {
				selection.add(pack.getId());
			}
		}
		selection.addAll(userPacks);
		repository.setSelected(selection);
		// Schreibt die Auswahl in die Optionen und lädt die Ressourcen neu, falls sich etwas geändert hat.
		client.options.updateResourcePacks(repository);
	}

	public static void toast(Minecraft client, Component message) {
		SystemToast.addOrUpdate(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				TOAST_TITLE, message);
	}
}
