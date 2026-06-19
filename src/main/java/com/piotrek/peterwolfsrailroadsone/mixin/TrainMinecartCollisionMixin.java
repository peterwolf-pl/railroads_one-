package com.piotrek.peterwolfsrailroadsone.mixin;

import com.piotrek.peterwolfsrailroadsone.TrainCollisionGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class TrainMinecartCollisionMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void peterwolfsRailroadsOne$preventTrainBounce(final Entity other, final CallbackInfo ci) {
		if (TrainCollisionGuard.preventBounce((AbstractMinecart) (Object) this, other)) {
			ci.cancel();
		}
	}
}
