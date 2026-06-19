# Peterwolf's RailRoad's One

Peterwolf's RailRoad's One is a Fabric prototype mod that adds smooth 3x3 railroad curves for Minecraft minecarts. The goal is to replace the sharp one-block 90 degree vanilla rail turn with a wider railway curve that reads more like real track geometry.

## Features

- Gentle Rail Curve Left 3x3 and Right 3x3 items.
- Gentle Rail Curve Left 2x2 and Right 2x2 items.
- Placeable 3x3 and 2x2 curve entities with saved orientation.
- Occupancy markers that prevent overlapping curve placement.
- Dedicated high-resolution image-tile models for the 3x3 and 2x2 curves.
- Prototype minecart guidance that takes over nearby minecarts and moves them along a quarter-circle path.
- Ready-to-place locomotive and locomotive-with-minecart items powered by the bundled Minecart Chain module.
- Rail semaphores that protect connected sections, brake conflicting trains, and emit redstone power while red.
- Two-section look-ahead clearance: a train moves only after reserving two consecutive protected sections.
- Fair section reservations at merging tracks so one waiting train is released instead of both remaining stopped.
- English and Polish translations.
- Crafting recipes for both curve items.

## Crafting

Both prototype items currently use the same recipe:

```text
RIR
RSR
RIR
```

`R` is a rail, `I` is an iron ingot, and `S` is a stick.

TODO: split the left and right recipes into distinct patterns once the gameplay shape is final.

## How To Use

1. Start Minecraft with the mod installed.
2. Take a 3x3 or 2x2 Gentle Rail Curve item from the tools and utilities creative tab.
3. Right-click flat ground to place the curve.
4. Rotate your player before placement to choose the curve orientation.
5. Try placing another curve on the same 3x3 area; the mod should reject it.
6. Push a minecart into the curve area to test the prototype arc guidance.

## Building

The Minecart Chain dependency is included in `deps/minecart-chain`, so the repository builds without a separate sibling checkout:

```text
./gradlew build
```

The RailRoad's One JAR is generated in `build/libs/`.

## Prototype Status

This is an intentionally practical first prototype. Placement, rotation, 3x3 occupancy, entity persistence, item UX, recipes, translations, and basic minecart curve guidance are implemented.

## Known Limitations

- Minecart physics are not fully integrated with vanilla rail behavior yet.
- Technical marker blocks reserve the 3x3 footprint and are invisible in normal rendering.

## Test Checklist

1. Run Minecraft with the mod.
2. Take Gentle Rail Curve Left.
3. Place it on flat terrain.
4. Check that it occupies a 3x3 area.
5. Check rotation against player direction.
6. Place a second curve nearby.
7. Try placing a curve on an occupied area.
8. Send a minecart into both curve sizes and check that it leaves the end of the curve instead of being held at the exit.

## Roadmap

- Tune minecart entry and exit detection for all vanilla cart types.
- Add better rail connection handoff at curve endpoints.
- Add final promotional artwork and screenshots.
