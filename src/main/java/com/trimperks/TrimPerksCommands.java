package com.trimperks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /trimperks guide                       anyone: opens the window listing every effect with the current numbers
 * /trimperks config get|set|reset <key>  ops: read or change one number (saved to config/trimperks.json right away)
 * /trimperks config disable|enable <name> ops: turn an effect off or on ("vex", "armor.vex", "material.netherite")
 * /trimperks config reload               ops: re-read the file (plain /reload does this too)
 */
public final class TrimPerksCommands {
	private static final SuggestionProvider<CommandSourceStack> KEYS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(TrimPerksConfig.keys(), builder);
	private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) -> {
		List<String> names = new ArrayList<>(Perks.PATTERNS);
		for (Perks.Perk p : Perks.ALL) if (p.kind() != Perks.Kind.GENERAL) names.add(p.key());
		return SharedSuggestionProvider.suggest(names, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> DISABLED =
		(ctx, builder) -> SharedSuggestionProvider.suggest(TrimPerks.config.disabled, builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("trimperks")
			.then(literal("guide").executes(ctx -> {
				Guide.open(ctx.getSource().getPlayerOrException());
				return 1;
			}))
			.then(literal("bars")
				.then(literal("on").executes(ctx -> {
					Cooldowns.setHidden(ctx.getSource().getPlayerOrException(), false);
					return ok(ctx, "Cooldown bars on");
				}))
				.then(literal("off").executes(ctx -> {
					Cooldowns.setHidden(ctx.getSource().getPlayerOrException(), true);
					return ok(ctx, "Cooldown bars off");
				})))
			.then(literal("config").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					Guide.openOp(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(literal("reload").executes(ctx -> {
					TrimPerks.config = TrimPerksConfig.load();
					return ok(ctx, "Reloaded " + TrimPerksConfig.file().getFileName());
				}))
				.then(literal("get").then(argument("key", StringArgumentType.word()).suggests(KEYS).executes(ctx -> {
					String key = key(ctx);
					if (key == null) return 0;
					return ok(ctx, key + " = " + Perks.format(TrimPerks.config.get(key)) + " (default " + Perks.format(TrimPerksConfig.defaultOf(key)) + ")");
				})))
				.then(literal("set").then(argument("key", StringArgumentType.word()).suggests(KEYS)
					.then(argument("value", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
						String key = key(ctx);
						if (key == null) return 0;
						double value = DoubleArgumentType.getDouble(ctx, "value");
						TrimPerks.config.values.put(key, value);
						TrimPerks.config.save();
						return ok(ctx, "Set " + key + " to " + Perks.format(value));
					}))))
				.then(literal("reset").then(argument("key", StringArgumentType.word()).suggests(KEYS).executes(ctx -> {
					String key = key(ctx);
					if (key == null) return 0;
					TrimPerks.config.values.put(key, TrimPerksConfig.defaultOf(key));
					TrimPerks.config.save();
					return ok(ctx, "Reset " + key + " to " + Perks.format(TrimPerksConfig.defaultOf(key)));
				})))
				.then(literal("disable").then(argument("name", StringArgumentType.word()).suggests(NAMES).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					if (!isName(name)) return fail(ctx, "No effect called " + name);
					TrimPerks.config.disabled.add(name);
					TrimPerks.config.save();
					return ok(ctx, "Turned off " + name);
				})))
				.then(literal("enable").then(argument("name", StringArgumentType.word()).suggests(DISABLED).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					if (!TrimPerks.config.disabled.remove(name)) return fail(ctx, name + " isn't turned off");
					TrimPerks.config.save();
					return ok(ctx, "Turned on " + name);
				})))));
	}

	private static boolean isName(String name) {
		if (Perks.PATTERNS.contains(name) || Perks.MATERIALS.contains(name)) return true;
		for (Perks.Perk p : Perks.ALL) if (p.kind() != Perks.Kind.GENERAL && p.key().equals(name)) return true;
		return false;
	}

	private static String key(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "key");
		if (TrimPerksConfig.isKey(key)) return key;
		fail(ctx, "Unknown setting " + key + " (press Tab to see them)");
		return null;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal("[TrimPerks] " + message), true);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendFailure(Component.literal("[TrimPerks] " + message));
		return 0;
	}

	private TrimPerksCommands() {}
}
