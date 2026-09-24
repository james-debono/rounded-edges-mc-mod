package com.jamesdebono.roundededges.carve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CarvedShapeTest {
	private final Carver carver = new Carver(Profile.STAIR_2);

	private static int mask(Edge... edges) {
		int m = 0;
		for (Edge e : edges) {
			m |= e.bit();
		}
		return m;
	}

	@Test
	void removedCounts() {
		assertEquals(0, carver.shape(0).removedCount());
		assertEquals(48, carver.shape(mask(Edge.UP_NORTH)).removedCount());
		assertEquals(91, carver.shape(mask(Edge.UP_NORTH, Edge.UP_WEST)).removedCount());
		assertEquals(133, carver.shape(mask(Edge.UP_NORTH, Edge.UP_WEST, Edge.NORTH_WEST)).removedCount());
	}

	@Test
	void singleEdgeCrossSection() {
		CarvedShape s = carver.shape(mask(Edge.UP_NORTH));
		for (int x = 0; x < 16; x++) {
			assertFalse(s.kept(x, 15, 0));
			assertFalse(s.kept(x, 15, 1));
			assertFalse(s.kept(x, 14, 0));
			assertTrue(s.kept(x, 15, 2));
			assertTrue(s.kept(x, 14, 1));
			assertTrue(s.kept(x, 13, 0));
		}
	}

	@Test
	void mitreStepsLineUpOnTheDiagonal() {
		CarvedShape s = carver.shape(mask(Edge.UP_NORTH, Edge.UP_WEST));
		for (int y = 0; y < 16; y++) {
			// Width of the cut strip along each face at this layer, measured away from the corner.
			int westStrip = 0;
			while (westStrip < 16 && !s.kept(westStrip, y, 8)) {
				westStrip++;
			}
			int northStrip = 0;
			while (northStrip < 16 && !s.kept(8, y, northStrip)) {
				northStrip++;
			}
			assertEquals(westStrip, northStrip, "layer y=" + y);
			// The inner corner of the L sits on the diagonal x == z.
			if (westStrip > 0) {
				assertTrue(s.kept(westStrip, y, westStrip));
				assertFalse(s.kept(westStrip - 1, y, westStrip));
				assertFalse(s.kept(westStrip, y, westStrip - 1));
			}
		}
	}

	@Test
	void uncutBlockHasNoCarvedFaces() {
		assertEquals(0, carver.shape(0).carvedFaces());
	}

	@Test
	void singleEdgeCarvesItsTwoFacesAndBothEnds() {
		int faces = carver.shape(mask(Edge.UP_NORTH)).carvedFaces();
		// UP and NORTH lose a strip and gain steps; WEST and EAST outlines get the notch. DOWN and SOUTH untouched.
		assertEquals(Face.UP.bit() | Face.NORTH.bit() | Face.WEST.bit() | Face.EAST.bit(), faces);
	}

	@Test
	void faceRectsCoverExactlyTheExposedArea() {
		// Every exposed cell face appears in exactly one rectangle, for every mask.
		for (int mask = 0; mask < (1 << 12); mask += 37) {
			CarvedShape s = carver.shape(mask);
			for (Face face : Face.VALUES) {
				if ((s.carvedFaces() & face.bit()) == 0) {
					continue;
				}
				int expected = 0;
				for (int d = 0; d < 16; d++) {
					for (int u = 0; u < 16; u++) {
						for (int v = 0; v < 16; v++) {
							if (s.kept(face.index(u, v, d)) && (d == 0 || !s.kept(face.index(u, v, d - 1)))) {
								expected++;
							}
						}
					}
				}
				int covered = 0;
				for (Rect r : s.rects(face)) {
					covered += r.area();
				}
				assertEquals(expected, covered, "mask=" + mask + " face=" + face);
			}
		}
	}

	@Test
	void singleEdgeStepFaces() {
		CarvedShape s = carver.shape(mask(Edge.UP_NORTH));
		// UP: top face shrinks to z 2..15 (depth 0) plus two treads (depth 1 and 2), one cell deep each.
		List<Rect> up = List.of(s.rects(Face.UP));
		assertEquals(3, up.size());
		assertTrue(up.contains(new Rect(0, 0, 16, 14, 0)));
		assertTrue(up.contains(new Rect(0, 14, 16, 15, 1)));
		assertTrue(up.contains(new Rect(0, 15, 16, 16, 2)));
		// NORTH: face shrinks to y 0..13 plus two risers.
		List<Rect> north = List.of(s.rects(Face.NORTH));
		assertEquals(3, north.size());
		assertTrue(north.contains(new Rect(0, 0, 16, 14, 0)));
		assertTrue(north.contains(new Rect(0, 14, 16, 15, 1)));
		assertTrue(north.contains(new Rect(0, 15, 16, 16, 2)));
	}

	@Test
	void endCapAgainstPlainNeighbourIsTheCrossSection() {
		CarvedShape a = carver.shape(mask(Edge.UP_NORTH));
		List<Rect> caps = a.endCaps(Face.EAST, null);
		int area = caps.stream().mapToInt(Rect::area).sum();
		assertEquals(3, area);
		caps.forEach(r -> assertEquals(16, r.depth()));
	}

	@Test
	void noEndCapWhenNeighbourCutLinesUp() {
		CarvedShape a = carver.shape(mask(Edge.UP_NORTH));
		CarvedShape c = carver.shape(mask(Edge.UP_NORTH));
		assertTrue(a.endCaps(Face.EAST, c).isEmpty());
		assertTrue(a.endCaps(Face.WEST, c).isEmpty());
	}

	@Test
	void noEndCapOnSidesWithoutACut() {
		CarvedShape a = carver.shape(mask(Edge.UP_NORTH));
		assertTrue(a.endCaps(Face.SOUTH, null).isEmpty());
		assertTrue(a.endCaps(Face.DOWN, null).isEmpty());
	}
}
