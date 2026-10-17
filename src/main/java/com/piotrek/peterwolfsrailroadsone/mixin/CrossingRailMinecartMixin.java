package com.piotrek.peterwolfsrailroadsone.mixin;

import com.piotrek.peterwolfsrailroadsone.CrossingRailBlock;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NewMinecartBehavior.class)
public abstract class CrossingRailMinecartMixin extends MinecartBehavior {
	private CrossingRailMinecartMixin(final AbstractMinecart minecart) {
		super(minecart);
	}

	@Redirect(
		method = { "adjustToRails", "moveAlongTrack", "stepAlongTrack" },
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"
		)
	)
	private <T extends Comparable<T>> T peterwolfsRailroadsOne$crossingShape(final BlockState state, final Property<T> property) {
		if (state.getBlock() instanceof CrossingRailBlock && property == CrossingRailBlock.SHAPE) {
			@SuppressWarnings("unchecked")
			T travelShape = (T) CrossingRailBlock.travelShape(this.minecart);
			return travelShape;
		}
		return state.getValue(property);
	}
}
