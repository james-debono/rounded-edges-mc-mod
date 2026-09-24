package com.jamesdebono.roundededges.carve;

import java.util.List;

/** Helpers for a 16x16x16 sub-voxel bitset stored as 64 longs, and for greedy-merging 16x16 face grids. */
public final class Voxels {
	public static final int SIZE = 16;
	public static final int COUNT = SIZE * SIZE * SIZE;

	private Voxels() {
	}

	public static int index(int x, int y, int z) {
		return (y << 8) | (z << 4) | x;
	}

	public static long[] full() {
		long[] bits = new long[COUNT / 64];
		java.util.Arrays.fill(bits, -1L);
		return bits;
	}

	public static boolean get(long[] bits, int index) {
		return (bits[index >>> 6] & (1L << index)) != 0;
	}

	public static void clear(long[] bits, int index) {
		bits[index >>> 6] &= ~(1L << index);
	}

	public static int count(long[] bits) {
		int n = 0;
		for (long word : bits) {
			n += Long.bitCount(word);
		}
		return n;
	}

	/**
	 * Greedily merges set cells of a 16x16 grid into rectangles and appends them to {@code out}. {@code rows[v]} holds
	 * the cells of row v as bits (bit u = cell u). The array is consumed (cleared) in the process.
	 */
	public static void greedy(int[] rows, int depth, List<Rect> out) {
		for (int v = 0; v < SIZE; v++) {
			while (rows[v] != 0) {
				int u0 = Integer.numberOfTrailingZeros(rows[v]);
				int run = Integer.numberOfTrailingZeros(~(rows[v] >>> u0));
				int runMask = ((1 << run) - 1) << u0;
				int v1 = v + 1;
				while (v1 < SIZE && (rows[v1] & runMask) == runMask) {
					v1++;
				}
				for (int vv = v; vv < v1; vv++) {
					rows[vv] &= ~runMask;
				}
				out.add(new Rect(u0, v, u0 + run, v1, depth));
			}
		}
	}
}
