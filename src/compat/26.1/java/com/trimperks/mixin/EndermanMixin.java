package com.trimperks.mixin;

import com.trimperks.Perks;
import com.trimperks.Trims;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Eye armor: endermen don't notice you looking at them. */
@Mixin(EnderMan.class)
public abstract class EndermanMixin {
	@Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
	private void trimperks$eye(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (Trims.get(player, Perks.Kind.ARMOR, "eye") != null) cir.setReturnValue(false);
	}
}
