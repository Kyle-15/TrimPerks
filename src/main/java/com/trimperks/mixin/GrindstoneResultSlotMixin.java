package com.trimperks.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Taking a trim-removal result: uses up one flint instead of the whole stack, and gives no XP (vanilla would pay
 * out for the enchantments the item keeps).
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin {
	@Unique private static boolean trimperks$sawFlint;

	@Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
	private void trimperks$keepFlint(Container container, int slot, ItemStack stack) {
		ItemStack current = container.getItem(slot);
		if (current.is(Items.FLINT) && current.getCount() > 1) {
			current.shrink(1);
			container.setChanged();
		} else {
			container.setItem(slot, stack);
		}
	}

	@Inject(method = "getExperienceAmount", at = @At("HEAD"))
	private void trimperks$reset(CallbackInfoReturnable<Integer> cir) { trimperks$sawFlint = false; }

	@Redirect(method = "getExperienceAmount", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack trimperks$peek(Container container, int slot) {
		ItemStack stack = container.getItem(slot);
		if (stack.is(Items.FLINT)) trimperks$sawFlint = true;
		return stack;
	}

	@Inject(method = "getExperienceAmount", at = @At("RETURN"), cancellable = true)
	private void trimperks$noXp(CallbackInfoReturnable<Integer> cir) {
		if (trimperks$sawFlint) cir.setReturnValue(0);
	}
}
