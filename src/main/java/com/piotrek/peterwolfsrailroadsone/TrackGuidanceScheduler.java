package com.piotrek.peterwolfsrailroadsone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

final class TrackGuidanceScheduler {
	private static final Map<ServerLevel, Set<GentleRailCurveEntity>> CURVES = new WeakHashMap<>();
	private static final Map<ServerLevel, Set<ParallelSidingSwitchEntity>> SWITCHES = new WeakHashMap<>();

	private TrackGuidanceScheduler() {
	}

	static void queue(final ServerLevel level, final GentleRailCurveEntity curve) {
		CURVES.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(curve);
	}

	static void queue(final ServerLevel level, final ParallelSidingSwitchEntity sidingSwitch) {
		SWITCHES.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(sidingSwitch);
	}

	static void runAfterEntityTicks(final ServerLevel level) {
		GuidedThisTick.begin(level);
		Set<ParallelSidingSwitchEntity> switches = SWITCHES.remove(level);
		if (switches != null) {
			List<ParallelSidingSwitchEntity> ordered = new ArrayList<>(switches);
			ordered.sort(Comparator
				.comparingDouble((ParallelSidingSwitchEntity sidingSwitch) -> sidingSwitch.guidancePriority(level))
				.thenComparingInt(System::identityHashCode));
			for (ParallelSidingSwitchEntity sidingSwitch : ordered) {
				if (!sidingSwitch.isRemoved() && sidingSwitch.level() == level) {
					sidingSwitch.guideAfterEntityTicks(level);
				}
			}
		}

		Set<GentleRailCurveEntity> curves = CURVES.remove(level);
		if (curves != null) {
			for (GentleRailCurveEntity curve : curves) {
				if (!curve.isRemoved() && curve.level() == level) {
					curve.guideAfterEntityTicks(level);
				}
			}
		}
	}

	static void clear() {
		CURVES.clear();
		SWITCHES.clear();
		GuidedThisTick.clear();
	}
}
