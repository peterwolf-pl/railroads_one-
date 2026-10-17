# Changelog

## 0.3.28

- Fixed 26.3 world load: crafting recipes used removed `transportation` category (`Unknown element name:transportation`), which failed registry load on Create New World.

## 0.3.27

- Port to Minecraft 26.3 (Fabric Loader `0.19.5`, Fabric API `0.160.7+26.3`).
- Requires Minecart Chain 1.0.34.

## 0.3.26

- Mining a 2x2/3x3 curve or parallel siding switch removes the whole piece and pops one item on the ground, like a vanilla rail.

## 0.3.25

- Parallel siding switches now show the selected route: unpowered stays iron grey on the main line, redstone throws onto the siding and paints those rails gold like a powered rail.

## 0.3.24

- Adjacent parallel siding switches no longer lose a pulled wagon between them. The switch that currently has the locomotive owns the consist; the previous switch keeps the wagon on its path until the next switch takes over, so the chain does not stretch and reverse the locomotive.

## 0.3.23

- Switches no longer fight neighboring vanilla rails every second, so adjacent turnouts no longer flicker between corners and straight track.
- Added Straight Rail: a vanilla-looking rail that never forms 90° corners. It stays on the chosen axis, can still slope up or down, and right-click toggles north-south / east-west.
- Added Rail Crossing: a 90° intersection that minecarts can travel through both north-south and east-west.

## 0.3.22

- On curves and switches the wagon now stays a fixed coupling length behind the locomotive. It no longer lags and stretches the chain; it catches up along the same path instead of only matching the engine's step.

## 0.3.21

- Parallel siding switch rails now line up with vanilla NS rails at the block edges, so the main line no longer has a visible step at the join.

## 0.3.20

- 2x2 and 3x3 curve sleepers and rails now use the same brown and iron greys as vanilla rails, instead of the brighter orange wood and silver track.

## 0.3.19

- A wagon on a switch can no longer stop or reverse the locomotive. Collision with the wagon is ignored on the switch, chain tension only moves the wagon, and a stalled engine keeps going forward instead of flipping direction.
- Requires Minecart Chain 1.0.28.

## 0.3.18

- A wagon no longer falls off after a switch that leads straight into a 2x2/3x3 curve. The curve now keeps the consist on the path from the switch exit, and the wagon is not frozen off-rails while the locomotive is already on the curve.
- Requires Minecart Chain 1.0.27.

## 0.3.17

- Wagons no longer teleport onto the locomotive or to a computed slot behind it. They keep their place on the path and only take the same small step the engine takes. Jumping to a target position was causing the overlap and bounce.

## 0.3.16

- A chained wagon no longer drives into the locomotive on a switch. The wagon is not simulated on its own: it stays a coupling-length behind the engine on the same path, including the approach. Overlap no longer starts the bounce.
- Requires Minecart Chain 1.0.26 so the wagon also drops vanilla physics while the locomotive is on the switch.

## 0.3.15

- On a switch the wagon no longer chooses its own route. It is guided kinematically behind the locomotive on the same path, copying the locomotive pose at that point, including the approach before the turnout.

## 0.3.14

- A wagon no longer parks on the switch entrance and blocks the locomotive. The switch is shorter than a coupling, so only the locomotive is guided through; the wagon waits on the approach until the engine has cleared.

## 0.3.13

- Locomotives turning east-to-south (and other 90-degree curve exits) no longer coast off the markers: vanilla physics stay off while the curve owns the cart, and the handoff lands on the next vanilla rail.

## 0.3.12

- Linked wagons no longer use chain tension on 2x2/3x3 curves or siding switches. They follow the locomotive along the same path, copying its pose at that point on the route.
- Vanilla minecart physics are skipped on a guided curve/switch so a locomotive turning east-to-south cannot coast off the rails. Exit hands off onto the next vanilla rail instead of dropping the cart on a marker.

## 0.3.11

- 3x3 and 2x2 curves now yaw only the 90 degrees of the arc, in the same direction as the rails. Pose is taken from the cart at entry and advanced by the curve sweep.
- Parallel siding switches use the same heading-follow yaw: the body turns with the selected route, without a 180-degree reversal.

## 0.3.10

- Moved 2x2 curve, 3x3 curve, and parallel-switch guidance to the end of the level tick so vanilla minecart physics can no longer overwrite the controller's final position or orientation.
- Replaced stale vanilla interpolation samples with one authoritative guided sample containing the exact position, path movement, yaw, and body-pose variant.
- The closest valid body-pose variant is selected on entry and held for the complete route, fixing remaining locomotive and linked-wagon flips on curve orientations that cross Minecraft's yaw boundary.
- Parallel switches now lock the first selected route for the complete linked consist and release it only after the final wagon clears, preventing cars from splitting across main and siding tracks.
- Switch traversal now uses the exact chord to the next path point, matching the smooth 2x2/3x3 curve controller and avoiding tangent overshoot or stalls.
- Requires Minecart Chain 1.0.18 for stable consist identifiers and improved tangent-based coupling.

