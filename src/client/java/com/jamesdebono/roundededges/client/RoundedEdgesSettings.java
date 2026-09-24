package com.jamesdebono.roundededges.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import com.jamesdebono.roundededges.carve.Carver;
import com.jamesdebono.roundededges.carve.Profile;

/** Runtime switches for the prototype. Read from chunk-build threads, so the fields are volatile. */
public final class RoundedEdgesSettings {
	public static final Carver CARVER = new Carver(Profile.STAIR_2);

	/** Carve distances (blocks) cycled in-game; 0 = no limit. */
	public static final int[] CARVE_DISTANCES = {32, 48, 64, 96, 128, 0};

	public static volatile boolean enabled = true;
	public static volatile boolean debugTint = false;
	/**
	 * Only chunk sections within this many blocks of the camera are carved (0 = all). The steps are a sixteenth of a
	 * block, about a pixel at 64 blocks, so carving further away costs geometry for detail you can't see.
	 */
	public static volatile int carveDistance = 64;

	/**
	 * Where the carve distance is measured from (packed {@link BlockPos}): the camera position, updated only after the
	 * camera has moved a few blocks so bobbing across a section boundary doesn't keep re-meshing. {@link #UNSET} until
	 * the first tick.
	 */
	static final long UNSET = Long.MIN_VALUE;
	static volatile long anchor = UNSET;

	private RoundedEdgesSettings() {
	}

	/** Whether the section containing this block is carved. Decided per section, so a section is all or nothing. */
	public static boolean isNear(int x, int y, int z) {
		return isNear(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z), anchor);
	}

	static boolean isNear(int sx, int sy, int sz, long from) {
		int limit = carveDistance;
		if (limit <= 0) {
			return true;
		}
		if (from == UNSET) {
			return false; // camera not known yet; sections get rebuilt carved once it is
		}
		// Distance from the section's centre.
		long dx = sx * 16L + 8 - BlockPos.getX(from);
		long dy = sy * 16L + 8 - BlockPos.getY(from);
		long dz = sz * 16L + 8 - BlockPos.getZ(from);
		return dx * dx + dy * dy + dz * dz <= (long) limit * limit;
	}
}
