package com.trimperks.mixin;

import com.trimperks.TrimPerks;
import com.trimperks.Trims;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Material-only retrim (empty template slot), and the settle timer on every trim taken out of the table. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
	@Shadow @Final private DataSlot hasRecipeError;

	protected SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void trimperks$materialSwap(CallbackInfo ci) {
		Trims.syncModel(resultSlots.getItem(0));
		ItemStack template = inputSlots.getItem(0), base = inputSlots.getItem(1), addition = inputSlots.getItem(2);
		if (!template.isEmpty() || base.isEmpty() || addition.isEmpty()) return;
		ArmorTrim old = base.get(DataComponents.TRIM);
		Holder<TrimMaterial> material = com.trimperks.Compat.trimMaterial(addition, player.registryAccess());
		if (old == null || material == null || material.equals(old.material())) return;
		ItemStack result = base.copyWithCount(1);
		result.set(DataComponents.TRIM, new ArmorTrim(material, old.pattern()));
		Trims.syncModel(result);
		resultSlots.setItem(0, result);
		hasRecipeError.set(0);
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void trimperks$onTake(Player player, ItemStack stack, CallbackInfo ci) {
		if (player.level().isClientSide() || Trims.trim(stack) == null) return;
		int levels = (int) TrimPerks.config.get("general.material_swap_levels");
		if (inputSlots.getItem(0).isEmpty() && !player.getAbilities().instabuild) player.giveExperienceLevels(-levels);
		Trims.startSettling(stack, player.level());
		com.trimperks.Compat.overlay(player, Component.literal("Trim settling: works in " + Trims.settleLeft(stack, player.level()) + "s"));
	}
}
