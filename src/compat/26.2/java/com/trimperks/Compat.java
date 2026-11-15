package com.trimperks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Calls whose names or arguments differ between Minecraft versions (one copy per src/compat/<version>; this is 26.2). */
public final class Compat {
	public static boolean canBonemeal(CropBlock crop, ServerLevel level, BlockPos pos, BlockState state) {
		return crop.isValidBonemealTarget(level, pos, state);
	}

	public static void bonemeal(CropBlock crop, ServerLevel level, BlockPos pos, BlockState state) {
		crop.performBonemeal(level, level.getRandom(), pos, state);
	}

	/** A short message above the hotbar. */
	public static void overlay(Player p, Component message) {
		p.sendOverlayMessage(message);
	}

	/** After changing an entity's velocity on the server. */
	public static void markMoved(Entity e) {
		e.needsSync = true;
	}

	public static ServerBossEvent bossEvent(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		return new ServerBossEvent(java.util.UUID.randomUUID(), name, color, overlay);
	}

	public static String path(ResourceKey<?> key) {
		return key.identifier().getPath();
	}

	/** The trim material an item provides in the smithing table, or null. */
	public static Holder<TrimMaterial> trimMaterial(ItemStack stack, HolderLookup.Provider registries) {
		return stack.get(DataComponents.PROVIDES_TRIM_MATERIAL);
	}

	/** null if the entity isn't an arrow; otherwise whether it could be picked up. */
	public static Boolean arrowPickup(Entity e) {
		return e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow ? arrow.pickup == net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED : null;
	}

	private Compat() {}
}
