package com.jamesdebono.roundededges.carve;

/**
 * The 8 corners of a block. Index bits: 1 = east side, 2 = up side, 4 = south side.
 *
 * <p>Each corner has three faces meeting at it (one per axis) and three edges running out of it.
 */
public enum Vertex {
	DOWN_NORTH_WEST, DOWN_NORTH_EAST, UP_NORTH_WEST, UP_NORTH_EAST,
	DOWN_SOUTH_WEST, DOWN_SOUTH_EAST, UP_SOUTH_WEST, UP_SOUTH_EAST;

	public static final Vertex[] VALUES = values();

	/** The corner's face on each axis, indexed by {@link Face#axis()} (0 = y, 1 = z, 2 = x). */
	private final Face[] faces = new Face[3];
	/** The edge running out of this corner along each axis, indexed like {@link #faces}. */
	private final Edge[] edges = new Edge[3];

	static {
		for (Vertex vertex : VALUES) {
			int i = vertex.ordinal();
			vertex.faces[0] = (i & 2) != 0 ? Face.UP : Face.DOWN;
			vertex.faces[1] = (i & 4) != 0 ? Face.SOUTH : Face.NORTH;
			vertex.faces[2] = (i & 1) != 0 ? Face.EAST : Face.WEST;
			for (int axis = 0; axis < 3; axis++) {
				vertex.edges[axis] = Edge.between(vertex.faces[(axis + 1) % 3], vertex.faces[(axis + 2) % 3]);
			}
		}
	}

	/** This corner's face on the given axis. */
	public Face face(int axis) {
		return faces[axis];
	}

	/** The edge that runs out of this corner along the given axis. */
	public Edge edge(int axis) {
		return edges[axis];
	}
}
