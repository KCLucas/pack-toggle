package com.kclucas.packtoggle.client.onboarding;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gemeinsamer Ordner aller Mods von KCLucas: {@code config/KCMods/<mod-id>/...}, dazu Dateien für alle Mods
 * direkt in {@code config/KCMods/}. Liegt noch eine Datei am alten Ort direkt in {@code config/}, wird sie beim
 * ersten Zugriff dorthin verschoben - so bleiben die Einstellungen beim Update erhalten.
 */
public final class KcFiles {

	public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("KCMods");
	private static final Logger LOGGER = LoggerFactory.getLogger("kclucas-files");

	private KcFiles() {
	}

	/** Eine Datei der Mod, z. B. {@code file("ahrestock", "config.json", "ahrestock.json")}. */
	public static Path file(String modId, String name, String... oldNamesInConfig) {
		return migrate(ROOT.resolve(modId).resolve(name), oldNamesInConfig);
	}

	/** Eine Datei, die alle Mods von KCLucas gemeinsam nutzen. */
	public static Path shared(String name, String... oldNamesInConfig) {
		return migrate(ROOT.resolve(name), oldNamesInConfig);
	}

	private static Path migrate(Path target, String... oldNames) {
		try {
			Files.createDirectories(target.getParent());
			for (String oldName : oldNames) {
				Path old = FabricLoader.getInstance().getConfigDir().resolve(oldName);
				if (!Files.exists(target) && Files.exists(old)) {
					Files.move(old, target, StandardCopyOption.REPLACE_EXISTING);
					LOGGER.info("{} nach {} verschoben", old, target);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Konnte {} nicht vorbereiten", target, e);
		}
		return target;
	}
}
