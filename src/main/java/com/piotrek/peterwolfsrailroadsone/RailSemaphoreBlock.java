package com.piotrek.peterwolfsrailroadsone;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartTrainLogic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RailSemaphoreBlock extends Block {
	public static final BooleanProperty OCCUPIED = BlockStateProperties.LIT;
	private static final int UPDATE_INTERVAL_TICKS = 5;
	private static final int SEMAPHORE_EXPIRY_TICKS = 40;
	private static final int TOPOLOGY_CACHE_TICKS = 100;
	private static final int MAX_SEMAPHORE_DISTANCE = 128;
	private static final int MAX_SECTION_TRACK_BLOCKS = 192;
	private static final int APPROACH_TRACK_BLOCKS = 7;
	private static final VoxelShape OUTLINE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 32.0D, 11.0D);
	private static final Map<ServerLevel, SemaphoreController> CONTROLLERS = new WeakHashMap<>();

	public RailSemaphoreBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(OCCUPIED, false));
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return context.getLevel().getBlockState(pos.above()).canBeReplaced() ? this.defaultBlockState() : null;
	}

	@Override
	protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston) {
		if (!oldState.is(this)) {
			if (level instanceof ServerLevel serverLevel) {
				controller(serverLevel).register(pos, serverLevel.getGameTime());
			}
			schedule(level, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(
		final BlockState state,
		final ServerLevel level,
		final BlockPos pos,
		final boolean movedByPiston
	) {
		controller(level).remove(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		SemaphoreController controller = controller(level);
		controller.register(pos, level.getGameTime());
		controller.update(level);
		schedule(level, pos);
	}

	@Override
	protected boolean isSignalSource(final BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction direction) {
		return state.getValue(OCCUPIED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction direction) {
		return state.getValue(OCCUPIED) ? 15 : 0;
	}

	@Override
	protected boolean hasAnalogOutputSignal(final BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos, final Direction direction) {
		return state.getValue(OCCUPIED) ? 15 : 0;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return OUTLINE;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OCCUPIED);
	}

	private static SemaphoreController controller(final ServerLevel level) {
		return CONTROLLERS.computeIfAbsent(level, ignored -> new SemaphoreController());
	}

	private static void setOccupied(final ServerLevel level, final BlockPos pos, final boolean occupied) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ModBlocks.RAIL_SEMAPHORE) && state.getValue(OCCUPIED) != occupied) {
			level.setBlock(pos, state.setValue(OCCUPIED, occupied), Block.UPDATE_ALL);
			level.updateNeighbourForOutputSignal(pos, ModBlocks.RAIL_SEMAPHORE);
		}
	}

	private static boolean isSemaphore(final ServerLevel level, final BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.RAIL_SEMAPHORE);
	}

	private static boolean movesToward(final MinecartFurnace locomotive, final BlockPos endpoint) {
		Vec3 toEndpoint = Vec3.atCenterOf(endpoint).subtract(locomotive.position()).horizontal();
		return toEndpoint.lengthSqr() < 0.25D || locomotive.getDeltaMovement().horizontal().dot(toEndpoint) > 0.005D;
	}

	private static void schedule(final Level level, final BlockPos pos) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, ModBlocks.RAIL_SEMAPHORE, UPDATE_INTERVAL_TICKS);
		}
	}

	private static final class SemaphoreController {
		private final Map<BlockPos, Long> knownSemaphores = new HashMap<>();
		private final Map<SectionKey, UUID> reservations = new HashMap<>();
		private final Map<RequestKey, Long> waitingSince = new HashMap<>();
		private final Set<UUID> automaticBrakes = new HashSet<>();
		private List<Section> sections = List.of();
		private Map<SectionKey, Set<SectionKey>> conflicts = Map.of();
		private long lastUpdateTick = Long.MIN_VALUE;
		private long topologyValidUntil = Long.MIN_VALUE;

		private void register(final BlockPos pos, final long gameTime) {
			BlockPos immutablePos = pos.immutable();
			if (this.knownSemaphores.put(immutablePos, gameTime) == null) {
				this.invalidateTopology();
			}
		}

		private void remove(final ServerLevel level, final BlockPos pos) {
			this.knownSemaphores.remove(pos);
			this.invalidateTopology();
			this.lastUpdateTick = Long.MIN_VALUE;
			if (this.knownSemaphores.isEmpty()) {
				this.releaseAllAutomaticBrakes(level);
			}
		}

		private void invalidateTopology() {
			this.topologyValidUntil = Long.MIN_VALUE;
		}

		private void update(final ServerLevel level) {
			long gameTime = level.getGameTime();
			if (this.lastUpdateTick != Long.MIN_VALUE && gameTime - this.lastUpdateTick < UPDATE_INTERVAL_TICKS) {
				return;
			}
			this.lastUpdateTick = gameTime;

			int previousCount = this.knownSemaphores.size();
			this.knownSemaphores.entrySet().removeIf(entry -> gameTime - entry.getValue() > SEMAPHORE_EXPIRY_TICKS
				|| !isSemaphore(level, entry.getKey()));
			if (previousCount != this.knownSemaphores.size()) {
				this.invalidateTopology();
			}

			if (this.knownSemaphores.isEmpty()) {
				this.releaseAllAutomaticBrakes(level);
				return;
			}
			if (gameTime >= this.topologyValidUntil) {
				this.rebuildTopology(level, gameTime);
			}
			this.evaluate(level, gameTime);
		}

		private void rebuildTopology(final ServerLevel level, final long gameTime) {
			Map<BlockPos, BlockPos> trackAnchors = new HashMap<>();
			for (BlockPos semaphorePos : this.knownSemaphores.keySet()) {
				RailSectionPathfinder.nearestTrack(level, semaphorePos).ifPresent(track -> trackAnchors.put(semaphorePos, track));
			}

			List<BlockPos> semaphorePositions = new ArrayList<>(trackAnchors.keySet());
			semaphorePositions.sort(SectionKey::comparePositions);
			List<Section> rebuilt = new ArrayList<>();
			for (int firstIndex = 0; firstIndex < semaphorePositions.size(); firstIndex++) {
				BlockPos firstSignal = semaphorePositions.get(firstIndex);
				BlockPos firstTrack = trackAnchors.get(firstSignal);
				for (int secondIndex = firstIndex + 1; secondIndex < semaphorePositions.size(); secondIndex++) {
					BlockPos secondSignal = semaphorePositions.get(secondIndex);
					BlockPos secondTrack = trackAnchors.get(secondSignal);
					if (firstTrack.equals(secondTrack)
						|| secondSignal.distSqr(firstSignal) > MAX_SEMAPHORE_DISTANCE * MAX_SEMAPHORE_DISTANCE) {
						continue;
					}

					RailSectionPathfinder.findPath(level, firstTrack, secondTrack, MAX_SECTION_TRACK_BLOCKS)
						.filter(path -> hasNoIntermediateSemaphore(path, firstSignal, secondSignal, trackAnchors))
						.filter(path -> path.blocks().size() >= 3)
						.ifPresent(path -> rebuilt.add(createSection(level, firstSignal, secondSignal, path)));
				}
			}

			this.sections = List.copyOf(rebuilt);
			this.conflicts = buildConflicts(this.sections);
			Set<SectionKey> rebuiltKeys = new HashSet<>();
			this.sections.forEach(section -> rebuiltKeys.add(section.key()));
			this.reservations.keySet().retainAll(rebuiltKeys);
			this.topologyValidUntil = gameTime + TOPOLOGY_CACHE_TICKS;
		}

		private void evaluate(final ServerLevel level, final long gameTime) {
			Map<UUID, UUID> trainIds = new HashMap<>();
			Map<SectionKey, SectionSnapshot> snapshots = new HashMap<>();
			for (Section section : this.sections) {
				SectionSnapshot snapshot = this.scanSection(level, section, trainIds);
				snapshots.put(section.key(), snapshot);
			}

			Set<UUID> occupyingTrains = new HashSet<>();
			Map<RequestKey, Approach> requests = new HashMap<>();
			for (SectionSnapshot snapshot : snapshots.values()) {
				occupyingTrains.addAll(snapshot.occupyingTrains());
				for (Approach approach : snapshot.approaches().values()) {
					requests.put(new RequestKey(snapshot.section().key(), approach.trainId()), approach);
				}
			}

			this.waitingSince.keySet().retainAll(requests.keySet());
			for (RequestKey request : requests.keySet()) {
				this.waitingSince.putIfAbsent(request, gameTime);
			}

			Set<UUID> waitingTrains = new HashSet<>();
			requests.keySet().forEach(request -> waitingTrains.add(request.trainId()));
			Set<UUID> presentTrains = new HashSet<>(occupyingTrains);
			presentTrains.addAll(waitingTrains);

			Map<UUID, Set<SectionKey>> sectionsToKeepCache = new HashMap<>();
			this.reservations.entrySet().removeIf(
				entry -> !reservationRemainsValid(level, entry, snapshots, presentTrains, waitingTrains, sectionsToKeepCache)
			);
			this.adoptUnreservedOccupants(snapshots, waitingTrains);
			this.enforceExclusiveReservations(snapshots);

			Set<UUID> protectedOccupants = new HashSet<>();
			for (Map.Entry<SectionKey, UUID> reservation : this.reservations.entrySet()) {
				if (!hasConflictingOccupant(reservation.getKey(), reservation.getValue(), snapshots, waitingTrains)) {
					protectedOccupants.add(reservation.getValue());
				}
			}

			Set<UUID> clearedApproaches = new HashSet<>();
			List<Map.Entry<RequestKey, Approach>> orderedRequests = new ArrayList<>(requests.entrySet());
			orderedRequests.sort(
				Comparator.comparingLong((Map.Entry<RequestKey, Approach> entry) -> this.waitingSince.get(entry.getKey()))
					.thenComparing(entry -> entry.getKey().section(), SectionKey::compare)
					.thenComparing(entry -> entry.getKey().trainId())
			);
			for (Map.Entry<RequestKey, Approach> requestEntry : orderedRequests) {
				RequestKey request = requestEntry.getKey();
				if (clearedApproaches.contains(request.trainId())) {
					continue;
				}
				if (this.hasOneSectionClearance(request)
					|| this.tryReserveOneSection(request, snapshots, waitingTrains)) {
					clearedApproaches.add(request.trainId());
					protectedOccupants.add(request.trainId());
				}
			}

			Set<UUID> desiredBrakes = new HashSet<>();
			Set<BlockPos> occupiedSignals = new HashSet<>();
			Set<BlockPos> redApproachSignals = new HashSet<>();
			Set<BlockPos> greenApproachSignals = new HashSet<>();
			for (UUID occupyingTrain : occupyingTrains) {
				if (!protectedOccupants.contains(occupyingTrain)) {
					addEmergencyBrake(level, occupyingTrain, desiredBrakes);
				}
			}
			for (SectionSnapshot snapshot : snapshots.values()) {
				Section section = snapshot.section();
				if (!snapshot.occupyingTrains().isEmpty()) {
					occupiedSignals.add(section.firstSignal());
					occupiedSignals.add(section.secondSignal());
				}
				for (Approach approach : snapshot.approaches().values()) {
					if (clearedApproaches.contains(approach.trainId())) {
						greenApproachSignals.add(approach.signalPos());
					} else {
						desiredBrakes.add(approach.locomotive().getUUID());
						redApproachSignals.add(approach.signalPos());
					}
				}
			}

			for (Map.Entry<SectionKey, UUID> reservation : this.reservations.entrySet()) {
				SectionSnapshot snapshot = snapshots.get(reservation.getKey());
				if (snapshot == null || !snapshot.occupyingTrains().isEmpty()) {
					continue;
				}
				Approach ownerApproach = snapshot.approaches().get(reservation.getValue());
				if (ownerApproach != null) {
					BlockPos oppositeSignal = ownerApproach.signalPos().equals(snapshot.section().firstSignal())
						? snapshot.section().secondSignal()
						: snapshot.section().firstSignal();
					redApproachSignals.add(oppositeSignal);
				}
			}

			Set<BlockPos> rearSignals = new HashSet<>();
			for (UUID trainId : occupyingTrains) {
				this.collectRearSignals(level, trainId, snapshots, rearSignals);
			}

			redApproachSignals.removeAll(greenApproachSignals);
			redApproachSignals.addAll(occupiedSignals);
			redApproachSignals.addAll(rearSignals);
			this.applyAutomaticBrakes(level, desiredBrakes);
			for (BlockPos semaphore : this.knownSemaphores.keySet()) {
				setOccupied(level, semaphore, redApproachSignals.contains(semaphore));
			}
		}

		private SectionSnapshot scanSection(
			final ServerLevel level,
			final Section section,
			final Map<UUID, UUID> trainIds
		) {
			List<AbstractMinecart> carts = level.getEntitiesOfClass(AbstractMinecart.class, section.searchBox());
			Set<UUID> occupyingTrains = new HashSet<>();
			Map<UUID, Approach> approaches = new HashMap<>();
			List<BlockPos> path = section.path().blocks();
			Set<BlockPos> sectionTrack = section.path().trackBlocks();

			for (AbstractMinecart cart : carts) {
				UUID trainId = trainId(level, cart, trainIds);
				if (RailSectionPathfinder.containsCart(sectionTrack, cart)
					&& RailSectionPathfinder.pathIndexForCart(List.of(path.getFirst(), path.getLast()), cart) < 0) {
					occupyingTrains.add(trainId);
				}

				if (!(cart instanceof MinecartFurnace locomotive)) {
					continue;
				}
				MinecartChainAccess controls = (MinecartChainAccess) locomotive;
				if (!controls.minecartChain$hasEngineLever()
					|| controls.minecartChain$isEngineActive() && !this.automaticBrakes.contains(locomotive.getUUID())) {
					continue;
				}

				BlockPos locomotiveTrack = RailSectionPathfinder.trackPositionForCart(level, locomotive);
				boolean approachingFirst = section.firstApproach().contains(locomotiveTrack)
					&& movesToward(locomotive, path.getFirst());
				boolean approachingSecond = section.secondApproach().contains(locomotiveTrack)
					&& movesToward(locomotive, path.getLast());
				if (!approachingFirst && !approachingSecond) {
					continue;
				}

				BlockPos signalPos;
				if (approachingFirst && approachingSecond) {
					signalPos = locomotive.position().distanceToSqr(Vec3.atCenterOf(path.getFirst()))
						<= locomotive.position().distanceToSqr(Vec3.atCenterOf(path.getLast()))
						? section.firstSignal()
						: section.secondSignal();
				} else {
					signalPos = approachingFirst ? section.firstSignal() : section.secondSignal();
				}
				approaches.putIfAbsent(trainId, new Approach(trainId, locomotive, signalPos));
			}
			return new SectionSnapshot(section, Set.copyOf(occupyingTrains), Map.copyOf(approaches));
		}

		private static Optional<MinecartFurnace> trainLocomotive(final ServerLevel level, final AbstractMinecart cart) {
			return connectedTrain(level, cart).stream()
				.filter(MinecartFurnace.class::isInstance)
				.map(MinecartFurnace.class::cast)
				.filter(furnace -> ((MinecartChainAccess) furnace).minecartChain$hasEngineLever())
				.min(Comparator.comparing(Entity::getUUID));
		}

		private static BlockPos rearSignal(final Section section, final MinecartFurnace locomotive) {
			Vec3 forward = MinecartTrainLogic.drivingDirection(locomotive);
			BlockPos first = section.firstSignal();
			BlockPos second = section.secondSignal();
			Vec3 toFirst = Vec3.atCenterOf(first).subtract(locomotive.position()).horizontal();
			Vec3 toSecond = Vec3.atCenterOf(second).subtract(locomotive.position()).horizontal();
			return toFirst.dot(forward) < toSecond.dot(forward) ? first : second;
		}

		private static BlockPos forwardSignal(final Section section, final MinecartFurnace locomotive) {
			Vec3 forward = MinecartTrainLogic.drivingDirection(locomotive);
			BlockPos first = section.firstSignal();
			BlockPos second = section.secondSignal();
			Vec3 toFirst = Vec3.atCenterOf(first).subtract(locomotive.position()).horizontal();
			Vec3 toSecond = Vec3.atCenterOf(second).subtract(locomotive.position()).horizontal();
			return toFirst.dot(forward) >= toSecond.dot(forward) ? first : second;
		}

		private Section findConnectedSection(final BlockPos signalPos, final SectionKey exclude) {
			return this.sections.stream()
				.filter(section -> !section.key().equals(exclude))
				.filter(section -> section.firstSignal().equals(signalPos) || section.secondSignal().equals(signalPos))
				.findFirst()
				.orElse(null);
		}

		private Set<SectionKey> sectionsToKeep(final ServerLevel level, final UUID trainId, final Map<SectionKey, SectionSnapshot> snapshots) {
			Set<SectionKey> keep = new HashSet<>();
			List<SectionSnapshot> occupiedSnapshots = snapshots.values().stream()
				.filter(snapshot -> snapshot.occupyingTrains().contains(trainId))
				.toList();

			if (occupiedSnapshots.isEmpty()) {
				return Set.of();
			}

			for (SectionSnapshot snapshot : occupiedSnapshots) {
				Section section = snapshot.section();
				keep.add(section.key());

				Optional<MinecartFurnace> locomotiveOpt = level.getEntitiesOfClass(MinecartFurnace.class, section.searchBox()).stream()
					.filter(loco -> ((MinecartChainAccess) loco).minecartChain$hasEngineLever())
					.filter(loco -> trainId.equals(trainLocomotive(level, loco).map(AbstractMinecart::getUUID).orElse(null)))
					.findFirst();

				if (locomotiveOpt.isPresent()) {
					MinecartFurnace locomotive = locomotiveOpt.get();
					
					// Trace behind (up to 2 sections)
					BlockPos rear1 = rearSignal(section, locomotive);
					Section nextBehind = findConnectedSection(rear1, section.key());
					if (nextBehind != null) {
						keep.add(nextBehind.key());
						BlockPos rear2 = nextBehind.firstSignal().equals(rear1) ? nextBehind.secondSignal() : nextBehind.firstSignal();
						Section secondBehind = findConnectedSection(rear2, nextBehind.key());
						if (secondBehind != null) {
							keep.add(secondBehind.key());
						}
					}
					
					// Trace ahead (up to 1 section)
					BlockPos front1 = forwardSignal(section, locomotive);
					Section nextAhead = findConnectedSection(front1, section.key());
					if (nextAhead != null) {
						keep.add(nextAhead.key());
					}
				}
			}
			return keep;
		}

		private void collectRearSignals(final ServerLevel level, final UUID trainId, final Map<SectionKey, SectionSnapshot> snapshots, final Set<BlockPos> redSignals) {
			List<SectionSnapshot> occupiedSnapshots = snapshots.values().stream()
				.filter(snapshot -> snapshot.occupyingTrains().contains(trainId))
				.toList();

			for (SectionSnapshot snapshot : occupiedSnapshots) {
				Section section = snapshot.section();
				Optional<MinecartFurnace> locomotiveOpt = level.getEntitiesOfClass(MinecartFurnace.class, section.searchBox()).stream()
					.filter(loco -> ((MinecartChainAccess) loco).minecartChain$hasEngineLever())
					.filter(loco -> trainId.equals(trainLocomotive(level, loco).map(AbstractMinecart::getUUID).orElse(null)))
					.findFirst();

				if (locomotiveOpt.isPresent()) {
					MinecartFurnace locomotive = locomotiveOpt.get();
					
					// First signal behind
					BlockPos rear1 = rearSignal(section, locomotive);
					redSignals.add(rear1);
					
					// Second signal behind
					Section nextBehind = findConnectedSection(rear1, section.key());
					if (nextBehind != null) {
						BlockPos rear2 = nextBehind.firstSignal().equals(rear1) ? nextBehind.secondSignal() : nextBehind.firstSignal();
						redSignals.add(rear2);
					}
				}
			}
		}

		private static UUID trainId(
			final ServerLevel level,
			final AbstractMinecart cart,
			final Map<UUID, UUID> trainIds
		) {
			UUID cached = trainIds.get(cart.getUUID());
			if (cached != null) {
				return cached;
			}

			List<AbstractMinecart> train = connectedTrain(level, cart);
			UUID representative = trainLocomotive(level, cart)
				.map(AbstractMinecart::getUUID)
				.orElse(cart.getUUID());

			for (AbstractMinecart trainCart : train) {
				trainIds.put(trainCart.getUUID(), representative);
			}
			return representative;
		}

		private static List<AbstractMinecart> connectedTrain(final ServerLevel level, final AbstractMinecart start) {
			List<AbstractMinecart> train = new ArrayList<>();
			Queue<AbstractMinecart> pending = new ArrayDeque<>();
			Set<UUID> seen = new HashSet<>();
			pending.add(start);
			seen.add(start.getUUID());

			while (!pending.isEmpty() && train.size() < 64) {
				AbstractMinecart minecart = pending.remove();
				train.add(minecart);
				MinecartChainAccess links = (MinecartChainAccess) minecart;
				addLinkedCart(level, links.minecartChain$getFirstLink(), seen, pending);
				addLinkedCart(level, links.minecartChain$getSecondLink(), seen, pending);
			}
			return train;
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

		private boolean reservationRemainsValid(
			final ServerLevel level,
			final Map.Entry<SectionKey, UUID> reservation,
			final Map<SectionKey, SectionSnapshot> snapshots,
			final Set<UUID> presentTrains,
			final Set<UUID> waitingTrains,
			final Map<UUID, Set<SectionKey>> sectionsToKeepCache
		) {
			SectionSnapshot snapshot = snapshots.get(reservation.getKey());
			if (snapshot == null) {
				return false;
			}
			UUID owner = reservation.getValue();
			if (!presentTrains.contains(owner)) {
				return false;
			}
			if (this.hasConflictingOccupant(reservation.getKey(), owner, snapshots, waitingTrains)) {
				return false;
			}

			boolean isOccupyingAny = snapshots.values().stream()
				.anyMatch(snap -> snap.occupyingTrains().contains(owner));
			if (isOccupyingAny) {
				Set<SectionKey> keep = sectionsToKeepCache.computeIfAbsent(owner, id -> this.sectionsToKeep(level, id, snapshots));
				if (!keep.contains(reservation.getKey())) {
					return false;
				}
			}

			return true;
		}

		private boolean hasOneSectionClearance(final RequestKey request) {
			return request.trainId().equals(this.reservations.get(request.section()));
		}

		private boolean tryReserveOneSection(
			final RequestKey request,
			final Map<SectionKey, SectionSnapshot> snapshots,
			final Set<UUID> waitingTrains
		) {
			UUID trainId = request.trainId();
			UUID currentOwner = this.reservations.get(request.section());
			if (currentOwner != null && !currentOwner.equals(trainId)
				|| !this.canReserve(request.section(), trainId, snapshots, waitingTrains)) {
				return false;
			}

			this.reservations.put(request.section(), trainId);
			return true;
		}

		private void adoptUnreservedOccupants(
			final Map<SectionKey, SectionSnapshot> snapshots,
			final Set<UUID> waitingTrains
		) {
			for (SectionSnapshot snapshot : snapshots.values()) {
				if (this.reservations.containsKey(snapshot.section().key())) {
					continue;
				}
				Set<UUID> establishedOccupants = new HashSet<>(snapshot.occupyingTrains());
				establishedOccupants.removeAll(waitingTrains);
				if (establishedOccupants.size() != 1) {
					continue;
				}
				UUID occupant = establishedOccupants.iterator().next();
				if (this.canReserve(snapshot.section().key(), occupant, snapshots, waitingTrains)) {
					this.reservations.put(snapshot.section().key(), occupant);
				}
			}
		}

		private void enforceExclusiveReservations(final Map<SectionKey, SectionSnapshot> snapshots) {
			List<Map.Entry<SectionKey, UUID>> ordered = new ArrayList<>(this.reservations.entrySet());
			ordered.sort(
				Comparator.comparing((Map.Entry<SectionKey, UUID> entry) -> {
					SectionSnapshot snapshot = snapshots.get(entry.getKey());
					return snapshot == null || !snapshot.occupyingTrains().contains(entry.getValue());
				}).thenComparing(Map.Entry::getKey, SectionKey::compare)
			);

			Map<SectionKey, UUID> accepted = new HashMap<>();
			for (Map.Entry<SectionKey, UUID> candidate : ordered) {
				boolean conflictsWithAccepted = this.conflicts.getOrDefault(candidate.getKey(), Set.of(candidate.getKey())).stream()
					.map(accepted::get)
					.anyMatch(owner -> owner != null && !owner.equals(candidate.getValue()));
				if (!conflictsWithAccepted) {
					accepted.put(candidate.getKey(), candidate.getValue());
				}
			}
			this.reservations.clear();
			this.reservations.putAll(accepted);
		}

		private boolean canReserve(
			final SectionKey section,
			final UUID trainId,
			final Map<SectionKey, SectionSnapshot> snapshots,
			final Set<UUID> waitingTrains
		) {
			if (this.hasConflictingOccupant(section, trainId, snapshots, waitingTrains)) {
				return false;
			}
			for (SectionKey conflictingSection : this.conflicts.getOrDefault(section, Set.of(section))) {
				UUID owner = this.reservations.get(conflictingSection);
				if (owner != null && !owner.equals(trainId)) {
					return false;
				}
			}
			return true;
		}

		private boolean hasConflictingOccupant(
			final SectionKey section,
			final UUID trainId,
			final Map<SectionKey, SectionSnapshot> snapshots,
			final Set<UUID> waitingTrains
		) {
			for (SectionKey conflictingSection : this.conflicts.getOrDefault(section, Set.of(section))) {
				SectionSnapshot snapshot = snapshots.get(conflictingSection);
				if (snapshot != null && snapshot.occupyingTrains().stream().anyMatch(id ->
					!id.equals(trainId) && (!waitingTrains.contains(id) || this.reservations.containsValue(id))
				)) {
					return true;
				}
			}
			return false;
		}

		private static void addEmergencyBrake(final ServerLevel level, final UUID trainId, final Set<UUID> desiredBrakes) {
			Entity entity = level.getEntityInAnyDimension(trainId);
			if (entity instanceof MinecartFurnace locomotive
				&& ((MinecartChainAccess) locomotive).minecartChain$hasEngineLever()) {
				desiredBrakes.add(locomotive.getUUID());
			}
		}

		private void applyAutomaticBrakes(final ServerLevel level, final Set<UUID> desiredBrakes) {
			Set<UUID> expandedBrakes = new HashSet<>();
			for (UUID locomotiveId : desiredBrakes) {
				Entity entity = level.getEntityInAnyDimension(locomotiveId);
				if (entity instanceof AbstractMinecart cart) {
					for (AbstractMinecart trainCart : connectedTrain(level, cart)) {
						if (trainCart instanceof MinecartFurnace locomotive
							&& ((MinecartChainAccess) locomotive).minecartChain$hasEngineLever()) {
							expandedBrakes.add(locomotive.getUUID());
						}
					}
				} else {
					expandedBrakes.add(locomotiveId);
				}
			}

			Set<UUID> released = new HashSet<>(this.automaticBrakes);
			released.removeAll(expandedBrakes);
			for (UUID locomotiveId : released) {
				Entity entity = level.getEntityInAnyDimension(locomotiveId);
				if (entity instanceof MinecartFurnace locomotive && locomotive.level() == level) {
					MinecartChainAccess controls = (MinecartChainAccess) locomotive;
					if (controls.minecartChain$hasEngineLever() && controls.minecartChain$isEngineActive()) {
						controls.minecartChain$setEngineActive(false);
						level.playSound(null, locomotive.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.65F);
					}
				}
			}

			for (UUID locomotiveId : expandedBrakes) {
				Entity entity = level.getEntityInAnyDimension(locomotiveId);
				if (!(entity instanceof MinecartFurnace locomotive) || locomotive.level() != level) {
					continue;
				}
				MinecartChainAccess controls = (MinecartChainAccess) locomotive;
				if (!controls.minecartChain$isEngineActive()) {
					controls.minecartChain$setEngineActive(true);
					level.playSound(null, locomotive.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.9F, 0.5F);
				}
				MinecartTrainLogic.applyTrainBrake(locomotive);
			}
			this.automaticBrakes.clear();
			this.automaticBrakes.addAll(expandedBrakes);
		}

		private void releaseAllAutomaticBrakes(final ServerLevel level) {
			this.applyAutomaticBrakes(level, Set.of());
			this.reservations.clear();
			this.waitingSince.clear();
		}

		private static Section createSection(
			final ServerLevel level,
			final BlockPos firstSignal,
			final BlockPos secondSignal,
			final RailSectionPathfinder.Path path
		) {
			List<BlockPos> blocks = path.blocks();
			Set<BlockPos> track = path.trackBlocks();
			Set<BlockPos> firstApproach = RailSectionPathfinder.approachTrack(
				level,
				blocks.getFirst(),
				blocks.get(1),
				track,
				APPROACH_TRACK_BLOCKS
			);
			Set<BlockPos> secondApproach = RailSectionPathfinder.approachTrack(
				level,
				blocks.getLast(),
				blocks.get(blocks.size() - 2),
				track,
				APPROACH_TRACK_BLOCKS
			);
			AABB searchBox = RailSectionPathfinder.bounds(track)
				.inflate(APPROACH_TRACK_BLOCKS + 1.0D, 2.5D, APPROACH_TRACK_BLOCKS + 1.0D);
			return new Section(
				SectionKey.of(firstSignal, secondSignal),
				firstSignal,
				secondSignal,
				path,
				Set.copyOf(firstApproach),
				Set.copyOf(secondApproach),
				searchBox
			);
		}

		private static boolean hasNoIntermediateSemaphore(
			final RailSectionPathfinder.Path path,
			final BlockPos first,
			final BlockPos second,
			final Map<BlockPos, BlockPos> trackAnchors
		) {
			Set<BlockPos> interior = new HashSet<>(path.trackBlocks());
			interior.remove(path.blocks().getFirst());
			interior.remove(path.blocks().getLast());
			return trackAnchors.entrySet().stream()
				.filter(entry -> !entry.getKey().equals(first) && !entry.getKey().equals(second))
				.noneMatch(entry -> interior.contains(entry.getValue()));
		}

		private static Map<SectionKey, Set<SectionKey>> buildConflicts(final List<Section> sections) {
			Map<SectionKey, Set<SectionKey>> result = new HashMap<>();
			for (Section section : sections) {
				result.computeIfAbsent(section.key(), ignored -> new HashSet<>()).add(section.key());
			}
			for (int first = 0; first < sections.size(); first++) {
				for (int second = first + 1; second < sections.size(); second++) {
					Section firstSection = sections.get(first);
					Section secondSection = sections.get(second);
					if (overlaps(firstSection.path().trackBlocks(), secondSection.path().trackBlocks())) {
						result.get(firstSection.key()).add(secondSection.key());
						result.get(secondSection.key()).add(firstSection.key());
					}
				}
			}
			Map<SectionKey, Set<SectionKey>> immutable = new HashMap<>();
			result.forEach((key, value) -> immutable.put(key, Set.copyOf(value)));
			return Map.copyOf(immutable);
		}

		private static boolean overlaps(final Set<BlockPos> first, final Set<BlockPos> second) {
			Set<BlockPos> smaller = first.size() <= second.size() ? first : second;
			Set<BlockPos> larger = smaller == first ? second : first;
			return smaller.stream().anyMatch(larger::contains);
		}
	}

	private record Section(
		SectionKey key,
		BlockPos firstSignal,
		BlockPos secondSignal,
		RailSectionPathfinder.Path path,
		Set<BlockPos> firstApproach,
		Set<BlockPos> secondApproach,
		AABB searchBox
	) {
	}

	private record SectionSnapshot(Section section, Set<UUID> occupyingTrains, Map<UUID, Approach> approaches) {
	}

	private record Approach(UUID trainId, MinecartFurnace locomotive, BlockPos signalPos) {
	}

	private record RequestKey(SectionKey section, UUID trainId) {
	}

	private record SectionKey(BlockPos first, BlockPos second) {
		private static SectionKey of(final BlockPos first, final BlockPos second) {
			return comparePositions(first, second) <= 0
				? new SectionKey(first.immutable(), second.immutable())
				: new SectionKey(second.immutable(), first.immutable());
		}

		private static int compare(final SectionKey first, final SectionKey second) {
			int firstPosition = comparePositions(first.first(), second.first());
			return firstPosition != 0 ? firstPosition : comparePositions(first.second(), second.second());
		}

		private static int comparePositions(final BlockPos first, final BlockPos second) {
			int x = Integer.compare(first.getX(), second.getX());
			if (x != 0) {
				return x;
			}
			int y = Integer.compare(first.getY(), second.getY());
			return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
		}
	}
}
