# Rounded Edges

A client-side Fabric mod for Minecraft Java 26.2 that carves a small stepped bevel into the exposed edges of natural terrain, logs and leaves.

The bevel is two sixteenths deep, cut as 1-pixel steps, so the world keeps its blocky look but loses the razor-sharp corners. The cuts are real geometry: you see through them to whatever is behind, lit and textured like the rest of the block.

## What it does
- **Terrain rounds where it meets open space.** An edge is cut when all three blocks around it are open (air, water, plants, torches, fences, glass, slabs and the like). Flat ground and buried blocks are left alone.
- **Corners meet cleanly.** Outside corners mitre like a picture frame. Where two or three cuts meet at an inside corner, they continue into the corner block and meet flush.
- **Logs look round.** Logs, stripped logs, nether stems and bone blocks always bevel the four edges along their length, so stacked logs form grooves.
- **Covers the natural world.** Stone types, every ore, dirt, grass, sand, gravel, terracotta, snow and ice, nether and end terrain, leaves, paths and farmland: 114 block types. Building blocks such as cobblestone, bricks and planks are left alone.
- **Looks native.** Cuts keep each block's texture variation, tint and grass overlay, and get smooth lighting.

## Compatibility
- Fabric Loader and **Fabric API** required. Client-side only: works on any server, including ones without the mod.
- Works with **Sodium** (tested with Sodium 0.9) and with Fabric's own renderer. Tested alongside Iris shaders, Continuity and C2ME.
- No options. Carving is limited to 64 blocks around you (leaves 24), because the steps are about a pixel beyond that.
- Visual only: collision and the block outline stay full cubes.

## Installing
Put the jar in your `mods` folder along with Fabric API.

## Building
Requires Java 25. From this folder:
- `gradlew build`: jar in `build/libs/`, runs the unit tests.
- `gradlew runClient`: dev client with Sodium (`-Puse_sodium=false` for Fabric's renderer).
- `gradlew runClientGameTest`: builds test scenes and saves screenshots to `build/run/clientGameTest/screenshots/`.

## License
[MIT](LICENSE)
