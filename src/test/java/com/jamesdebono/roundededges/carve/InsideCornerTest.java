package com.jamesdebono.roundededges.carve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The inside-corner cases from the design sketches. The block under test ("B") sits at the origin; V is its
 * up-south-east corner.
 *
 * <ul>
 * <li>Three cuts (1A/2A): T on top of B, L south of B, R east of B. T's vertical edge, L's top-east edge and R's
 * top-south edge are all cut and all end at V.</li>
 * <li>Two cuts (1B/2B/2C): L and R only, with B's top open.</li>
 * </ul>
 */
class InsideCornerTest {
	private static final int[] T = {0, 1, 0};
	private static final int[] L = {0, 0, 1};
	private static final int[] R = {1, 0, 0};
	private static final int[] B = {0, 0, 0};

	/**
	 * Solid ground (y <= 0) with a pit where x >= 1 and z >= 1, plus walls at y = 1 behind T (x <= -1 or z <= -1), so
	 * only the corner being tested is open. The listed blocks are carvable (offsets relative to B); everything else
	 * solid is a plain block.
	 */
	private static CarveKey.Cells world(int[]... carvable) {
		Map<Long, Integer> kinds = new HashMap<>();
		for (int[] c : carvable) {
			kinds.put(key(c[0], c[1], c[2]), CarveKey.CARVABLE);
		}
		return (dx, dy, dz) -> {
			Integer kind = kinds.get(key(dx, dy, dz));
			if (kind != null) {
				return kind;
			}
			boolean ground = dy <= 0 && !(dx >= 1 && dz >= 1);
			boolean wall = dy == 1 && (dx <= -1 || dz <= -1);
			return ground || wall ? CarveKey.SOLID : CarveKey.EMPTY;
		};
	}

	/** The same world seen from the block at {@code at}. */
	private static CarveKey.Cells from(CarveKey.Cells world, int[] at) {
		return (dx, dy, dz) -> world.kind(dx + at[0], dy + at[1], dz + at[2]);
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 8) << 16) | ((long) (y + 8) << 8) | (z + 8);
	}

	private static int removedNear(CarvedShape s) {
		// Cells within 2 of V = (15, 15, 15).
		int n = 0;
		for (int x = 14; x < 16; x++) {
			for (int y = 14; y < 16; y++) {
				for (int z = 14; z < 16; z++) {
					if (!s.kept(x, y, z)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	@Test
	void threeCutsAreDetected() {
		long key = CarveKey.compute(world(B, T, L, R));
		assertEquals(0, CarveKey.mask(key), "B itself has no exposed edges");
		assertEquals(0b111, CarveKey.insideCorner(key, Vertex.UP_SOUTH_EAST));
	}

	@Test
	void twoCutsAreDetected() {
		long key = CarveKey.compute(world(B, L, R));
		assertEquals(0b110, CarveKey.insideCorner(key, Vertex.UP_SOUTH_EAST), "z- and x-axis cuts end at V");
	}

	@Test
	void singleCutAgainstAWallIsNotAnInsideCorner() {
		// L's top-east cut ends against B, but nothing else meets it there.
		long key = CarveKey.compute(world(B, L));
		assertEquals(0, CarveKey.insideCorner(key, Vertex.UP_SOUTH_EAST));
	}

	@Test
	void fillSizesMatchTheSketches() {
		long three = CarveKey.compute(world(B, T, L, R));
		long two = CarveKey.compute(world(B, L, R));
		assertEquals(0, removedNear(new Carver(Profile.STAIR_2, InsideCorners.OFF).shape(three)));
		assertEquals(1, removedNear(new Carver(Profile.STAIR_2, InsideCorners.SINGLE_TEXEL).shape(three)), "1A");
		assertEquals(1, removedNear(new Carver(Profile.STAIR_2, InsideCorners.SINGLE_TEXEL).shape(two)), "1B");
		assertEquals(4, removedNear(new Carver(Profile.STAIR_2, InsideCorners.PYRAMID).shape(three)), "2A");
		assertEquals(4, removedNear(new Carver(Profile.STAIR_2, InsideCorners.PYRAMID).shape(two)), "2B");
		assertEquals(4, removedNear(new Carver(Profile.STAIR_2, InsideCorners.MITRE).shape(three)), "2A");
		assertEquals(5, removedNear(new Carver(Profile.STAIR_2, InsideCorners.MITRE).shape(two)), "2C");
	}

	@Test
	void mitreLeavesNoEndCapsAtTheCorner() {
		// With the mitre fill, each cut continues flush into B, so none of them needs to draw B's face.
		for (int[][] world : new int[][][] {{B, T, L, R}, {B, L, R}}) {
			CarveKey.Cells cells = world(world);
			Carver carver = new Carver(Profile.STAIR_2, InsideCorners.MITRE);
			CarvedShape b = carver.shape(CarveKey.compute(cells));
			CarvedShape l = carver.shape(CarveKey.compute(from(cells, L)));
			CarvedShape r = carver.shape(CarveKey.compute(from(cells, R)));
			assertTrue(l.endCaps(Face.NORTH, b).isEmpty(), "L against B");
			assertTrue(r.endCaps(Face.WEST, b).isEmpty(), "R against B");
			if (world.length == 4) {
				CarvedShape t = carver.shape(CarveKey.compute(from(cells, T)));
				assertTrue(t.endCaps(Face.DOWN, b).isEmpty(), "T against B");
			}
		}
	}

	@Test
	void withoutAFillTheCutsNeedEndCaps() {
		CarveKey.Cells cells = world(B, L, R);
		Carver carver = new Carver(Profile.STAIR_2, InsideCorners.OFF);
		CarvedShape b = carver.shape(CarveKey.compute(cells));
		CarvedShape l = carver.shape(CarveKey.compute(from(cells, L)));
		assertEquals(3, l.endCaps(Face.NORTH, b).stream().mapToInt(Rect::area).sum());
	}
}
