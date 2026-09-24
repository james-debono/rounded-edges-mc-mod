package com.jamesdebono.roundededges.carve;

/**
 * The six faces of a block, in the same order as Minecraft's {@code Direction} enum so ordinals can be shared.
 *
 * <p>Sub-voxel coordinates run 0..15 on each axis: x west to east, y down to up, z north to south.
 *
 * <p>Each face also defines a face-local frame {@code (u, v, d)}: {@code u, v} span the face and match the
 * {@code left/bottom/right/top} convention of Fabric's {@code QuadEmitter.square()}, and {@code d} is the depth in
 * sub-voxels from this face (0 = the layer touching it). A rectangle of cells in this frame can be handed straight
 * to {@code square()} after dividing by 16.
 */
public enum Face {
	DOWN(0, -1, 0),
	UP(0, 1, 0),
	NORTH(0, 0, -1),
	SOUTH(0, 0, 1),
	WEST(-1, 0, 0),
	EAST(1, 0, 0);

	public static final Face[] VALUES = values();

	public final int dx;
	public final int dy;
	public final int dz;

	Face(int dx, int dy, int dz) {
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public Face opposite() {
		return VALUES[ordinal() ^ 1];
	}

	/** 0 = Y (down/up), 1 = Z (north/south), 2 = X (west/east). */
	public int axis() {
		return ordinal() >> 1;
	}

	/** True for UP, SOUTH and EAST. */
	public boolean positive() {
		return (ordinal() & 1) == 1;
	}

	/** Depth of sub-voxel (x, y, z) measured from this face; 0 = the layer touching it. */
	public int depthOf(int x, int y, int z) {
		int c = switch (axis()) {
			case 0 -> y;
			case 1 -> z;
			default -> x;
		};
		return positive() ? 15 - c : c;
	}

	/** The direction in which face-local u increases. */
	public Face uDir() {
		return switch (this) {
			case DOWN, UP, SOUTH -> EAST;
			case NORTH -> WEST;
			case WEST -> SOUTH;
			case EAST -> NORTH;
		};
	}

	/** The direction in which face-local v increases. */
	public Face vDir() {
		return switch (this) {
			case DOWN -> SOUTH;
			case UP -> NORTH;
			default -> UP;
		};
	}

	/** Face-local u (0..1) of a point given in block units; depth along the face normal is ignored. */
	public float u(float x, float y, float z) {
		return uDir().along(x, y, z);
	}

	/** Face-local v (0..1) of a point given in block units. */
	public float v(float x, float y, float z) {
		return vDir().along(x, y, z);
	}

	/** Position of a point along this direction, 0 at the opposite face and 1 at this one. */
	private float along(float x, float y, float z) {
		float c = switch (axis()) {
			case 0 -> y;
			case 1 -> z;
			default -> x;
		};
		return positive() ? c : 1 - c;
	}

	/** Packed sub-voxel index of face-local cell (u, v) at depth d. See {@link Voxels#index}. */
	public int index(int u, int v, int d) {
		return switch (this) {
			case DOWN -> Voxels.index(u, d, v);
			case UP -> Voxels.index(u, 15 - d, 15 - v);
			case NORTH -> Voxels.index(15 - u, v, d);
			case SOUTH -> Voxels.index(u, v, 15 - d);
			case WEST -> Voxels.index(d, v, u);
			case EAST -> Voxels.index(15 - d, v, 15 - u);
		};
	}
}
