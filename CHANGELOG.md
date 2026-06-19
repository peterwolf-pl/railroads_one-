# Changelog

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
