package com.trimperks.mixin;

import com.trimperks.TrimPerks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A material-only retrim can't be taken without enough XP levels. */
@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {
	@Shadow @Final protected Container inputSlots;

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void trimperks$needLevels(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof SmithingMenu) || !inputSlots.getItem(0).isEmpty() || player.getAbilities().instabuild) return;
		if (player.experienceLevel < TrimPerks.config.get("general.material_swap_levels")) cir.setReturnValue(false);
	}
}
