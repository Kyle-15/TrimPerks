package com.trimperks.mixin;

import com.trimperks.Effects;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sentry mining tool: ores drop more XP. */
@Mixin(Block.class)
public abstract class BlockMixin {
	@ModifyVariable(method = "popExperience", at = @At("HEAD"), argsOnly = true)
	private int trimperks$xp(int amount) { return Effects.oreXp(amount); }
}
