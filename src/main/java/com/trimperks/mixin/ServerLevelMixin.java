package com.trimperks.mixin;

import com.trimperks.Perks;
import com.trimperks.Trims;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Silence: sculk can't hear your footsteps (armor) or your mining (tool). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "gameEvent", at = @At("HEAD"), cancellable = true)
	private void trimperks$silence(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		if (!(context.sourceEntity() instanceof ServerPlayer p)) return;
		if ((event.is(GameEvent.STEP.key()) || event.is(GameEvent.HIT_GROUND.key())) && Trims.get(p, Perks.Kind.ARMOR, "silence") != null) ci.cancel();
		else if (event.is(GameEvent.BLOCK_DESTROY.key()) && Trims.get(p, Perks.Kind.TOOL, "silence") != null) ci.cancel();
	}
}
