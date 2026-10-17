package com.piotrek.peterwolfsrailroadsone.mixin;

import com.piotrek.peterwolfsrailroadsone.RailSemaphoreBlock;
import com.piotrek.peterwolfsrailroadsone.SemaphoreBrakeAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceSemaphoreMixin implements SemaphoreBrakeAccess {
	@Unique
	private static final String SEMAPHORE_BRAKE_KEY = "PeterwolfsRailroadsOneSemaphoreBrake";

	@Unique
	private boolean peterwolfsRailroadsOne$semaphoreBraked;

	@Override
	public boolean peterwolfsRailroadsOne$isSemaphoreBraked() {
		return this.peterwolfsRailroadsOne$semaphoreBraked;
	}

	@Override
	public void peterwolfsRailroadsOne$setSemaphoreBraked(final boolean semaphoreBraked) {
		this.peterwolfsRailroadsOne$semaphoreBraked = semaphoreBraked;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void peterwolfsRailroadsOne$saveSemaphoreBrake(final ValueOutput output, final CallbackInfo ci) {
		if (this.peterwolfsRailroadsOne$semaphoreBraked) {
			output.putBoolean(SEMAPHORE_BRAKE_KEY, true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void peterwolfsRailroadsOne$loadSemaphoreBrake(final ValueInput input, final CallbackInfo ci) {
		this.peterwolfsRailroadsOne$semaphoreBraked = input.getBooleanOr(SEMAPHORE_BRAKE_KEY, false);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void peterwolfsRailroadsOne$reconcileSemaphoreBrake(final CallbackInfo ci) {
		MinecartFurnace locomotive = (MinecartFurnace) (Object) this;
		if (this.peterwolfsRailroadsOne$semaphoreBraked && locomotive.level() instanceof ServerLevel level) {
			RailSemaphoreBlock.rememberPersistedAutomaticBrake(level, locomotive);
		}
	}
}
