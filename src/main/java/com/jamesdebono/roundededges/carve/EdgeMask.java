package com.jamesdebono.roundededges.carve;

/**
 * The exposure rule: an edge is cut iff all 3 cells sharing its edge line with the block (the two face neighbours and
 * the diagonal between them) are empty. The result is a 12-bit mask indexed by {@link Edge#ordinal()}.
 */
public final class EdgeMask {
	public static final int ALL = (1 << 12) - 1;

	/** Answers whether the cell at the given offset from the block counts as empty. */
	@FunctionalInterface
	public interface EmptyTest {
		boolean isEmpty(int dx, int dy, int dz);
	}

	private EdgeMask() {
	}

	public static int compute(EmptyTest empty) {
		return compute(empty, ALL);
	}

	/** Like {@link #compute(EmptyTest)} but only tests the edges set in {@code candidates}. */
	public static int compute(EmptyTest empty, int candidates) {
		int emptyFaces = 0;
		for (Face face : Face.VALUES) {
			if (empty.isEmpty(face.dx, face.dy, face.dz)) {
				emptyFaces |= face.bit();
			}
		}
		// Every edge needs both of its face neighbours empty, so most buried blocks stop here.
		if (Integer.bitCount(emptyFaces) < 2) {
			return 0;
		}
		int mask = 0;
		for (Edge edge : Edge.VALUES) {
			if ((candidates & edge.bit()) == 0 || (emptyFaces & edge.a.bit()) == 0 || (emptyFaces & edge.b.bit()) == 0) {
				continue;
			}
			if (empty.isEmpty(edge.a.dx + edge.b.dx, edge.a.dy + edge.b.dy, edge.a.dz + edge.b.dz)) {
				mask |= edge.bit();
			}
		}
		return mask;
	}

	/** Mask of the 4 edges running along the given axis ({@link Face#axis()} numbering). */
	public static int alongAxis(int axis) {
		int mask = 0;
		for (Edge edge : Edge.VALUES) {
			if (edge.axis() == axis) {
				mask |= edge.bit();
			}
		}
		return mask;
	}
}
