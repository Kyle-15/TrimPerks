package com.trimperks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

/**
 * The /trimperks guide window. Uses vanilla dialogs, so players don't need the mod installed. It's rebuilt every
 * time it opens, so the numbers always match the current config.
 */
public final class Guide {
	public static void open(ServerPlayer player) {
		player.openDialog(Holder.direct(build(player.level().getServer())));
	}

	public static Dialog build(MinecraftServer server) {
		TrimPerksConfig c = TrimPerks.config;
		JsonArray buttons = new JsonArray();
		buttons.add(button("How it works", page("How trims work", intro(c))));
		buttons.add(button("Materials", page("Materials: how a trim works", section(c, Perks.Kind.MATERIAL,
			"The material changes the pattern's numbers. Every material has a downside."))));
		buttons.add(button("Armor sets", page("Armor patterns: full-set bonus", section(c, Perks.Kind.ARMOR,
			"All 4 armor pieces need the same pattern. Materials can mix: each piece gives 25% of its material."))));
		buttons.add(button("Tools & weapons", page("Tool patterns", tools(c))));

		JsonObject index = new JsonObject();
		index.addProperty("type", "minecraft:multi_action");
		index.add("title", text("TrimPerks", "gold", true));
		JsonArray body = new JsonArray();
		body.add(message(text("Trims do things. The pattern decides what, the material decides how. Pick a page:", "white", false)));
		index.add("body", body);
		index.add("actions", buttons);
		index.addProperty("columns", 2);
		return Dialog.DIRECT_CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), index).getOrThrow();
	}

	private static JsonArray intro(TrimPerksConfig c) {
		JsonArray body = new JsonArray();
		body.add(message(text(Perks.ALL.getFirst().describe(c), "white", false)));
		body.add(message(text("Change a trim's material: smithing table with the item and a new material, template slot empty. "
			+ "Remove a trim: grindstone with flint in the second slot (you lose the template). "
			+ "Enchantments are never touched, and a trim never stacks with an enchantment that does the same thing.", "white", false)));
		body.add(message(text("Cooldowns show as a bar at the top of your screen (blue = armor, red = weapon, yellow = tool). "
			+ "It turns green with a ding when the effect is ready. /trimperks bars off hides them, /trimperks bars on brings them back.", "white", false)));
		return body;
	}

	private static JsonArray section(TrimPerksConfig c, Perks.Kind kind, String header) {
		JsonArray body = new JsonArray();
		body.add(message(text(header, "gray", false)));
		for (Perks.Perk p : Perks.ALL) if (p.kind() == kind) body.add(message(entry(c, p, p.name())));
		return body;
	}

	private static JsonArray tools(TrimPerksConfig c) {
		JsonArray body = new JsonArray();
		body.add(message(text("One trim per tool; it works while the tool is in your main hand. Weapons: swords, axes, maces, "
			+ "tridents, bows, crossbows. Mining tools: pickaxes, shovels, hoes, axes, fishing rods.", "gray", false)));
		for (String pattern : Perks.PATTERNS) {
			JsonArray line = new JsonArray();
			line.add(text(Perks.title(pattern), "gold", true));
			for (Perks.Perk p : Perks.ALL) {
				if (!p.key().endsWith("." + pattern) || (p.kind() != Perks.Kind.WEAPON && p.kind() != Perks.Kind.TOOL)) continue;
				line.add(text("\n" + (p.kind() == Perks.Kind.WEAPON ? "Weapon: " : "Mining: "), "aqua", false));
				line.add(entry(c, p, null));
			}
			body.add(message(line));
		}
		return body;
	}

	/** One effect: bold name (unless null), PvP tag, description, and a red note if it's turned off. */
	private static JsonArray entry(TrimPerksConfig c, Perks.Perk p, String name) {
		JsonArray line = new JsonArray();
		line.add(text("", "white", false));
		if (name != null) line.add(text(name + "  ", "gold", true));
		if (p.pvp() && name != null) line.add(text("[PvP x" + Perks.format(c.get("general.pvp_multiplier")) + "] ", "red", false));
		boolean off = c.isDisabled(p);
		JsonObject desc = text(p.describe(c), off ? "dark_gray" : "white", false);
		if (off) desc.addProperty("strikethrough", true);
		line.add(desc);
		if (off) line.add(text(" (turned off)", "red", false));
		return line;
	}

	// ---------------------------------------------------------------- op settings window: /trimperks config

	public static void openOp(ServerPlayer player) {
		player.openDialog(Holder.direct(buildOp(player.level().getServer())));
	}

	public static Dialog buildOp(MinecraftServer server) {
		TrimPerksConfig c = TrimPerks.config;
		JsonArray buttons = new JsonArray();
		buttons.add(button("Commands", page("Op commands", opCommands(), BACK_OP)));
		String[][] kinds = {{"GENERAL", "General"}, {"MATERIAL", "Materials"}, {"ARMOR", "Armor sets"}, {"WEAPON", "Weapons"}, {"TOOL", "Mining tools"}};
		for (String[] k : kinds) buttons.add(button(k[1], page("Settings: " + k[1], opSettings(c, Perks.Kind.valueOf(k[0])), BACK_OP)));
		JsonObject index = new JsonObject();
		index.addProperty("type", "minecraft:multi_action");
		index.add("title", text("TrimPerks settings (ops)", "gold", true));
		JsonArray body = new JsonArray();
		body.add(message(text("Every number here is live: changes apply at once, save to config/trimperks.json, and show up in "
			+ "/trimperks guide for players. Click any setting to put its command in chat.", "white", false)));
		index.add("body", body);
		index.add("actions", buttons);
		index.addProperty("columns", 2);
		return Dialog.DIRECT_CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), index).getOrThrow();
	}

	private static JsonArray opCommands() {
		String[][] rows = {
			{"/trimperks config", "Opens this window."},
			{"/trimperks config set <key> <value>", "Changes one number. Tab shows every key. Example: set armor.vex.chance 0.05"},
			{"/trimperks config get <key>", "Shows the current value and the default."},
			{"/trimperks config reset <key>", "Puts one number back to its default."},
			{"/trimperks config disable <name>", "Turns an effect off. \"vex\" turns off all three Vex effects, \"armor.vex\" just the armor one, \"netherite\" a material (it then works like iron)."},
			{"/trimperks config enable <name>", "Turns it back on."},
			{"/trimperks config reload", "Re-reads the file after editing it by hand. Plain /reload does this too."},
			{"/trimperks guide", "The player guide (anyone can open it)."},
			{"/trimperks bars on|off", "Shows or hides your own cooldown bars (anyone). general.cooldown_bars 0 turns them off for everyone."},
		};
		JsonArray body = new JsonArray();
		for (String[] r : rows) {
			JsonArray line = new JsonArray();
			line.add(text("", "white", false));
			JsonObject cmd = text(r[0], "yellow", true);
			cmd.add("click_event", suggest(r[0].replaceAll(" <.*", " ")));
			line.add(cmd);
			line.add(text("\n" + r[1], "white", false));
			body.add(message(line));
		}
		body.add(message(text("Balance tips: chances and percentages are fractions (0.25 = 25%). Durations and cooldowns are seconds. "
			+ "Material strength/duration/cooldown/range are multipliers (1 = no change). general.pvp_multiplier scales the [PvP] "
			+ "effects against players only, so you can weaken them for PvP without touching survival.", "gray", false)));
		return body;
	}

	private static JsonArray opSettings(TrimPerksConfig c, Perks.Kind kind) {
		JsonArray body = new JsonArray();
		body.add(message(text("Yellow = changed from the default. Click a setting to edit it.", "gray", false)));
		for (Perks.Perk p : Perks.ALL) {
			if (p.kind() != kind) continue;
			JsonArray line = new JsonArray();
			line.add(text("", "white", false));
			line.add(text(p.name(), "gold", true));
			if (c.isDisabled(p)) line.add(text("  (turned off)", "red", false));
			if (p.defaults().isEmpty()) line.add(text("\n  no numbers to change", "dark_gray", false));
			for (String name : p.defaults().keySet()) {
				String key = p.key() + "." + name;
				double value = c.get(key), def = TrimPerksConfig.defaultOf(key);
				JsonObject t = text("\n  " + key + " = " + Perks.format(value) + (value != def ? "  (default " + Perks.format(def) + ")" : ""),
					value != def ? "yellow" : "white", false);
				t.add("click_event", suggest("/trimperks config set " + key + " "));
				line.add(t);
			}
			body.add(message(line));
		}
		return body;
	}

	private static JsonObject suggest(String command) {
		JsonObject click = new JsonObject();
		click.addProperty("action", "suggest_command");
		click.addProperty("command", command);
		return click;
	}

	private static final String BACK_GUIDE = "trimperks guide", BACK_OP = "trimperks config";

	private static JsonObject page(String title, JsonArray body) { return page(title, body, BACK_GUIDE); }

	private static JsonObject page(String title, JsonArray body, String backCommand) {
		JsonObject dialog = new JsonObject();
		dialog.addProperty("type", "minecraft:notice");
		dialog.add("title", text(title, "gold", true));
		dialog.add("body", body);
		JsonObject back = new JsonObject();
		back.add("label", text("Back", "white", false));
		JsonObject run = new JsonObject();
		run.addProperty("type", "minecraft:run_command");
		run.addProperty("command", backCommand);
		back.add("action", run);
		dialog.add("action", back);
		return dialog;
	}

	private static JsonObject button(String label, JsonObject dialog) {
		JsonObject button = new JsonObject();
		button.add("label", text(label, "white", false));
		JsonObject show = new JsonObject();
		show.addProperty("type", "minecraft:show_dialog");
		show.add("dialog", dialog);
		button.add("action", show);
		return button;
	}

	private static JsonObject message(JsonElement contents) {
		JsonObject m = new JsonObject();
		m.addProperty("type", "minecraft:plain_message");
		m.add("contents", contents);
		m.addProperty("width", 400);
		return m;
	}

	private static JsonObject text(String s, String color, boolean bold) {
		JsonObject t = new JsonObject();
		t.addProperty("text", s);
		t.addProperty("color", color);
		t.addProperty("bold", bold); // always set: children inherit the parent style otherwise
		return t;
	}

	private Guide() {}
}
