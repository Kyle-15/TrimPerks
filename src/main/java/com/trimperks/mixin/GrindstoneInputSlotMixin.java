package com.trimperks.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets flint go into the grindstone's input slots. */
@Mixin(targets = {"net.minecraft.world.inventory.GrindstoneMenu$2", "net.minecraft.world.inventory.GrindstoneMenu$3"})
public abstract class GrindstoneInputSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void trimperks$flint(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(Items.FLINT)) cir.setReturnValue(true);
	}
}
