package com.jamesdebono.roundededges.carve;

/**
 * How to fill the corner of a block where two or three cuts from neighbouring blocks end together (an inside
 * corner). Without a fill, the corner of the uncut block pokes out into the space where the cuts meet.
 *
 * <p>Depths below are sub-voxels from the three faces that meet at the corner.
 */
public enum InsideCorners {
	/** Leave the corner as is. */
	OFF("off (sharp corner)"),
	/** Remove the single corner cell (option 1A/1B). */
	SINGLE_TEXEL("1A/1B: single texel"),
	/** Remove cells with {@code dx + dy + dz < reach}: a small stepped pyramid (option 2A/2B). */
	PYRAMID("2A/2B: 4-texel pyramid"),
	/**
	 * Continue each cut into the block and remove where they all overlap (option 2A with three cuts, 2C with two).
	 * Every cut then meets the corner flush, mirroring how outside corners combine cuts by union.
	 */
	MITRE("2A/2C: continue cuts (mitre)");

	public static final InsideCorners[] VALUES = values();

	private final String label;

	InsideCorners(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public InsideCorners next() {
		return VALUES[(ordinal() + 1) % VALUES.length];
	}
}
