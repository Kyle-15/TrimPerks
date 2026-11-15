package com.trimperks.mixin;

import com.trimperks.Perks;
import com.trimperks.Trims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ward mining tool: stops at 1 durability instead of breaking. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "processDurabilityChange", at = @At("RETURN"), cancellable = true)
	private void trimperks$ward(int amount, ServerLevel level, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (player == null || player.getMainHandItem() != self || Trims.get(player, Perks.Kind.TOOL, "ward") == null) return;
		int room = self.getMaxDamage() - 1 - self.getDamageValue();
		if (cir.getReturnValue() > room) cir.setReturnValue(Math.max(0, room));
	}
}
