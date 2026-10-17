package com.piotrek.peterwolfsrailroadsone;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartTrainLogic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;

final class GuidedConsist {
	private GuidedConsist() {
	}

	static List<AbstractMinecart> linkedTo(final ServerLevel level, final AbstractMinecart start) {
		List<AbstractMinecart> train = new ArrayList<>();
		List<AbstractMinecart> pending = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		pending.add(start);
		seen.add(start.getUUID());
		for (int index = 0; index < pending.size(); index++) {
			AbstractMinecart current = pending.get(index);
			train.add(current);
			for (AbstractMinecart linked : neighbors(level, current)) {
				if (seen.add(linked.getUUID())) {
					pending.add(linked);
				}
			}
		}
		return train;
	}

	static Map<UUID, Integer> hopsFrom(final ServerLevel level, final AbstractMinecart root) {
		Map<UUID, Integer> hops = new HashMap<>();
		ArrayDeque<AbstractMinecart> queue = new ArrayDeque<>();
		hops.put(root.getUUID(), 0);
		queue.add(root);
		while (!queue.isEmpty()) {
			AbstractMinecart current = queue.remove();
			int hop = hops.get(current.getUUID());
			for (AbstractMinecart linked : neighbors(level, current)) {
				if (hops.putIfAbsent(linked.getUUID(), hop + 1) == null) {
					queue.add(linked);
				}
			}
		}
		return hops;
	}

	static boolean followerFits(final int hops, final double routeLength) {
		return hops * MinecartTrainLogic.couplingDistance() < routeLength * 0.9D;
	}

	static double followProgress(final double leaderProgress, final double direction, final int hops, final double routeLength) {
		return leaderProgress - direction * hops * MinecartTrainLogic.couplingDistance() / routeLength;
	}

	static AbstractMinecart leader(final ServerLevel level, final List<AbstractMinecart> onPath) {
		if (onPath.isEmpty()) {
			return null;
		}

		Set<UUID> onPathIds = new HashSet<>();
		for (AbstractMinecart cart : onPath) {
			onPathIds.add(cart.getUUID());
		}
		Optional<MinecartFurnace> locomotive = MinecartTrainLogic.controlledLocomotive(level, onPath.getFirst());
		if (locomotive.isPresent() && onPathIds.contains(locomotive.get().getUUID())) {
			return locomotive.get();
		}
		for (AbstractMinecart cart : onPath) {
			if (cart instanceof MinecartFurnace) {
				return cart;
			}
		}
		return onPath.getFirst();
	}

	private static List<AbstractMinecart> neighbors(final ServerLevel level, final AbstractMinecart minecart) {
		List<AbstractMinecart> linked = new ArrayList<>(2);
		MinecartChainAccess data = (MinecartChainAccess) minecart;
		addNeighbor(level, data.minecartChain$getFirstLink(), linked);
		addNeighbor(level, data.minecartChain$getSecondLink(), linked);
		return linked;
	}

	private static void addNeighbor(
		final ServerLevel level,
		final Optional<UUID> linkId,
		final List<AbstractMinecart> linked
	) {
		if (linkId.isEmpty()) {
			return;
		}

		Entity entity = level.getEntityInAnyDimension(linkId.get());
		if (entity instanceof AbstractMinecart minecart && !minecart.isRemoved()) {
			linked.add(minecart);
		}
	}
}
