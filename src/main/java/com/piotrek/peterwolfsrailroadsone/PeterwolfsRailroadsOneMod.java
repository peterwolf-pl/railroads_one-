package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PeterwolfsRailroadsOneMod.MOD_ID)
public class PeterwolfsRailroadsOneMod {
	public static final String MOD_ID = "peterwolfs_railroads_one";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public PeterwolfsRailroadsOneMod(final IEventBus modBus) {
		ModBlocks.BLOCKS.register(modBus);
		ModEntities.ENTITY_TYPES.register(modBus);
		ModItems.ITEMS.register(modBus);
		modBus.addListener(PeterwolfsRailroadsOneMod::addCreative);
		NeoForge.EVENT_BUS.register(GameEvents.class);
		LOGGER.info("Peterwolf's RailRoad's One prototype loaded");
	}

	private static void addCreative(final BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) {
			return;
		}
		event.insertAfter(Items.RAIL.getDefaultInstance(), ModItems.STRAIGHT_RAIL.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.STRAIGHT_RAIL.get().getDefaultInstance(), ModItems.CROSSING_RAIL.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.CROSSING_RAIL.get().getDefaultInstance(), ModItems.GENTLE_RAIL_CURVE_LEFT.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.GENTLE_RAIL_CURVE_LEFT.get().getDefaultInstance(), ModItems.GENTLE_RAIL_CURVE_RIGHT.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.GENTLE_RAIL_CURVE_RIGHT.get().getDefaultInstance(), ModItems.GENTLE_RAIL_CURVE_LEFT_2X2.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.GENTLE_RAIL_CURVE_LEFT_2X2.get().getDefaultInstance(), ModItems.GENTLE_RAIL_CURVE_RIGHT_2X2.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.GENTLE_RAIL_CURVE_RIGHT_2X2.get().getDefaultInstance(), ModItems.PARALLEL_SIDING_SWITCH_LEFT.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.PARALLEL_SIDING_SWITCH_LEFT.get().getDefaultInstance(), ModItems.PARALLEL_SIDING_SWITCH_RIGHT.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.PARALLEL_SIDING_SWITCH_RIGHT.get().getDefaultInstance(), ModItems.LOCOMOTIVE.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.LOCOMOTIVE.get().getDefaultInstance(), ModItems.LOCOMOTIVE_WITH_MINECART.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		event.insertAfter(ModItems.LOCOMOTIVE_WITH_MINECART.get().getDefaultInstance(), ModItems.RAIL_SEMAPHORE.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final class GameEvents {
		private GameEvents() {
		}

		@SubscribeEvent
		public static void onLevelTick(final LevelTickEvent.Post event) {
			if (event.getLevel() instanceof ServerLevel level) {
				TrackGuidanceScheduler.runAfterEntityTicks(level);
			}
		}

		@SubscribeEvent
		public static void onServerStopped(final ServerStoppedEvent event) {
			TrackGuidanceScheduler.clear();
		}

		@SubscribeEvent
		public static void onRegisterCommands(final RegisterCommandsEvent event) {
			RailroadDebugCommand.register(event.getDispatcher());
		}

		@SubscribeEvent
		public static void onServerTick(final ServerTickEvent.Post event) {
			RailroadDebugCommand.tick();
		}
	}
}
