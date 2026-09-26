# Changelog

## 1.0.0 (beta) — 2026-09-26
First public release, for Minecraft 26.2 (Fabric).

- Stepped 2-sixteenth bevel on the exposed edges of 114 natural block types: stone types and ores, soil and grass, sand and gravel, terracotta, snow and ice, nether and end terrain, leaves, dirt paths and farmland.
- Logs, stripped logs, nether stems and bone blocks always bevel the four edges along their length.
- Clean mitred outside corners; inside corners where cuts meet are filled flush.
- Edges round next to anything that isn't a full solid block (water, plants, torches, fences, glass, slabs), with neighbouring faces drawn back into the cut so nothing opens up.
- Texture variants, tint and grass overlays carry onto the cuts; steps get smooth lighting.
- Carving is limited to 64 blocks around the camera (24 for leaves). Works with Sodium and Fabric's renderer; no options.
