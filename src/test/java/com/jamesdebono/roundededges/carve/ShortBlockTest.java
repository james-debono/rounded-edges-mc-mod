package com.jamesdebono.roundededges.carve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Paths and farmland: 15/16 tall, so their top-edge cuts start one row lower. */
class ShortBlockTest {
	private final Carver carver = new Carver(Profile.STAIR_2);

	private static CarveKey.Cells shortBlockIn(CarveKey.Cells around) {
		return new CarveKey.Cells() {
			@Override
			public int kind(int dx, int dy, int dz) {
				return around.kind(dx, dy, dz);
			}

			@Override
			public boolean isShort(int dx, int dy, int dz) {
				return dx == 0 && dy == 0 && dz == 0;
			}
		};
	}

	@Test
	void uncutShortBlockStillGetsItsOwnShape() {
		long key = CarveKey.compute(shortBlockIn((dx, dy, dz) -> CarveKey.CARVABLE));
		assertTrue(CarveKey.isShort(key));
		assertEquals(0, CarveKey.mask(key));
		CarvedShape shape = carver.shape(key);
		assertEquals(256, shape.removedCount(), "just the top row");
		assertEquals(List.of(new Rect(0, 0, 16, 16, 1)), List.of(shape.rects(Face.UP)), "top face a sixteenth down");
	}

	@Test
	void topEdgeCutStartsFromTheRealTop() {
		// Ground below and to the south; open above and to the north: only the top-north edge is exposed.
		long key = CarveKey.compute(shortBlockIn((dx, dy, dz) -> dy < 0 || (dy == 0 && dz >= 0) ? CarveKey.CARVABLE : CarveKey.EMPTY));
		assertEquals(Edge.UP_NORTH.bit(), CarveKey.mask(key));
		CarvedShape shape = carver.shape(key);
		for (int x = 0; x < 16; x++) {
			assertFalse(shape.kept(x, 14, 0));
			assertFalse(shape.kept(x, 14, 1));
			assertFalse(shape.kept(x, 13, 0));
			assertTrue(shape.kept(x, 14, 2));
			assertTrue(shape.kept(x, 13, 1));
			assertTrue(shape.kept(x, 12, 0));
		}
	}
}
