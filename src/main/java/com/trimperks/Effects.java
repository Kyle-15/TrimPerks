package com.trimperks;

import com.trimperks.Perks.Kind;
import com.trimperks.Trims.Active;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every trim effect. Numbers always come from the config through {@link Active}, so the guide matches. */
public final class Effects {
	/** The block a player is breaking right now (between BEFORE and AFTER), for ore XP. */
	public static ServerPlayer breaker;
	public static BlockState breaking;
	private static boolean sparking;
	private record Magnet(ServerLevel level, AABB area, ServerPlayer player) {}
	private static final List<Magnet> MAGNETS = new ArrayList<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Effects::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Effects::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> afterDamage(entity, source, taken));
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> afterKill(killer, killed));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			breaker = player instanceof ServerPlayer sp ? sp : null;
			breaking = state;
			return true;
		});
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, be) -> breaker = null);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			breaker = null;
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) afterBreak(sl, sp, pos, state);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> useBlock(player, level, hit));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && sp.isShiftKeyDown()) oreCount(sp);
			return InteractionResult.PASS;
		});
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p);
		for (Magnet m : MAGNETS) {
			if (m.player().isRemoved()) continue;
			for (ItemEntity item : m.level().getEntitiesOfClass(ItemEntity.class, m.area(), e -> e.tickCount < 2 && e.getOwner() == null)) {
				item.setPos(m.player().getX(), m.player().getY(), m.player().getZ());
				item.setNoPickUpDelay();
			}
		}
		MAGNETS.clear();
	}

	private static void tickPlayer(ServerPlayer p) {
		Trims.syncModel(p.getMainHandItem()); // covers tools trimmed by commands or before the pack existed
		Cooldowns.tick(p, Trims.now(p.level()));
		Trims.tickAbsorption(p);
		Active armor = Trims.get(p, Kind.ARMOR), weapon = Trims.get(p, Kind.WEAPON), tool = Trims.get(p, Kind.TOOL);
		Trims.State st = Trims.state(p);
		long now = Trims.now(p.level());
		String a = armor == null ? "" : armor.pattern();
		// PvP stat boosts (reach, attack speed, dash) can't be aimed at one target, so they drop to the PvP strength
		// while another player is close enough to fight
		double pvp = pvpNearby(p) ? TrimPerks.config.get("general.pvp_multiplier") : 1;

		// Armor: attributes
		mod(p, Attributes.WATER_MOVEMENT_EFFICIENCY, "coast", a.equals("coast") && !Trims.armorHasEnchant(p, Enchantments.DEPTH_STRIDER)
			? armor.power("swim", null) : 0, AttributeModifier.Operation.ADD_VALUE);
		mod(p, Attributes.OXYGEN_BONUS, "tide", a.equals("tide") && !Trims.armorHasEnchant(p, Enchantments.RESPIRATION)
			? armor.power("air", null) : 0, AttributeModifier.Operation.ADD_VALUE);
		mod(p, Attributes.BLOCK_INTERACTION_RANGE, "shaper_armor", a.equals("shaper") ? armor.power("reach", null) : 0, AttributeModifier.Operation.ADD_VALUE);
		mod(p, Attributes.JUMP_STRENGTH, "raiser", a.equals("raiser") && p.isSprinting() ? 0.1 * armor.power("level", null) : 0, AttributeModifier.Operation.ADD_VALUE);
		double speed = a.equals("dune") && p.getBlockStateOn().is(BlockTags.SAND) ? 0.2 * armor.power("speed_level", null) : 0;
		speed -= TrimPerks.config.get("material.netherite.armor_slow") * (armor == null ? 0 : armor.netherite());
		mod(p, Attributes.MOVEMENT_SPEED, "speed", speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

		// Armor: timed and input effects
		boolean sneaking = p.getLastClientInput().shift(), jumping = p.getLastClientInput().jump();
		// Eye: a double-tap of sneak (two presses within 8 ticks), so normal crouching never sets it off
		if (sneaking && !st.wasSneaking) {
			if (a.equals("eye") && now - st.lastSneakPress <= 8 && Trims.trigger(p, armor, "cooldown")) {
				for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(armor.reach("range"))))
					mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, armor.ticks("duration"), 0, false, false));
				st.lastSneakPress = Long.MIN_VALUE / 2;
			} else {
				st.lastSneakPress = now;
			}
		}
		// Flow: a real double jump. The jump that leaves the ground arrives while you're already airborne, so a press
		// only counts after jump was let go in the air. Never with an elytra (that press opens it) or where you can
		// fly (creative double-tap).
		if (p.onGround() || p.isInWater() || p.onClimbable() || p.isPassenger()) st.flowArmed = false;
		else if (!jumping) st.flowArmed = true;
		if (a.equals("flow") && jumping && !st.wasJumping && st.flowArmed && !p.isFallFlying() && !p.isInLava()
				&& !p.getAbilities().mayfly && !p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).has(net.minecraft.core.component.DataComponents.GLIDER)
				&& Trims.trigger(p, armor, "cooldown")) {
			st.flowArmed = false;
			Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.9 * armor.strength() * pvp);
			p.setDeltaMovement(p.getDeltaMovement().add(look.x, 0.35, look.z));
			launch(p);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7f, 1.2f);
		}
		st.wasSneaking = sneaking;
		st.wasJumping = jumping;
		if (a.equals("rib") && p.getRemainingFireTicks() > 0 && !Trims.armorHasEnchant(p, Enchantments.FIRE_PROTECTION)) {
			double r = Math.min(0.75, armor.power("reduction", null)), extra = r / (1 - r);
			int whole = (int) extra + (p.getRandom().nextDouble() < extra - (int) extra ? 1 : 0);
			p.setRemainingFireTicks(Math.max(0, p.getRemainingFireTicks() - whole));
		}
		if (now % 40 == 0 && a.equals("host")) {
			for (TamableAnimal pet : pets(p, armor.reach("range"))) pet.heal((float) armor.strength());
		}
		if (now % 20 == 0 && a.equals("wayfinder") && sneaking) showDeath(p);

		// Held weapon: attack speed and reach
		double attack = 0;
		if (weapon != null && weapon.pattern().equals("vex")) attack += weapon.power("speed", null) * pvp;
		if (weapon != null && weapon.pattern().equals("flow") && now < st.timers.getOrDefault("flow", 0L)) attack += weapon.power("speed", null) * pvp;
		boolean netheriteHeld = (weapon != null && weapon.netherite() > 0) || (tool != null && tool.netherite() > 0);
		if (netheriteHeld) attack -= TrimPerks.config.get("material.netherite.tool_slow");
		mod(p, Attributes.ATTACK_SPEED, "attack", attack, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		mod(p, Attributes.ENTITY_INTERACTION_RANGE, "shaper_weapon", weapon != null && weapon.pattern().equals("shaper")
			? weapon.power("reach", null) * pvp : 0, AttributeModifier.Operation.ADD_VALUE);

		// Held mining tool: mining speed (attributes, so the player's own client mines faster too)
		mod(p, Attributes.BLOCK_INTERACTION_RANGE, "shaper_tool", tool != null && tool.pattern().equals("shaper")
			? tool.power("reach", null) : 0, AttributeModifier.Operation.ADD_VALUE);
		mod(p, Attributes.SUBMERGED_MINING_SPEED, "tide_tool", tool != null && tool.pattern().equals("tide") ? 0.8 : 0, AttributeModifier.Operation.ADD_VALUE);
		double mine = 1;
		if (tool != null) {
			BlockState target = null;
			HitResult hit = p.pick(p.blockInteractionRange(), 0, false);
			if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) target = p.level().getBlockState(bh.getBlockPos());
			switch (tool.pattern()) {
				case "dune" -> { if (target != null && (target.is(BlockTags.SAND) || target.is(Blocks.GRAVEL) || target.is(Blocks.SUSPICIOUS_GRAVEL))) mine *= 1 + tool.power("speed", null); }
				case "rib" -> { if (target != null && (target.is(BlockTags.BASE_STONE_NETHER) || target.is(BlockTags.NYLIUM) || target.is(Blocks.NETHER_QUARTZ_ORE) || target.is(Blocks.NETHER_GOLD_ORE))) mine *= 1 + tool.power("speed", null); }
				case "vex" -> { if (p.level().getMaxLocalRawBrightness(p.blockPosition()) <= tool.raw("light")) mine *= 1 + tool.power("speed", null); }
				case "spire" -> { if (!p.onGround()) mine *= 5; }
				case "flow" -> {
					if (now - st.timers.getOrDefault("chain", 0L) < 60)
						mine *= 1 + Math.min(tool.raw("max"), tool.raw("per_block") * st.counter) * tool.strength();
				}
				default -> {}
			}
		}
		if (netheriteHeld) mine *= 1 - TrimPerks.config.get("material.netherite.tool_slow");
		ItemStack held = p.getMainHandItem();
		if (tool != null && tool.pattern().equals("ward") && held.isDamageableItem() && held.getDamageValue() >= held.getMaxDamage() - 1) {
			mine = 0; // "broken": it's saved from breaking, but has to be repaired before it mines again
			if (now % 40 == 0) Compat.overlay(p, Component.literal("This tool needs repairing"));
		}
		mod(p, Attributes.BLOCK_BREAK_SPEED, "mining", mine - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void showDeath(ServerPlayer p) {
		GlobalPos death = p.getLastDeathLocation().orElse(null);
		if (death == null || death.dimension() != p.level().dimension()) {
			Compat.overlay(p, Component.literal("No death point in this dimension"));
			return;
		}
		Vec3 to = Vec3.atCenterOf(death.pos()).subtract(p.position());
		double angle = Math.toDegrees(Math.atan2(-to.x, to.z)) - p.getYRot();
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		int i = (int) Math.floorMod(Math.round(angle / 45), 8);
		Compat.overlay(p, Component.literal("Last death: " + (int) to.horizontalDistance() + " blocks " + arrows[i]));
	}

	// ---------------------------------------------------------------- damage

	/** Cancels damage: arrow catching, dodging, cactus/berries, safe falls, sand suffocation. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer p)) return true;
		Active armor = Trims.get(p, Kind.ARMOR);
		if (armor == null) return true;
		Entity attacker = source.getEntity();
		switch (armor.pattern()) {
			case "sentry" -> {
				Boolean pickup = source.getDirectEntity() == null ? null : Compat.arrowPickup(source.getDirectEntity());
				if (pickup != null && p.getRandom().nextDouble() < armor.power("chance", attacker)) {
					boolean keep = pickup;
					source.getDirectEntity().discard();
					if (keep && !p.getInventory().add(new ItemStack(Items.ARROW))) p.spawnAtLocation((ServerLevel) p.level(), new ItemStack(Items.ARROW));
					p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);
					return false;
				}
			}
			case "vex" -> {
				if (attacker instanceof LivingEntity && source.getDirectEntity() == attacker
						&& p.getRandom().nextDouble() < armor.power("chance", attacker) && Trims.trigger(p, armor, "cooldown")) {
					p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1f, 1.4f);
					return false;
				}
			}
			case "wild" -> { if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) return false; }
			case "spire" -> {
				if (source.is(DamageTypeTags.IS_FALL) && amount >= 2 && !Trims.armorHasEnchant(p, Enchantments.FEATHER_FALLING)
						&& Math.max(p.fallDistance, amount + 3) <= armor.power("max_height", null) && Trims.trigger(p, armor, "cooldown")) return false;
			}
			case "dune" -> {
				if (source.is(DamageTypes.IN_WALL) && p.level().getBlockState(BlockPos.containing(p.getEyePosition())).getBlock() instanceof FallingBlock) return false;
			}
			default -> {}
		}
		return true;
	}

	/** Weapon damage bonuses (called from LivingEntityMixin before damage is applied). */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer p) {
			Active w = Trims.get(p, Kind.WEAPON);
			if (w == null) return amount;
			double bonus = switch (w.pattern()) {
				case "sentry" -> victim.getType().builtInRegistryHolder().is(EntityTypeTags.ILLAGER) ? w.power("damage", victim) : 0;
				case "coast" -> victim.getType().builtInRegistryHolder().is(EntityTypeTags.AQUATIC) && !Trims.hasEnchant(p.getMainHandItem(), Enchantments.IMPALING) ? w.power("damage", victim) : 0;
				case "spire" -> p.fallDistance > 0 && !p.onGround() && !p.getMainHandItem().is(Items.MACE) ? w.power("damage", victim) : 0;
				case "silence" -> victim instanceof Mob mob && mob.getTarget() != p && behind(victim, p) ? w.power("damage", victim) : 0;
				default -> 0;
			};
			return (float) (amount * (1 + bonus));
		}
		if (attacker instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner && owner.getLastHurtMob() == victim) {
			Active w = Trims.get(owner, Kind.WEAPON, "host");
			if (w != null) return (float) (amount * (1 + w.power("damage", victim)));
		}
		return amount;
	}

	/**
	 * After changing an entity's velocity. Mobs sync on their own; a player's movement is run by their own client, so
	 * the new velocity has to be sent to them or the push never happens (the sound plays, nothing moves).
	 */
	private static void launch(Entity e) {
		Compat.markMoved(e);
		if (e instanceof ServerPlayer sp) sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
	}

	private static boolean behind(LivingEntity victim, Entity attacker) {
		Vec3 toAttacker = attacker.position().subtract(victim.position()).normalize();
		return victim.getViewVector(1).dot(toAttacker) < -0.3;
	}

	private static boolean isCrit(Player p) {
		return p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger() && !p.isSprinting();
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0) return;
		// Armor reacting to being hit
		if (victim instanceof ServerPlayer p) {
			Active armor = Trims.get(p, Kind.ARMOR);
			if (armor != null && armor.pattern().equals("ward") && p.getHealth() < armor.raw("below") * 2 && Trims.trigger(p, armor, "cooldown"))
				Trims.absorb(p, armor, armor.power("absorption", source.getEntity()), armor.ticks("duration"));
			if (armor != null && armor.pattern().equals("bolt") && source.getEntity() instanceof LivingEntity attacker
					&& source.getDirectEntity() == attacker && attacker.distanceTo(p) < 5 && Trims.trigger(p, armor, "cooldown")) {
				Vec3 away = attacker.position().subtract(p.position()).multiply(1, 0, 1).normalize()
					.scale(1.2 * armor.strength() * (attacker instanceof Player ? TrimPerks.config.get("general.pvp_multiplier") : 1));
				attacker.push(away.x, 0.35, away.z);
				launch(attacker);
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.4f, 1.8f);
			}
		}
		// Weapon hitting something
		if (!(source.getEntity() instanceof ServerPlayer p) || source.getDirectEntity() != p || sparking) return;
		Active w = Trims.get(p, Kind.WEAPON);
		if (w == null) return;
		boolean crit = isCrit(p);
		switch (w.pattern()) {
			case "dune" -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
				(int) (w.ticks("duration") * (victim instanceof Player ? TrimPerks.config.get("general.pvp_multiplier") : 1)), 0), p);
			case "wild" -> { if (crit) victim.addEffect(new MobEffectInstance(MobEffects.POISON, w.ticks("duration"), 0), p); }
			case "eye" -> { if (crit) victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, w.ticks("duration"), 0), p); }
			case "rib" -> { if (crit && !Trims.hasEnchant(p.getMainHandItem(), Enchantments.FIRE_ASPECT)) victim.igniteForTicks(w.ticks("duration")); }
			case "tide" -> {
				if (p.isInWater() || victim.isInWater()) {
					Vec3 push = p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.5 * w.power("knockback", victim));
					victim.push(push.x, 0.1, push.z);
					launch(victim);
				}
			}
			case "raiser" -> {
				if (victim.getBbWidth() < 1 && !(victim instanceof Player)) {
					victim.push(0, 0.45 * w.strength(), 0);
					launch(victim);
				}
			}
			case "flow" -> { if (p.isSprinting()) Trims.state(p).timers.put("flow", Trims.now(p.level()) + w.ticks("duration")); }
			case "bolt" -> {
				Trims.State st = Trims.state(p);
				if (++st.hits % Math.max(1, (int) w.raw("hits")) != 0) return;
				LivingEntity next = null;
				for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(4)))
					if (e != victim && e instanceof net.minecraft.world.entity.monster.Enemy && (next == null || e.distanceTo(victim) < next.distanceTo(victim))) next = e;
				if (next != null && p.level() instanceof ServerLevel level) {
					sparking = true;
					next.hurtServer(level, level.damageSources().playerAttack(p), (float) w.power("damage", next));
					sparking = false;
					level.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 2f);
				}
			}
			default -> {}
		}
	}

	private static void afterKill(Entity killer, LivingEntity killed) {
		if (!(killer instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
		Active w = Trims.get(p, Kind.WEAPON), t = Trims.get(p, Kind.TOOL);
		// Ward and Host only reward real fights: hostile mobs and players, not farm animals
		boolean fight = killed instanceof net.minecraft.world.entity.monster.Enemy || killed instanceof Player;
		if (fight && w != null && w.pattern().equals("ward") && Trims.trigger(p, w, "cooldown")) Trims.absorb(p, w, w.power("absorption", killed), w.ticks("duration"));
		if (w != null && w.pattern().equals("wayfinder")) MAGNETS.add(new Magnet(level, killed.getBoundingBox().inflate(0.5), p));
		if (fight && t != null && t.pattern().equals("host")) for (TamableAnimal pet : pets(p, 16)) pet.heal((float) (t.raw("hearts") * 2 * t.strength()));
	}

	// ---------------------------------------------------------------- blocks and items

	private static void afterBreak(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
		Active t = Trims.get(p, Kind.TOOL);
		if (t == null) return;
		Trims.State st = Trims.state(p);
		long now = Trims.now(level);
		switch (t.pattern()) {
			case "wayfinder" -> MAGNETS.add(new Magnet(level, new AABB(pos).inflate(0.25), p));
			case "flow" -> {
				if (felling) return;
				st.counter = now - st.timers.getOrDefault("chain", 0L) < 60 ? st.counter + 1 : 1;
				st.timers.put("chain", now);
			}
			case "snout" -> {
				if ((state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.GILDED_BLACKSTONE)) && !Trims.hasEnchant(p.getMainHandItem(), Enchantments.SILK_TOUCH)) {
					double extra = 4 * t.power("bonus", null);
					int n = (int) extra + (p.getRandom().nextDouble() < extra - (int) extra ? 1 : 0);
					if (n > 0) net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(Items.GOLD_NUGGET, n));
				}
			}
			case "bolt" -> { if (!felling && state.is(BlockTags.LOGS) && p.getMainHandItem().is(ItemTags.AXES) && !p.isShiftKeyDown()) fellTree(level, p, pos, t); }
			default -> {}
		}
	}

	private static boolean felling;

	/**
	 * Bolt axe. Breaks the connected logs through the player's own block breaking, so claim and protection mods,
	 * spawn protection, tool damage, Fortune and stats all work as normal. Only fells natural trees (logs touching
	 * natural leaves), so a log cabin doesn't come down.
	 */
	private static void fellTree(ServerLevel level, ServerPlayer p, BlockPos start, Active t) {
		int max = (int) t.raw("max_logs");
		Set<BlockPos> seen = new HashSet<>(List.of(start));
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
		List<BlockPos> logs = new ArrayList<>();
		boolean natural = false;
		while (!queue.isEmpty() && logs.size() < max) {
			BlockPos at = queue.poll();
			for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				BlockState s = level.getBlockState(next);
				if (!natural && s.is(BlockTags.LEAVES) && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT)
						&& !s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT)) natural = true;
				if (logs.size() >= max || next.getY() < start.getY() || !s.is(BlockTags.LOGS) || !seen.add(next.immutable())) continue;
				logs.add(next.immutable());
				queue.add(next.immutable());
			}
		}
		if (!natural) return;
		felling = true;
		try {
			for (BlockPos log : logs) {
				ItemStack axe = p.getMainHandItem();
				if (axe.isEmpty() || !axe.is(ItemTags.AXES) || axe.getMaxDamage() - axe.getDamageValue() <= 1) break;
				p.gameMode.destroyBlock(log);
			}
		} finally {
			felling = false;
		}
	}

	private static InteractionResult useBlock(Player player, Level level, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl) || !p.getMainHandItem().is(ItemTags.HOES)) return InteractionResult.PASS;
		Active t = Trims.get(p, Kind.TOOL);
		if (t == null) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!p.mayBuild() || !level.mayInteract(p, pos)) return InteractionResult.PASS;
		if (t.pattern().equals("wild") && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			// through normal block breaking, so protection mods can say no
			if (p.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir()) level.setBlockAndUpdate(pos, crop.getStateForAge(0));
			return InteractionResult.SUCCESS;
		}
		if (t.pattern().equals("raiser") && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
				&& Compat.canBonemeal(crop, sl, pos, state) && Trims.trigger(p, t, "cooldown")) {
			Compat.bonemeal(crop, sl, pos, state);
			sl.levelEvent(1505, pos, 15);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static void oreCount(ServerPlayer p) {
		Active t = Trims.get(p, Kind.TOOL, "eye");
		if (t == null || !p.getMainHandItem().is(ItemTags.PICKAXES)) return;
		if (!Trims.ready(p, t, "cooldown")) {
			Compat.overlay(p, Component.literal("Ore sense is cooling down"));
			return;
		}
		if (!Trims.trigger(p, t, "cooldown")) return;
		int r = (int) Math.round(t.reach("range")), count = 0;
		for (BlockPos at : BlockPos.betweenClosed(p.blockPosition().offset(-r, -r, -r), p.blockPosition().offset(r, r, r)))
			if (p.level().getBlockState(at).is(ConventionalBlockTags.ORES)) count++;
		Compat.overlay(p, Component.literal(count + " ores within " + r + " blocks"));
	}

	// ---------------------------------------------------------------- helpers

	private static List<TamableAnimal> pets(ServerPlayer p, double range) {
		return p.level().getEntitiesOfClass(TamableAnimal.class, p.getBoundingBox().inflate(range), pet -> pet.isOwnedBy(p));
	}

	private static void mod(ServerPlayer p, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = p.getAttribute(attribute);
		if (inst == null) return;
		Identifier id = Identifier.fromNamespaceAndPath(TrimPerks.MOD_ID, name);
		AttributeModifier current = inst.getModifier(id);
		if (Math.abs(amount) < 1e-6) {
			if (current != null) inst.removeModifier(id);
		} else if (current == null || Math.abs(current.amount() - amount) > 1e-6) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Ore XP bonus (BlockMixin). */
	public static int oreXp(int amount) {
		if (breaker == null || breaking == null || !breaking.is(ConventionalBlockTags.ORES)) return amount;
		Active t = Trims.get(breaker, Kind.TOOL, "sentry");
		if (t == null) return amount;
		double v = amount * (1 + t.power("xp", null));
		return (int) v + (breaker.getRandom().nextDouble() < v - (int) v ? 1 : 0);
	}

	/** Another player (not a spectator) within 8 blocks: a fight could be on. */
	private static boolean pvpNearby(ServerPlayer p) {
		for (Player other : p.level().players())
			if (other != p && !other.isSpectator() && other.distanceToSqr(p) < 64) return true;
		return false;
	}

	private Effects() {}
}
