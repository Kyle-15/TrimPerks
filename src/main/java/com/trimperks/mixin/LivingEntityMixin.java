package com.trimperks.mixin;

import com.trimperks.Effects;
import com.trimperks.Perks;
import com.trimperks.Trims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Weapon damage bonuses. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float trimperks$damage(float amount, ServerLevel level, DamageSource source) {
		return Effects.modifyDamage((LivingEntity) (Object) this, source, amount);
	}

	/** Rib armor: Wither lasts shorter. */
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance trimperks$wither(MobEffectInstance effect, MobEffectInstance same, Entity source) {
		if (!((Object) this instanceof ServerPlayer p) || !effect.is(MobEffects.WITHER) || Trims.armorHasEnchant(p, Enchantments.FIRE_PROTECTION)) return effect;
		Trims.Active a = Trims.get(p, Perks.Kind.ARMOR, "rib");
		if (a == null) return effect;
		int ticks = (int) (effect.getDuration() * (1 - Math.min(0.75, a.power("reduction", null))));
		return new MobEffectInstance(effect.getEffect(), ticks, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}
}
