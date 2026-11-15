package com.trimperks.mixin;

import com.trimperks.Perks;
import com.trimperks.Trims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
	/** Snout armor counts as wearing gold. */
	@Inject(method = "isWearingSafeArmor", at = @At("HEAD"), cancellable = true)
	private static void trimperks$snout(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player p && Trims.get(p, Perks.Kind.ARMOR, "snout") != null) cir.setReturnValue(true);
	}

	/** Snout weapon: a piglin you hit doesn't call the others. */
	@Inject(method = "broadcastAngerTarget", at = @At("HEAD"), cancellable = true)
	private static void trimperks$quiet(ServerLevel level, AbstractPiglin piglin, LivingEntity target, CallbackInfo ci) {
		if (target instanceof Player p && Trims.get(p, Perks.Kind.WEAPON, "snout") != null) ci.cancel();
	}
}
