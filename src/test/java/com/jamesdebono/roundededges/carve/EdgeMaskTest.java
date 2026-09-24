package com.jamesdebono.roundededges.carve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EdgeMaskTest {
	/** A tiny world: the block under test sits at the origin; listed offsets are solid, the rest is air. */
	private static EdgeMask.EmptyTest solidAt(int[]... solid) {
		Set<Long> set = new HashSet<>();
		for (int[] s : solid) {
			set.add(key(s[0], s[1], s[2]));
		}
		return (dx, dy, dz) -> !set.contains(key(dx, dy, dz));
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 8) << 16) | ((long) (y + 8) << 8) | (z + 8);
	}

	private static int mask(Edge... edges) {
		int m = 0;
		for (Edge e : edges) {
			m |= e.bit();
		}
		return m;
	}

	@Test
	void loneBlockCutsAll12() {
		assertEquals(EdgeMask.ALL, EdgeMask.compute(solidAt()));
	}

	@Test
	void blockRestingOnFloorCutsTopAndVerticalEdges() {
		// Floor: the 3x3 layer below.
		int[][] floor = new int[9][];
		int i = 0;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				floor[i++] = new int[] {x, -1, z};
			}
		}
		int expected = mask(Edge.UP_NORTH, Edge.UP_SOUTH, Edge.UP_WEST, Edge.UP_EAST,
				Edge.NORTH_WEST, Edge.NORTH_EAST, Edge.SOUTH_WEST, Edge.SOUTH_EAST);
		assertEquals(expected, EdgeMask.compute(solidAt(floor)));
	}

	@Test
	void blockSetIntoFlatFloorCutsNothing() {
		// Flat ground at y=0 (this block's layer) and below; air above.
		int[][] ground = new int[18][];
		int i = 0;
		for (int y = -1; y <= 0; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					ground[i++] = new int[] {x, y, z};
				}
			}
		}
		assertEquals(0, EdgeMask.compute(solidAt(ground)));
	}

	@Test
	void buriedBlockCutsNothing() {
		EdgeMask.EmptyTest allSolid = (dx, dy, dz) -> false;
		assertEquals(0, EdgeMask.compute(allSolid));
	}

	@Test
	void cliffTopCutsOneEdge() {
		// Ground everywhere except to the north, where it drops away: only the top-north edge is exposed.
		int[][] ground = new int[12][];
		int i = 0;
		for (int y = -1; y <= 0; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = 0; z <= 1; z++) {
					ground[i++] = new int[] {x, y, z};
				}
			}
		}
		assertEquals(mask(Edge.UP_NORTH), EdgeMask.compute(solidAt(ground)));
	}

	@Test
	void diagonalBlockPreventsCut() {
		// Face neighbours above and north are air, but the cell above-north is solid: no cut.
		int[][] solid = {{0, 1, -1}};
		int m = EdgeMask.compute(solidAt(solid));
		assertEquals(0, m & Edge.UP_NORTH.bit());
	}

	@Test
	void candidatesRestrictTheTest() {
		int alongX = EdgeMask.alongAxis(2);
		assertEquals(mask(Edge.UP_NORTH, Edge.UP_SOUTH, Edge.DOWN_NORTH, Edge.DOWN_SOUTH), alongX);
		assertEquals(alongX, EdgeMask.compute(solidAt(), alongX));
	}
}
