package com.piotrek.peterwolfsrailroadsone.mixin;

import com.piotrek.peterwolfsrailroadsone.CrossingRailBlock;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NewMinecartBehavior.class)
public abstract class CrossingRailMinecartMixin extends MinecartBehavior {
	private CrossingRailMinecartMixin(final AbstractMinecart minecart) {
		super(minecart);
	}

	@WrapOperation(
		method = { "adjustToRails", "moveAlongTrack", "stepAlongTrack" },
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"
		)
	)
	private <T extends Comparable<T>> T peterwolfsRailroadsOne$crossingShape(
		final BlockState state,
		final Property<T> property,
		final Operation<T> original
	) {
		if (state.getBlock() instanceof CrossingRailBlock && property == CrossingRailBlock.SHAPE) {
			@SuppressWarnings("unchecked")
			T travelShape = (T) CrossingRailBlock.travelShape(this.minecart);
			return travelShape;
		}
		return original.call(state, property);
	}
}
