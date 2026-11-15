package com.trimperks.mixin;

import com.trimperks.Trims;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Trimmed item + flint: removes the trim only and keeps the enchantments. */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void trimperks$flint(ItemStack a, ItemStack b, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack item = a.is(Items.FLINT) ? b : b.is(Items.FLINT) ? a : null;
		if (item == null || item.is(Items.FLINT)) return;
		if (Trims.trim(item) == null) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		ItemStack result = item.copyWithCount(1);
		result.remove(DataComponents.TRIM);
		Trims.clearSettling(result);
		Trims.syncModel(result);
		cir.setReturnValue(result);
	}
}
