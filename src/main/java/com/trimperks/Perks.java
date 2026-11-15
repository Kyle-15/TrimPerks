package com.trimperks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every material and pattern effect, its description and its default numbers. Each number is a config key
 * "<perk key>.<name>" (e.g. armor.vex.chance) that ops can change in-game. In a description, {name} shows the
 * value and {name%} shows it as a percentage.
 */
public final class Perks {
	public enum Kind { GENERAL, MATERIAL, ARMOR, WEAPON, TOOL }

	public record Perk(Kind kind, String key, String name, String template, boolean pvp, Map<String, Double> defaults) {
		public String describe(TrimPerksConfig config) {
			Matcher m = PLACEHOLDER.matcher(template);
			StringBuilder out = new StringBuilder();
			while (m.find()) {
				double v = config.get(key + "." + m.group(1));
				m.appendReplacement(out, Matcher.quoteReplacement(m.group(2) != null ? format(v * 100) + "%" : format(v)));
			}
			return m.appendTail(out).toString();
		}
	}

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)(%)?}");
	public static final List<Perk> ALL = new ArrayList<>();
	public static final List<String> PATTERNS = List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide",
		"snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt");
	public static final List<String> MATERIALS = List.of("iron", "copper", "gold", "redstone", "lapis", "amethyst",
		"emerald", "quartz", "diamond", "netherite", "resin");
	/** Patterns that can matter against players; config general.pvp_multiplier scales them. */
	public static final Set<String> PVP = Set.of("ward", "vex", "rib", "shaper", "silence", "flow", "bolt");

	public static String format(double v) {
		if (v == Math.rint(v)) return Long.toString((long) v);
		return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
	}

	public static String title(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

	private static void add(Kind kind, String name, String template, Object... values) {
		Map<String, Double> defaults = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) defaults.put((String) values[i], ((Number) values[i + 1]).doubleValue());
		String key = kind == Kind.GENERAL ? "general" : kind.name().toLowerCase(Locale.ROOT) + "." + name;
		ALL.add(new Perk(kind, key, title(name), template, kind != Kind.MATERIAL && PVP.contains(name), defaults));
	}

	/** Materials: strength/duration/cooldown/range multiply the pattern's numbers. */
	private static void material(String name, String template, double strength, double duration, double cooldown, double range, Object... extra) {
		Object[] all = new Object[8 + extra.length];
		System.arraycopy(new Object[] {"strength", strength, "duration", duration, "cooldown", cooldown, "range", range}, 0, all, 0, 8);
		System.arraycopy(extra, 0, all, 8, extra.length);
		add(Kind.MATERIAL, name, template, all);
	}

	static {
		add(Kind.GENERAL, "general", "Effects marked [PvP] work at {pvp_multiplier%} strength when they hit or protect against a player. "
			+ "A new or changed trim is inactive for {settle_seconds}s. A material-only retrim costs {material_swap_levels} levels "
			+ "every time (it never grows). Protection and trims together never block more than {max_damage_reduction%} of damage. "
			+ "Cooldown bars at the top of the screen: {cooldown_bars} (1 = on, 0 = off for everyone).",
			"pvp_multiplier", 0.75, "settle_seconds", 60, "material_swap_levels", 3, "max_damage_reduction", 0.64, "cooldown_bars", 1);

		material("iron", "The baseline: no upside, no downside.", 1, 1, 1, 1);
		material("copper", "Lasts x{duration} as long, but strength x{strength}.", 0.75, 1.5, 1, 1);
		material("gold", "When a trim gives you a status effect, allies within {ally_range} blocks (your team, never someone you're fighting) get it too for {ally_share%} as long; your own strength x{strength}.", 0.75, 1, 1, 1, "ally_range", 8, "ally_share", 0.5);
		material("redstone", "Cooldown x{cooldown}, but duration x{duration}.", 1, 0.6, 0.6, 1);
		material("lapis", "Strength x{strength}, but each trigger costs {xp_cost} XP points (competes with Mending).", 1.25, 1, 1, 1, "xp_cost", 2);
		material("amethyst", "Range x{range}, but triggering plays a chime that alerts mobs and sculk.", 1, 1, 1, 1.5);
		material("emerald", "{skip_chance%} chance to skip the cooldown, but strength x{strength}.", 0.9, 1, 1, 1, "skip_chance", 0.15);
		material("quartz", "Strength x{nether} in the Nether, x{elsewhere} everywhere else.", 1, 1, 1, 1, "nether", 1.5, "elsewhere", 0.75);
		material("diamond", "Strength x{strength}, but cooldown x{cooldown}.", 1.25, 1, 1.25, 1);
		material("netherite", "Strength x{strength} and duration x{duration}, but armor slows you {armor_slow%} per piece and tools attack/mine {tool_slow%} slower.", 1.25, 1.25, 1, 1, "armor_slow", 0.025, "tool_slow", 0.05);
		material("resin", "The effect lingers {linger}s after you unequip or swap away, but strength x{strength}.", 0.85, 1, 1, 1, "linger", 5);

		add(Kind.ARMOR, "sentry", "{chance%} chance to catch an arrow that hits you: no damage, and you keep the arrow if it could be picked up.", "chance", 0.2);
		add(Kind.ARMOR, "dune", "No suffocation from falling blocks; Speed {speed_level} on sand.", "speed_level", 1);
		add(Kind.ARMOR, "coast", "Swim {swim%} faster. Doesn't work with Depth Strider boots.", "swim", 0.3);
		add(Kind.ARMOR, "wild", "Cactus and berry bushes don't hurt you; leaves don't slow you.");
		add(Kind.ARMOR, "ward", "Below {hearts} hearts you get Resistance for {duration}s. {cooldown}s cooldown.", "hearts", 4, "duration", 3, "cooldown", 90);
		add(Kind.ARMOR, "eye", "Endermen don't aggro. Sneak to make mobs within {range} blocks glow for {duration}s. {cooldown}s cooldown.", "range", 16, "duration", 3, "cooldown", 60);
		add(Kind.ARMOR, "vex", "{chance%} chance to dodge a melee hit. {cooldown}s cooldown.", "chance", 0.1, "cooldown", 20);
		add(Kind.ARMOR, "tide", "{air%} more air underwater. Doesn't work with a Respiration helmet.", "air", 0.5);
		add(Kind.ARMOR, "snout", "Piglins stay neutral even without gold.");
		add(Kind.ARMOR, "rib", "Fire and Wither last {reduction%} shorter on you. Doesn't work with Fire Protection.", "reduction", 0.5);
		add(Kind.ARMOR, "spire", "Your first fall every {cooldown}s takes no damage, up to {max_height} blocks. Doesn't work with Feather Falling boots.", "cooldown", 30, "max_height", 10);
		add(Kind.ARMOR, "wayfinder", "While sneaking, the action bar shows the distance and direction to your last death.");
		add(Kind.ARMOR, "shaper", "+{reach} block placing reach.", "reach", 1);
		add(Kind.ARMOR, "silence", "Sculk sensors and Wardens can't hear your footsteps.");
		add(Kind.ARMOR, "raiser", "Jump Boost {level} while sprinting.", "level", 1);
		add(Kind.ARMOR, "host", "Your tamed pets within {range} blocks slowly regenerate.", "range", 16);
		add(Kind.ARMOR, "flow", "Press jump in midair to dash. {cooldown}s cooldown.", "cooldown", 10);
		add(Kind.ARMOR, "bolt", "Knocks melee attackers back. {cooldown}s cooldown.", "cooldown", 15);

		add(Kind.WEAPON, "sentry", "+{damage%} damage to illagers.", "damage", 0.2);
		add(Kind.WEAPON, "dune", "Hits slow targets for {duration}s.", "duration", 1);
		add(Kind.WEAPON, "coast", "+{damage%} damage to aquatic mobs. Doesn't work with Impaling.", "damage", 0.2);
		add(Kind.WEAPON, "wild", "Crits poison the target for {duration}s.", "duration", 2);
		add(Kind.WEAPON, "ward", "Killing a hostile mob or player gives 2 hearts of Absorption for {duration}s. {cooldown}s cooldown.", "duration", 10, "cooldown", 30);
		add(Kind.WEAPON, "eye", "Crits make the target glow for {duration}s.", "duration", 3);
		add(Kind.WEAPON, "vex", "+{speed%} attack speed.", "speed", 0.1);
		add(Kind.WEAPON, "tide", "+{knockback%} knockback in water.", "knockback", 0.5);
		add(Kind.WEAPON, "snout", "Piglins you hit don't call their friends.");
		add(Kind.WEAPON, "rib", "Crits set the target on fire for {duration}s. Doesn't work with Fire Aspect.", "duration", 2);
		add(Kind.WEAPON, "spire", "+{damage%} damage while falling (not maces).", "damage", 0.1);
		add(Kind.WEAPON, "wayfinder", "Mob drops fly to you.");
		add(Kind.WEAPON, "shaper", "+{reach} attack reach.", "reach", 0.5);
		add(Kind.WEAPON, "silence", "+{damage%} damage from behind to mobs that aren't targeting you.", "damage", 0.2);
		add(Kind.WEAPON, "raiser", "Hits pop light mobs upward.");
		add(Kind.WEAPON, "host", "Your pets deal +{damage%} damage to whatever you hit.", "damage", 0.2);
		add(Kind.WEAPON, "flow", "+{speed%} attack speed for {duration}s after a sprint hit.", "speed", 0.15, "duration", 3);
		add(Kind.WEAPON, "bolt", "Every {hits}th hit sparks {damage} damage to a nearby hostile mob.", "hits", 5, "damage", 2);

		add(Kind.TOOL, "sentry", "Ores drop {xp%} more XP.", "xp", 0.25);
		add(Kind.TOOL, "dune", "Mines sand and gravel {speed%} faster.", "speed", 0.5);
		add(Kind.TOOL, "coast", "Fishing rod: fish bite {lure%} sooner.", "lure", 0.2);
		add(Kind.TOOL, "wild", "Hoe: right-click a fully grown crop to harvest and replant it.");
		add(Kind.TOOL, "ward", "Stops at 1 durability instead of breaking; repair it to keep mining.");
		add(Kind.TOOL, "eye", "Sneak + right-click the air to see how many ores are within {range} blocks (count only). {cooldown}s cooldown.", "range", 5, "cooldown", 120);
		add(Kind.TOOL, "vex", "Mines {speed%} faster at light level {light} or lower.", "speed", 0.1, "light", 7);
		add(Kind.TOOL, "tide", "Full mining speed while swimming.");
		add(Kind.TOOL, "snout", "{bonus%} more gold from Nether gold ore and gilded blackstone.", "bonus", 0.25);
		add(Kind.TOOL, "rib", "Mines Nether blocks {speed%} faster.", "speed", 0.3);
		add(Kind.TOOL, "spire", "No mining slowdown while in the air.");
		add(Kind.TOOL, "wayfinder", "Block drops fly to you.");
		add(Kind.TOOL, "shaper", "+{reach} block mining reach.", "reach", 1);
		add(Kind.TOOL, "silence", "Mining makes no sculk vibrations.");
		add(Kind.TOOL, "raiser", "Hoe: right-click to bonemeal one crop. {cooldown}s cooldown.", "cooldown", 30);
		add(Kind.TOOL, "host", "Killing a hostile mob or player heals your nearby pets {hearts} hearts.", "hearts", 2);
		add(Kind.TOOL, "flow", "Chain-mining gets {per_block%} faster per block, up to {max%}.", "per_block", 0.05, "max", 0.25);
		add(Kind.TOOL, "bolt", "Axe: fells a whole natural tree (not builds), up to {max_logs} logs (1 durability each). Sneak to break just one log.", "max_logs", 64);
	}

	public static Map<String, Double> defaults() {
		Map<String, Double> all = new LinkedHashMap<>();
		for (Perk p : ALL) p.defaults().forEach((k, v) -> all.put(p.key() + "." + k, v));
		return all;
	}

	private Perks() {}
}
