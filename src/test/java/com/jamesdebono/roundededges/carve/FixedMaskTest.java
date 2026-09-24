package com.jamesdebono.roundededges.carve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Logs: carvable blocks with a fixed edge mask (the 4 edges along their axis), whatever surrounds them. */
class FixedMaskTest {
	private static final int VERTICAL = EdgeMask.alongAxis(0);
	private final Carver carver = new Carver(Profile.STAIR_2);

	/** Solid carvable blocks everywhere; the listed offsets are logs with a vertical axis. */
	private static CarveKey.Cells buriedAmong(int[]... logs) {
		return new CarveKey.Cells() {
			@Override
			public int kind(int dx, int dy, int dz) {
				return CarveKey.CARVABLE;
			}

			@Override
			public int fixedMask(int dx, int dy, int dz) {
				for (int[] log : logs) {
					if (log[0] == dx && log[1] == dy && log[2] == dz) {
						return VERTICAL;
					}
				}
				return -1;
			}
		};
	}

	@Test
	void logCutsItsAxisEdgesEvenWhenBuried() {
		long key = CarveKey.compute(buriedAmong(new int[] {0, 0, 0}));
		assertEquals(VERTICAL, CarveKey.mask(key));
	}

	@Test
	void logsSideBySideFormAGrooveWithoutEndCaps() {
		CarveKey.Cells world = buriedAmong(new int[] {0, 0, 0}, new int[] {1, 0, 0});
		CarvedShape a = carver.shape(CarveKey.compute(world));
		CarvedShape b = carver.shape(CarveKey.compute(new CarveKey.Cells() {
			@Override
			public int kind(int dx, int dy, int dz) {
				return world.kind(dx + 1, dy, dz);
			}

			@Override
			public int fixedMask(int dx, int dy, int dz) {
				return world.fixedMask(dx + 1, dy, dz);
			}
		}));
		assertEquals(VERTICAL, b.mask());
		// Both logs cut their facing vertical edges, so the cuts meet as a V-groove: nothing to patch.
		assertTrue(a.endCaps(Face.EAST, b).isEmpty());
		// Against a plain neighbour, both east-side cuts show that neighbour's face: 2 cells x 16 rows each.
		assertEquals(64, a.endCaps(Face.EAST, null).stream().mapToInt(Rect::area).sum());
	}

	@Test
	void logCutEndingOnABlockIsNotAnInsideCornerByItself() {
		// A vertical log on top of the block under test: one cut arrives at each top corner.
		CarveKey.Cells world = buriedAmong(new int[] {0, 1, 0});
		long key = CarveKey.compute(world);
		for (Vertex v : Vertex.VALUES) {
			assertEquals(0, CarveKey.insideCorner(key, v));
		}
	}
}
