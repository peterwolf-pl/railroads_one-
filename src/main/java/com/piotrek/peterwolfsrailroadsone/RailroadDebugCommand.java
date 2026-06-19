package com.piotrek.peterwolfsrailroadsone;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RailroadDebugCommand {
	private static final List<DebugVisualization> ACTIVE_VISUALIZATIONS = Collections.synchronizedList(new ArrayList<>());

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("railroad")
				.then(literal("debug")
					.then(argument("section_id", StringArgumentType.string())
						.suggests((context, builder) -> {
							ServerLevel level = context.getSource().getLevel();
							for (UUID id : RailSemaphoreBlock.getActiveSectionIds(level)) {
								builder.suggest(id.toString());
							}
							return builder.buildFuture();
						})
						.executes(context -> {
							String sectionIdStr = StringArgumentType.getString(context, "section_id");
							return runDebugCommand(context.getSource(), sectionIdStr);
						})
					)
				)
			);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ACTIVE_VISUALIZATIONS.removeIf(vis -> {
				vis.ticksRemaining--;
				if (vis.ticksRemaining < 0) {
					return true;
				}
				for (BlockPos pos : vis.blocks) {
					if (vis.level.isLoaded(pos)) {
						vis.level.sendParticles(
							ParticleTypes.FLAME,
							pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
							1, 0.05, 0.05, 0.05, 0.0
						);
					}
				}
				return false;
			});
		});
	}

	private static int runDebugCommand(final CommandSourceStack source, final String sectionIdStr) {
		try {
			UUID sectionId = UUID.fromString(sectionIdStr);
			ServerLevel level = source.getLevel();
			List<BlockPos> blocks = RailSemaphoreBlock.getSectionTrackBlocks(level, sectionId);
			if (blocks.isEmpty()) {
				source.sendFailure(Component.literal("Section ID not found or has no tracks."));
				return 0;
			}

			ACTIVE_VISUALIZATIONS.add(new DebugVisualization(level, blocks, 200)); // 200 ticks = 10 seconds
			source.sendSuccess(() -> Component.literal("Displaying section " + sectionId 
				+ " (" + blocks.getFirst().toShortString() + " to " + blocks.getLast().toShortString() + ") for 10 seconds."), true);
			return 1;
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.literal("Invalid UUID format."));
			return 0;
		}
	}

	private static final class DebugVisualization {
		final ServerLevel level;
		final List<BlockPos> blocks;
		int ticksRemaining;

		DebugVisualization(final ServerLevel level, final List<BlockPos> blocks, final int durationTicks) {
			this.level = level;
			this.blocks = blocks;
			this.ticksRemaining = durationTicks;
		}
	}
}
