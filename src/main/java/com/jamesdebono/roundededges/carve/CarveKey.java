package com.jamesdebono.roundededges.carve;

/**
 * Everything about a block's neighbourhood that affects its carved shape, packed into a long:
 *
 * <ul>
 * <li>bits 0-11: the edge mask (see {@link EdgeMask});</li>
 * <li>bits 12-35: inside corners, 3 bits per {@link Vertex}. Bit k is set when the edge running out of that corner
 * along axis k is not cut in this block but is cut in the carvable neighbour beyond the corner, so that cut ends
 * here. Only recorded for corners where at least two cuts end together.</li>
 * </ul>
 *
 * <p>Everything is read from the block's 3x3x3 neighbourhood. 0 means "draw the plain cube".
 */
public final class CarveKey {
	public static final int EMPTY = 0;
	public static final int CARVABLE = 1;
	public static final int SOLID = 2;

	private static final int CORNER_SHIFT = 12;

	/** What kind of cell sits at the given offset from the block. */
	@FunctionalInterface
	public interface Cells {
		int kind(int dx, int dy, int dz);
	}

	private CarveKey() {
	}

	public static long compute(Cells cells) {
		int mask = EdgeMask.compute((dx, dy, dz) -> cells.kind(dx, dy, dz) == EMPTY);
		long corners = 0;
		for (Vertex vertex : Vertex.VALUES) {
			Face fy = vertex.face(0);
			Face fz = vertex.face(1);
			Face fx = vertex.face(2);
			// Every cut ending at this corner has the cell diagonally out from the corner as one of its empty cells.
			if (cells.kind(fx.dx, fy.dy, fz.dz) != EMPTY) {
				continue;
			}
			int bits = 0;
			for (int axis = 0; axis < 3; axis++) {
				Edge edge = vertex.edge(axis);
				if ((mask & edge.bit()) != 0) {
					continue;
				}
				Face beyond = vertex.face(axis);
				if (cells.kind(beyond.dx, beyond.dy, beyond.dz) == CARVABLE && isCut(cells, beyond, edge)) {
					bits |= 1 << axis;
				}
			}
			if (Integer.bitCount(bits) >= 2) {
				corners |= (long) bits << (CORNER_SHIFT + 3 * vertex.ordinal());
			}
		}
		return mask | corners;
	}

	public static int mask(long key) {
		return (int) (key & EdgeMask.ALL);
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
