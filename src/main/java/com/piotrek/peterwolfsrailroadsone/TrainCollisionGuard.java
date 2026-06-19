package com.piotrek.peterwolfsrailroadsone;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartTrainLogic;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.phys.Vec3;

public final class TrainCollisionGuard {
	private static final int MAX_TRAIN_CARTS = 64;

	private TrainCollisionGuard() {
	}

	public static boolean preventBounce(final AbstractMinecart first, final Entity other) {
		if (!(other instanceof AbstractMinecart second) || !(first.level() instanceof ServerLevel level)) {
			return false;
		}

		if (inSameTrain(level, first, second)) {
			return true;
		}

		net.minecraft.core.BlockPos track1 = RailSectionPathfinder.trackPositionForCart(level, first);
		net.minecraft.core.BlockPos track2 = RailSectionPathfinder.trackPositionForCart(level, second);
		boolean onSameLine = track1.equals(track2) || RailSectionPathfinder.findPath(level, track1, track2, 3).isPresent();
		if (!onSameLine) {
			return true;
		}

		Optional<MinecartFurnace> firstLocomotive = MinecartTrainLogic.controlledLocomotive(level, first);
		Optional<MinecartFurnace> secondLocomotive = MinecartTrainLogic.controlledLocomotive(level, second);
		if (firstLocomotive.isEmpty() && secondLocomotive.isEmpty()) {
			return false;
		}

		if (firstLocomotive.isPresent()
			&& secondLocomotive.isPresent()
			&& firstLocomotive.get().getUUID().equals(secondLocomotive.get().getUUID())) {
			return true;
		}

		stopConnectedCarts(level, first);
		stopConnectedCarts(level, second);
		firstLocomotive.ifPresent(MinecartTrainLogic::applyTrainBrake);
		secondLocomotive.ifPresent(MinecartTrainLogic::applyTrainBrake);
		return true;
	}

	private static boolean inSameTrain(final ServerLevel level, final AbstractMinecart first, final AbstractMinecart second) {
		Queue<AbstractMinecart> pending = new ArrayDeque<>();
		Set<UUID> seen = new HashSet<>();
		pending.add(first);
		seen.add(first.getUUID());

		while (!pending.isEmpty() && seen.size() <= MAX_TRAIN_CARTS) {
			AbstractMinecart minecart = pending.remove();
			if (minecart.getUUID().equals(second.getUUID())) {
				return true;
			}
			MinecartChainAccess links = (MinecartChainAccess) minecart;
			addLinkedCart(level, links.minecartChain$getFirstLink(), seen, pending);
			addLinkedCart(level, links.minecartChain$getSecondLink(), seen, pending);
		}
		return false;
	}

	private static void stopConnectedCarts(final ServerLevel level, final AbstractMinecart start) {
		Queue<AbstractMinecart> pending = new ArrayDeque<>();
		Set<UUID> seen = new HashSet<>();
		pending.add(start);
		seen.add(start.getUUID());

		while (!pending.isEmpty() && seen.size() <= MAX_TRAIN_CARTS) {
			AbstractMinecart minecart = pending.remove();
			Vec3 movement = minecart.getDeltaMovement();
			minecart.setDeltaMovement(0.0D, movement.y, 0.0D);
			MinecartChainAccess links = (MinecartChainAccess) minecart;
			addLinkedCart(level, links.minecartChain$getFirstLink(), seen, pending);
			addLinkedCart(level, links.minecartChain$getSecondLink(), seen, pending);
		}
	}

	private static void addLinkedCart(
		final ServerLevel level,
		final Optional<UUID> linkedId,
		final Set<UUID> seen,
		final Queue<AbstractMinecart> pending
	) {
		linkedId.filter(seen::add).ifPresent(id -> {
			Entity linked = level.getEntityInAnyDimension(id);
			if (linked instanceof AbstractMinecart minecart) {
				pending.add(minecart);
			}
		});
	}
}
