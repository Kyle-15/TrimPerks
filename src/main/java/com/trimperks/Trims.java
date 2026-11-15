package com.trimperks;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads trims off a player's gear and turns them into an {@link Active} effect with the material math applied. */
public final class Trims {
	public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TrimPerks.MOD_ID, "weapons"));
	public static final TagKey<Item> MINING_TOOLS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TrimPerks.MOD_ID, "mining_tools"));
	private static final String SETTLE = "trimperks_settle";
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** One working effect: the pattern plus the blended material multipliers. */
	public record Active(String pattern, Perks.Kind kind, double strength, double duration, double cooldown, double range,
			double xpCost, double skipChance, double allyShare, double allyRange, boolean chime, double linger, int netherite) {
		public String key(String name) { return kind.name().toLowerCase(java.util.Locale.ROOT) + "." + pattern + "." + name; }
		public double raw(String name) { return TrimPerks.config.get(key(name)); }
		/** A strength-type number (chance, damage, speed...), scaled by the material and, against players, the PvP multiplier. */
		public double power(String name, Object target) {
			double v = raw(name) * strength;
			if (target instanceof Player && Perks.PVP.contains(pattern)) v *= TrimPerks.config.get("general.pvp_multiplier");
			return v;
		}
		public int ticks(String name) { return (int) Math.round(raw(name) * duration * 20); }
		public int cooldownTicks(String name) { return (int) Math.round(raw(name) * cooldown * 20); }
		public double reach(String name) { return raw(name) * range; }
	}

	public static String pathOf(Holder<?> holder) {
		return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	public static final TagKey<Item> TRIMMABLE_TOOLS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TrimPerks.MOD_ID, "trimmable_tools"));

	/**
	 * Tools have no trim textures in vanilla, so the TrimPerks resource pack picks the overlay from custom model data
	 * ("trimperks:<pattern>"). Keeps it in step with the trim; never touches custom model data another mod set.
	 */
	public static void syncModel(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(TRIMMABLE_TOOLS)) return;
		net.minecraft.world.item.component.CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		boolean ours = cmd != null && !cmd.strings().isEmpty() && cmd.strings().getFirst().startsWith("trimperks:");
		if (cmd != null && !ours) return;
		ArmorTrim trim = trim(stack);
		String want = trim == null ? null : "trimperks:" + pathOf(trim.pattern());
		if (want == null) {
			if (ours) stack.remove(DataComponents.CUSTOM_MODEL_DATA);
		} else if (!ours || !cmd.strings().getFirst().equals(want)) {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(), List.of(want), List.of()));
		}
	}

	public static ArmorTrim trim(ItemStack stack) { return stack.isEmpty() ? null : stack.get(DataComponents.TRIM); }

	public static long now(Level level) { return level.getServer() != null ? level.getServer().overworld().getGameTime() : level.getGameTime(); }

	// ---- settle timer: stored on the item, so it survives logouts and trading ----

	public static void startSettling(ItemStack stack, Level level) {
		long until = now(level) + (long) (TrimPerks.config.get("general.settle_seconds") * 20);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(SETTLE, until));
	}

	public static void clearSettling(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return;
		CompoundTag tag = data.copyTag();
		tag.remove(SETTLE);
		if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
		else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** Seconds left before this item's trim works, 0 if it's settled. */
	public static long settleLeft(ItemStack stack, Level level) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return 0;
		long until = data.copyTag().getLongOr(SETTLE, 0L);
		return Math.max(0, (until - now(level) + 19) / 20);
	}

	// ---- reading gear ----

	private static Active build(String pattern, Perks.Kind kind, List<String> materials, Level level) {
		Perks.Perk perk = Perks.find(kind, pattern);
		boolean nether = level.dimension() == Level.NETHER;
		double n = materials.size(), s = 0, d = 0, c = 0, r = 0, xp = 0, skip = 0, ally = 0, allyRange = 0, linger = 0;
		boolean chime = false;
		int netherite = 0;
		TrimPerksConfig cfg = TrimPerks.config;
		for (String m : materials) {
			String k = "material." + m + ".";
			// unknown (modded) or turned-off materials, and materials whose trade-off means nothing here, work like iron
			if (!TrimPerksConfig.isKey(k + "strength") || cfg.disabled.contains(m) || cfg.disabled.contains("material." + m)
					|| perk == null || !perk.materialApplies(m)) {
				s += 1; d += 1; c += 1; r += 1;
				continue;
			}
			double str = cfg.get(k + "strength");
			if (m.equals("quartz")) str = cfg.get(k + (nether ? "nether" : "elsewhere"));
			s += str; d += cfg.get(k + "duration"); c += cfg.get(k + "cooldown"); r += cfg.get(k + "range");
			switch (m) {
				case "lapis" -> xp += cfg.get(k + "xp_cost");
				case "emerald" -> skip += cfg.get(k + "skip_chance");
				case "gold" -> { ally += cfg.get(k + "ally_share"); allyRange = cfg.get(k + "ally_range"); }
				case "amethyst" -> chime = true;
				case "resin" -> linger = Math.max(linger, cfg.get(k + "linger"));
				case "netherite" -> netherite++;
				default -> {}
			}
		}
		return new Active(pattern, kind, s / n, d / n, c / n, r / n, xp / n, skip / n, ally / n, allyRange, chime, linger, netherite);
	}

	private static boolean enabled(Perks.Kind kind, String pattern) {
		TrimPerksConfig cfg = TrimPerks.config;
		return !cfg.disabled.contains(pattern) && !cfg.disabled.contains(kind.name().toLowerCase(java.util.Locale.ROOT) + "." + pattern);
	}

	private static Active readArmor(Player player) {
		String pattern = null;
		String[] materials = new String[4];
		for (int i = 0; i < 4; i++) {
			ItemStack stack = player.getItemBySlot(ARMOR[i]);
			ArmorTrim trim = trim(stack);
			if (trim == null || settleLeft(stack, player.level()) > 0) return null;
			String p = pathOf(trim.pattern());
			if (pattern != null && !pattern.equals(p)) return null;
			pattern = p;
			materials[i] = pathOf(trim.material());
		}
		if (!Perks.PATTERNS.contains(pattern) || !enabled(Perks.Kind.ARMOR, pattern)) return null;
		return build(pattern, Perks.Kind.ARMOR, List.of(materials), player.level());
	}

	private static Active readHeld(Player player, Perks.Kind kind) {
		ItemStack stack = player.getMainHandItem();
		ArmorTrim trim = trim(stack);
		if (trim == null || !stack.is(kind == Perks.Kind.WEAPON ? WEAPONS : MINING_TOOLS) || settleLeft(stack, player.level()) > 0) return null;
		String pattern = pathOf(trim.pattern());
		if (!Perks.PATTERNS.contains(pattern) || !enabled(kind, pattern)) return null;
		return build(pattern, kind, List.of(pathOf(trim.material())), player.level());
	}

	// ---- per-player state: cooldowns, resin lingering, counters ----

	public static final class State {
		final Map<String, Long> readyAt = new HashMap<>();
		final Map<Perks.Kind, Active> last = new HashMap<>();
		final Map<Perks.Kind, Long> lastUntil = new HashMap<>();
		public final Map<String, Long> timers = new HashMap<>();
		public int counter, hits;
		public boolean wasJumping, wasSneaking, flowArmed;
		public long lastSneakPress = Long.MIN_VALUE / 2;
		final Map<String, Cooldowns.Bar> bars = new java.util.LinkedHashMap<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	public static State state(Player player) { return STATES.computeIfAbsent(player.getUUID(), u -> new State()); }

	public static void forget(Player player) { STATES.remove(player.getUUID()); }

	/** The working effect of this kind, or null. Resin keeps a removed trim working for a few seconds. */
	public static Active get(Player player, Perks.Kind kind) {
		if (!(player instanceof ServerPlayer) || player.isSpectator()) return null;
		Active a = kind == Perks.Kind.ARMOR ? readArmor(player) : readHeld(player, kind);
		State st = state(player);
		long now = now(player.level());
		if (a != null) {
			st.last.put(kind, a);
			st.lastUntil.put(kind, now + (long) (a.linger() * 20));
			return a;
		}
		Active prev = st.last.get(kind);
		return prev != null && prev.linger() > 0 && now < st.lastUntil.getOrDefault(kind, 0L) ? prev : null;
	}

	public static Active get(Player player, Perks.Kind kind, String pattern) {
		Active a = get(player, kind);
		return a != null && a.pattern().equals(pattern) ? a : null;
	}

	public static boolean ready(Player player, Active a, String timer) {
		return now(player.level()) >= state(player).readyAt.getOrDefault(a.kind() + timer, 0L);
	}

	/**
	 * Use a cooldown effect: false if it's still cooling down or a lapis trim can't pay its XP. Otherwise pays,
	 * starts the cooldown (emerald may skip it) and plays the amethyst chime.
	 */
	public static boolean trigger(Player player, Active a, String cooldownName) {
		if (!ready(player, a, cooldownName)) return false;
		int xp = (int) Math.round(a.xpCost());
		if (xp > 0 && !player.getAbilities().instabuild) {
			if (player.totalExperience < xp) return false;
			player.giveExperiencePoints(-xp);
		}
		if (player.getRandom().nextDouble() >= a.skipChance()) {
			long ticks = a.cooldownTicks(cooldownName);
			state(player).readyAt.put(a.kind() + cooldownName, now(player.level()) + ticks);
			Cooldowns.start(player, a, ticks);
		}
		if (a.chime()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1f);
			player.gameEvent(GameEvent.RESONATE_15);
		}
		return true;
	}

	private static final Identifier ABSORB = Identifier.fromNamespaceAndPath(TrimPerks.MOD_ID, "ward_absorption");

	/**
	 * Ward's Absorption hearts (any amount, so material strength matters). Gold also gives allies (same team, or
	 * everyone if you're on no team, never someone you're fighting) the hearts for part of the time.
	 */
	public static void absorb(ServerPlayer player, Active a, double hearts, int ticks) {
		giveAbsorption(player, hearts * 2, ticks);
		if (a.allyShare() <= 0 || !(player.level() instanceof ServerLevel level)) return;
		for (ServerPlayer other : level.players()) {
			if (other == player || other.isSpectator() || other.distanceTo(player) > a.allyRange()) continue;
			if (player.getTeam() != null && player.getTeam() != other.getTeam()) continue;
			if (player.getLastHurtByMob() == other || player.getLastHurtMob() == other || other.getLastHurtMob() == player) continue;
			giveAbsorption(other, hearts * 2, (int) (ticks * a.allyShare()));
		}
	}

	private static void giveAbsorption(ServerPlayer p, double hp, int ticks) {
		var max = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
		if (max == null) return;
		var current = max.getModifier(ABSORB);
		double before = current == null ? 0 : current.amount(), amount = Math.max(before, hp);
		max.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ABSORB, amount,
			net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		p.setAbsorptionAmount(p.getAbsorptionAmount() + (float) (amount - before));
		State st = state(p);
		st.timers.put("absorb", Math.max(st.timers.getOrDefault("absorb", 0L), now(p.level()) + ticks));
	}

	/** Every tick: take Ward's Absorption away again when it runs out. */
	public static void tickAbsorption(ServerPlayer p) {
		var max = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);
		if (max == null || max.getModifier(ABSORB) == null || now(p.level()) < state(p).timers.getOrDefault("absorb", 0L)) return;
		max.removeModifier(ABSORB);
		p.setAbsorptionAmount(Math.min(p.getAbsorptionAmount(), (float) max.getValue()));
	}

	public static boolean hasEnchant(ItemStack stack, ResourceKey<Enchantment> key) {
		for (Holder<Enchantment> h : stack.getEnchantments().keySet()) if (h.is(key)) return true;
		return false;
	}

	public static boolean armorHasEnchant(Player player, ResourceKey<Enchantment> key) {
		for (EquipmentSlot slot : ARMOR) if (hasEnchant(player.getItemBySlot(slot), key)) return true;
		return false;
	}

	private Trims() {}
}
