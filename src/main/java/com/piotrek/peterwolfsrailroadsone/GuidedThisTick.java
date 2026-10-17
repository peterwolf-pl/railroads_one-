package com.piotrek.peterwolfsrailroadsone;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

final class GuidedThisTick {
	private static final Map<ServerLevel, Set<UUID>> CARTS = new WeakHashMap<>();

	private GuidedThisTick() {
	}

	static void begin(final ServerLevel level) {
		CARTS.remove(level);
	}

	static void mark(final AbstractMinecart minecart) {
		if (minecart.level() instanceof ServerLevel level) {
			CARTS.computeIfAbsent(level, ignored -> new HashSet<>()).add(minecart.getUUID());
		}
	}

	static boolean contains(final AbstractMinecart minecart) {
		if (!(minecart.level() instanceof ServerLevel level)) {
			return false;
		}
		Set<UUID> ids = CARTS.get(level);
		return ids != null && ids.contains(minecart.getUUID());
	}

	static void clear() {
		CARTS.clear();
	}
}
