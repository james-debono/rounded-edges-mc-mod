package com.jamesdebono.roundededges.carve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfileTest {
	@Test
	void lockedProfileIsReach2Staircase() {
		Profile p = Profile.STAIR_2;
		assertEquals(2, p.reach());
		for (int a = 0; a < 16; a++) {
			for (int b = 0; b < 16; b++) {
				assertEquals(a + b < 2, p.removed(a, b), "a=" + a + " b=" + b);
			}
		}
	}

	@Test
	void staircaseMatchesFormula() {
		Profile p = Profile.staircase(4);
		for (int a = 0; a < 16; a++) {
			for (int b = 0; b < 16; b++) {
				assertEquals(a + b < 4, p.removed(a, b));
			}
		}
	}

	@Test
	void rejectsAsymmetric() {
		// Rows 2,2 = a 2x2 square notch is symmetric; rows 3,1 is not (cell (0,2) removed but (2,0) kept).
		new Profile(2, 2);
		assertThrows(IllegalArgumentException.class, () -> new Profile(3, 1));
	}

	@Test
	void rejectsNonMonotone() {
		assertThrows(IllegalArgumentException.class, () -> new Profile(1, 2));
	}

	@Test
	void rejectsTooDeep() {
		assertThrows(IllegalArgumentException.class, () -> Profile.staircase(9));
	}

	@Test
	void roundOverShapesAreAccepted() {
		// Quarter-circle round-over, radius 6: rows 4,2,1,1.
		Profile p = new Profile(4, 2, 1, 1);
		assertTrue(p.removed(3, 0));
		assertFalse(p.removed(3, 1));
	}
}
