package com.jamesdebono.roundededges.carve;

/**
 * Everything about a block's neighbourhood that affects its carved shape, packed into a long:
 *
 * <ul>
 * <li>bits 0-11: the edge mask (see {@link EdgeMask});</li>
 * <li>bits 12-35: inside corners, 3 bits per {@link Vertex}. Bit k is set when the edge running out of that corner
 * along axis k is not cut in this block but is cut in the carvable neighbour beyond the corner, so that cut ends
 * here. Only recorded for corners where at least two cuts end together.</li>
 * <li>bit 36: the block is 15/16 tall (paths, farmland); cuts on its top edges start from its real top.</li>
 * </ul>
 *
 * <p>Everything is read from the block's 3x3x3 neighbourhood. 0 means "draw the plain cube".
 */
public final class CarveKey {
	public static final int EMPTY = 0;
	public static final int CARVABLE = 1;
	public static final int SOLID = 2;

	private static final int CORNER_SHIFT = 12;
	/** Bit 36: the block is 15/16 tall. */
	private static final long SHORT = 1L << 36;

	/** What kind of cell sits at the given offset from the block. */
	@FunctionalInterface
	public interface Cells {
		int kind(int dx, int dy, int dz);

		/**
		 * For carvable blocks whose cuts don't depend on their surroundings (logs always round the edges along their
		 * axis): that fixed edge mask. -1 for blocks that follow the exposure rule.
		 */
		default int fixedMask(int dx, int dy, int dz) {
			return -1;
		}

		/** Whether the carvable block here is 15/16 tall (paths, farmland) rather than a full cube. */
		default boolean isShort(int dx, int dy, int dz) {
			return false;
		}
	}

	private CarveKey() {
	}

	public static long compute(Cells cells) {
		int fixed = cells.fixedMask(0, 0, 0);
		int mask = fixed >= 0 ? fixed : EdgeMask.compute((dx, dy, dz) -> cells.kind(dx, dy, dz) == EMPTY);
		long corners = 0;
		for (Vertex vertex : Vertex.VALUES) {
			Face fy = vertex.face(0);
			Face fz = vertex.face(1);
			Face fx = vertex.face(2);
			// A rule-based cut ending here needs the cell diagonally out from this corner to be empty.
			boolean cornerOpen = cells.kind(fx.dx, fy.dy, fz.dz) == EMPTY;
			int bits = 0;
			for (int axis = 0; axis < 3; axis++) {
				Edge edge = vertex.edge(axis);
				if ((mask & edge.bit()) != 0) {
					continue;
				}
				Face beyond = vertex.face(axis);
				if (cells.kind(beyond.dx, beyond.dy, beyond.dz) != CARVABLE) {
					continue;
				}
				int beyondFixed = cells.fixedMask(beyond.dx, beyond.dy, beyond.dz);
				boolean cut = beyondFixed >= 0 ? (beyondFixed & edge.bit()) != 0 : cornerOpen && isCut(cells, beyond, edge);
				if (cut) {
					bits |= 1 << axis;
				}
			}
			if (Integer.bitCount(bits) >= 2) {
				corners |= (long) bits << (CORNER_SHIFT + 3 * vertex.ordinal());
			}
		}
		// A short block always needs its own shape, even uncut, so neighbours see its lower top.
		return mask | corners | (cells.isShort(0, 0, 0) ? SHORT : 0);
	}

	public static int mask(long key) {
		return (int) (key & EdgeMask.ALL);
	}

	public static boolean isShort(long key) {
		return (key & SHORT) != 0;
	}

	/** Bits (by axis) of the cuts that end together at this corner; 0 if none are recorded. */
	public static int insideCorner(long key, Vertex vertex) {
		return (int) (key >>> (CORNER_SHIFT + 3 * vertex.ordinal())) & 7;
	}

	/** Whether the neighbour at {@code at} has {@code edge} cut: its 3 cells sharing the edge line are empty. */
	private static boolean isCut(Cells cells, Face at, Edge edge) {
		int x = at.dx;
		int y = at.dy;
		int z = at.dz;
		return cells.kind(x + edge.a.dx, y + edge.a.dy, z + edge.a.dz) == EMPTY
				&& cells.kind(x + edge.b.dx, y + edge.b.dy, z + edge.b.dz) == EMPTY
				&& cells.kind(x + edge.a.dx + edge.b.dx, y + edge.a.dy + edge.b.dy, z + edge.a.dz + edge.b.dz) == EMPTY;
	}
}
