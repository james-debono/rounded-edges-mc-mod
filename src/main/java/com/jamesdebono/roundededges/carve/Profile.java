package com.jamesdebono.roundededges.carve;

import java.util.Arrays;

/**
 * Cross-section of the cut along one edge. {@code a} and {@code b} are sub-voxel depths from the edge's two faces; a
 * cell is removed iff {@code b < widths[a]}.
 *
 * <p>The profile must be <b>monotone</b> (widths never increase with depth) and <b>symmetric</b>
 * ({@code removed(a, b) == removed(b, a)}). Together these guarantee that the union of profiles extruded along
 * adjacent edges mitres cleanly with the steps lining up on the diagonal.
 */
public final class Profile {
	/** Locked design profile: 45-degree staircase, reach 2, 1-texel steps ({@code removed iff a + b < 2}). */
	public static final Profile STAIR_2 = staircase(2);

	/** Maximum reach, so cuts on opposite edges of a face can never meet. */
	public static final int MAX_REACH = 8;

	private final int[] widths;

	public Profile(int... widths) {
		this.widths = widths.clone();
		if (widths.length > MAX_REACH) {
			throw new IllegalArgumentException("Profile deeper than " + MAX_REACH + ": " + Arrays.toString(widths));
		}
		for (int i = 0; i < widths.length; i++) {
			if (widths[i] < 1 || widths[i] > MAX_REACH) {
				throw new IllegalArgumentException("Row width out of range 1.." + MAX_REACH + ": " + Arrays.toString(widths));
			}
			if (i > 0 && widths[i] > widths[i - 1]) {
				throw new IllegalArgumentException("Profile not monotone: " + Arrays.toString(widths));
			}
		}
		for (int a = 0; a < Voxels.SIZE; a++) {
			for (int b = 0; b < Voxels.SIZE; b++) {
				if (removed(a, b) != removed(b, a)) {
					throw new IllegalArgumentException("Profile not symmetric: " + Arrays.toString(widths));
				}
			}
		}
	}

	/** 45-degree staircase with 1-cell steps reaching {@code reach} cells into each face. */
	public static Profile staircase(int reach) {
		int[] widths = new int[reach];
		for (int i = 0; i < reach; i++) {
			widths[i] = reach - i;
		}
		return new Profile(widths);
	}

	public boolean removed(int a, int b) {
		return a < widths.length && b < widths[a];
	}

	public int reach() {
		return widths.length == 0 ? 0 : widths[0];
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Profile other && Arrays.equals(widths, other.widths);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(widths);
	}

	@Override
	public String toString() {
		return "Profile" + Arrays.toString(widths);
	}
}
