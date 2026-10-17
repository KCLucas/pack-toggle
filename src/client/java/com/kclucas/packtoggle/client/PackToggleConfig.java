package com.kclucas.packtoggle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.kclucas.packtoggle.client.onboarding.KcFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PackToggleConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(PackToggleClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = KcFiles.file(PackToggleClient.MOD_ID, "config.json", "packtoggle.json");

	/**
	 * Die gespeicherten Ressourcenpakete in der Reihenfolge, in der Minecraft sie lädt:
	 * das erste liegt ganz unten, das letzte ganz oben (höchste Priorität).
	 */
	public List<String> savedPacks = new ArrayList<>();

	public static PackToggleConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				PackToggleConfig config = GSON.fromJson(reader, PackToggleConfig.class);
				if (config != null) {
					if (config.savedPacks == null) {
						config.savedPacks = new ArrayList<>();
					}
					return config;
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Konnte {} nicht lesen, nutze Standardwerte", PATH, e);
			}
		}
		return new PackToggleConfig();
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Konnte {} nicht speichern", PATH, e);
		}
	}
}
