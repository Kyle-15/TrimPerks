package com.trimperks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * config/trimperks.json. Reloaded by /reload, editable in-game with /trimperks config. Missing keys are filled in
 * with defaults and unknown keys are dropped, so the file always matches the current list in {@link Perks}.
 */
public class TrimPerksConfig {
	/** Every number, keyed like "armor.vex.chance". */
	public Map<String, Double> values = new LinkedHashMap<>();
	/** Turned-off effects: a full key ("armor.vex", "material.netherite") or a bare name ("vex" turns off all three vex effects). */
	public TreeSet<String> disabled = new TreeSet<>();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Double> DEFAULTS = Perks.defaults();

	public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("trimperks.json"); }

	public double get(String key) {
		Double v = values.get(key);
		return v != null ? v : DEFAULTS.getOrDefault(key, 0.0);
	}

	public static boolean isKey(String key) { return DEFAULTS.containsKey(key); }

	public static Double defaultOf(String key) { return DEFAULTS.get(key); }

	public static Iterable<String> keys() { return DEFAULTS.keySet(); }

	public boolean isDisabled(Perks.Perk perk) {
		return disabled.contains(perk.key()) || disabled.contains(perk.key().substring(perk.key().lastIndexOf('.') + 1));
	}

	public static TrimPerksConfig load() {
		TrimPerksConfig config = new TrimPerksConfig();
		try {
			if (Files.exists(file())) {
				TrimPerksConfig read = GSON.fromJson(Files.readString(file()), TrimPerksConfig.class);
				if (read != null) config = read;
			}
		} catch (IOException | RuntimeException e) {
			TrimPerks.LOG.error("Couldn't read {}, using defaults", file(), e);
		}
		Map<String, Double> merged = new LinkedHashMap<>();
		Map<String, Double> read = config.values != null ? config.values : Map.of();
		DEFAULTS.forEach((k, v) -> merged.put(k, read.getOrDefault(k, v)));
		config.values = merged;
		if (config.disabled == null) config.disabled = new TreeSet<>();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.writeString(file(), GSON.toJson(this));
		} catch (IOException e) {
			TrimPerks.LOG.error("Couldn't write {}", file(), e);
		}
	}
}
