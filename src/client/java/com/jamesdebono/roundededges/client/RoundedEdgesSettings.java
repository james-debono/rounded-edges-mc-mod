package com.jamesdebono.roundededges.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import com.jamesdebono.roundededges.carve.Carver;
import com.jamesdebono.roundededges.carve.Profile;

/** Runtime switches for the prototype. Read from chunk-build threads, so the fields are volatile. */
public final class RoundedEdgesSettings {
	public static final Carver CARVER = new Carver(Profile.STAIR_2);

	/** Master switch and debug tint; no longer on keys, but the automated game test still uses them. */
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

	/**
	 * Leaves carve out to at most this far. Their noisy see-through texture hides the steps sooner, and every carved
	 * leaf adds see-through quads, which shaders draw twice (once for shadows).
	 */
	public static final int LEAVES_DISTANCE = 24;

	/**
	 * Whether blocks of this kind in the section containing this block are carved. Decided per section, so a section
	 * is all or nothing for each kind.
	 */
	public static boolean isNear(int x, int y, int z, boolean leaves) {
		return isNear(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z), anchor, leaves);
	}

	static boolean isNear(int sx, int sy, int sz, long from, boolean leaves) {
		int limit = carveDistance;
		if (leaves) {
			limit = limit <= 0 ? LEAVES_DISTANCE : Math.min(limit, LEAVES_DISTANCE);
		}
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
