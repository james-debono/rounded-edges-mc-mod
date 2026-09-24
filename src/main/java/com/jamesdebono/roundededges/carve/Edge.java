package com.jamesdebono.roundededges.carve;

/** The 12 edges of a block, each named by the two faces it borders. Ordinals are the bits of an edge mask. */
public enum Edge {
	// along X
	UP_NORTH(Face.UP, Face.NORTH),
	UP_SOUTH(Face.UP, Face.SOUTH),
	DOWN_NORTH(Face.DOWN, Face.NORTH),
	DOWN_SOUTH(Face.DOWN, Face.SOUTH),
	// along Y
	NORTH_WEST(Face.NORTH, Face.WEST),
	NORTH_EAST(Face.NORTH, Face.EAST),
	SOUTH_WEST(Face.SOUTH, Face.WEST),
	SOUTH_EAST(Face.SOUTH, Face.EAST),
	// along Z
	UP_WEST(Face.UP, Face.WEST),
	UP_EAST(Face.UP, Face.EAST),
	DOWN_WEST(Face.DOWN, Face.WEST),
	DOWN_EAST(Face.DOWN, Face.EAST);

	public static final Edge[] VALUES = values();

	public final Face a;
	public final Face b;

	Edge(Face a, Face b) {
		this.a = a;
		this.b = b;
	}

	public int bit() {
		return 1 << ordinal();
	}

	/** The axis the edge runs along, using {@link Face#axis()} numbering. */
	public int axis() {
		return 3 - a.axis() - b.axis();
	}

	/** The edge between two perpendicular faces, in either order. */
	public static Edge between(Face f1, Face f2) {
		for (Edge edge : VALUES) {
			if ((edge.a == f1 && edge.b == f2) || (edge.a == f2 && edge.b == f1)) {
				return edge;
			}
		}
		throw new IllegalArgumentException("No edge between " + f1 + " and " + f2);
	}

	public boolean removes(Profile profile, int x, int y, int z) {
		return profile.removed(a.depthOf(x, y, z), b.depthOf(x, y, z));
	}
}