## 0.3.9

- Fixed pulled and pushed minecarts visibly rotating around their center while traversing 2x2 and 3x3 curves.
- Curve entry now locks the minecart's current vanilla body-pose variant for the complete traversal.
- Curve guidance updates yaw and Minecraft 26.2's `flipped` state together, preventing chain corrections from being interpolated as an artificial 180-degree reversal.
- The controlled yaw uses the same movement-to-pose conversion as `NewMinecartBehavior`, preserving smooth rotation in both travel directions and through the exit transition.

## 0.3.8

- Fixed pulled minecarts spinning while traversing 2x2 and 3x3 curves.
- Curve guidance no longer fights Minecraft 26.2's native `flipped` orientation state by writing a second, conflicting yaw each tick.
- Minecarts now keep the exact smooth curve movement vector while vanilla controls body rotation and client interpolation.

## 0.3.7

- Smoothed minecart, locomotive, and linked-train travel through both 2x2 and 3x3 curves by advancing carts with the exact chord to the next curve point instead of allowing an extra tangent-based overshoot.
- Curve controllers now share their current path direction with Minecart Chain 1.0.16, keeping chain tension and locomotive assistance aligned with the curve.
- A cart's forward/reversed render orientation is selected once when it enters a curve and remains stable for the complete traversal, preventing mid-curve 180-degree flips.
- Curve exit velocity remains aligned with the destination track for a smooth handoff back to vanilla rails.

## 0.3.5

- Fixed stale section reservations accumulating as a train moved through the signal network and eventually blocking unrelated semaphores.
- A reservation now remains valid only for the section being approached or the occupied train's bounded neighboring sections.
- Semaphore-applied brakes remain identifiable across chunk unloads and server restarts, while manual locomotive brakes remain untouched.
- A train stopped automatically on an approach remains registered as waiting even after its velocity reaches zero.

## 0.3.4

- Fixed 3x3 curves losing their controller entity, which made marker tiles break individually and stopped minecart guidance.
- A minecart entering a complete curve footprint now restores a missing curve controller from the marker tile data.
- Breaking a marker without a controller still removes the complete 2x2 or 3x3 curve footprint.
- Curve and siding-switch minecart detection runs every tick again so fast trains cannot skip capture between staggered scans.

## 0.3.3

- Idle curve and siding-switch entities now stagger nearby-minecart scans while active carts continue to receive guidance every tick.
- Curve and siding-switch geometry reuses cached bounds and avoids temporary vector allocations in hot movement calculations.
- Semaphore evaluation indexes each connected train once per pass instead of repeatedly traversing the same chain for every section and signal.
- Semaphore topology refreshes are less frequent while placement and removal still invalidate the cache immediately.
- Directly linked carts bypass full train traversal during collision suppression.

## 0.3.2

- A train now receives a green signal only after atomically reserving the requested protected section and the following protected section.
- Two-track merges retain oldest-request-first arbitration: one train can reserve both required sections while conflicting trains remain braked.
- Look-ahead reservations remain owned while the train is present in the signalled network, preventing another train from entering the second section early.

## 0.3.1

- Fixed a safety bug where a train detected anywhere on an overlapping merge section was treated as cleared without owning its reservation.
- Only the reservation owner may now enter a protected or conflicting section; unreserved occupants trigger an emergency brake.
- Topology cache refreshes preserve active reservations so a train already inside a section cannot lose its clearance mid-route.
- Train-to-train minecart collision impulses are suppressed: linked cars do not bounce, and separate trains stop instead of reversing direction.

## 0.3.0

- Rail semaphores now emit redstone power level 15 while showing an occupied/red aspect, readable by redstone dust and comparators.
- Semaphore topology and approach tracks are cached and the whole signal network is evaluated once per interval instead of once per semaphore.
- Overlapping protected sections at merges now share reservations; one waiting train receives clearance while conflicting trains remain braked.
- Waiting reservations are stable and oldest-first to prevent both trains from being held indefinitely at a merge.
- Curve footprint validation and siding-switch maintenance are staggered to remove redundant per-tick block scans.

## 0.2.1

- Rail semaphore sections now follow the actual connected rail path, including slopes, mod curves, and siding switches, instead of using a straight geometric corridor.
- Semaphores ignore nearby parallel rails that are not connected to their section.
- A locomotive brake applied by a semaphore is now released automatically when the protected section becomes free.

## 0.2.0

- Added the two-block-tall Rail Semaphore item and block.
- A pair of nearby semaphores now marks a protected track section.
- Occupied sections light both semaphores red and automatically apply the locomotive brake to another train approaching that section.
