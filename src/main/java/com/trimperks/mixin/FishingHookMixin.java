package com.trimperks.mixin;

import com.trimperks.Perks;
import com.trimperks.Trims;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coast fishing rod: fish bite sooner (the wait counts down faster). */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow private int timeUntilLured;
	@Shadow public abstract Player getPlayerOwner();

	@Inject(method = "tick", at = @At("HEAD"))
	private void trimperks$lure(CallbackInfo ci) {
		Player owner = getPlayerOwner();
		if (owner == null || timeUntilLured <= 1) return;
		Trims.Active a = Trims.get(owner, Perks.Kind.TOOL, "coast");
		if (a == null) return;
		double lure = Math.min(0.75, a.power("lure", null));
		if (owner.getRandom().nextDouble() < lure / (1 - lure)) timeUntilLured--;
	}
}
