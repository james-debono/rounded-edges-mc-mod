package com.jamesdebono.roundededges.carve;

import java.util.ArrayList;
import java.util.List;

/**
 * The carved geometry for one edge mask: the full 16x16x16 block minus the union of the profile extruded along every
 * cut edge, plus the merged face rectangles needed to draw it.
 *
 * <p>Instances are immutable and shared; get them from {@link Carver#shape(int)}.
 */
public final class CarvedShape {
	private static final Rect[] NO_RECTS = new Rect[0];

	private final long key;
	private final long[] kept;
	private final Rect[][] rects = new Rect[6][];
	private final int carvedFaces;
	private final int openBoundaries;

	/** Highest kept row: 15 for full blocks, 14 for 15/16-tall ones (paths, farmland). */
	private final int top;

	CarvedShape(Profile profile, InsideCorners insideCorners, long key) {
		this.key = key;
		this.kept = Voxels.full();
		this.top = CarveKey.isShort(key) ? 14 : 15;
		if (top < 15) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					Voxels.clear(kept, Voxels.index(x, 15, z));
				}
			}
		}
		int mask = CarveKey.mask(key);
		int reach = profile.reach();
		int[] c = new int[3]; // coordinates indexed by Face.axis(): 0 = y, 1 = z, 2 = x
		for (Edge edge : Edge.VALUES) {
			if ((mask & edge.bit()) == 0) {
				continue;
			}
			// Only cells within reach of both faces can be removed; walk just that strip along the edge.
			for (int a = 0; a < reach; a++) {
				for (int b = 0; b < reach; b++) {
					if (!profile.removed(a, b)) {
						continue;
					}
					c[edge.a.axis()] = coord(edge.a, a);
					c[edge.b.axis()] = coord(edge.b, b);
					for (int t = 0; t < 16; t++) {
						c[edge.axis()] = t;
						Voxels.clear(kept, Voxels.index(c[2], c[0], c[1]));
					}
				}
			}
		}

		for (Vertex vertex : Vertex.VALUES) {
			int ending = CarveKey.insideCorner(key, vertex);
			if (ending != 0 && insideCorners != InsideCorners.OFF) {
				fillInsideCorner(profile, insideCorners, vertex, ending);
			}
		}

		int carved = 0;
		int open = 0;
		for (Face face : Face.VALUES) {
			for (int i = 0; i < 256 && (open & face.bit()) == 0; i++) {
				if (!kept(face.index(i & 15, i >>> 4, 0))) {
					open |= face.bit();
				}
			}
			List<Rect> out = new ArrayList<>();
			int[] rows = new int[16];
			// A cell deeper than the reach can't have a removed cell in front of it (the profile is monotone), so
			// only the first reach + 1 layers can show this face (one more on top of a 15/16-tall block).
			for (int d = 0; d <= Math.min(reach + 15 - top, 15); d++) {
				for (int v = 0; v < 16; v++) {
					int row = 0;
					for (int u = 0; u < 16; u++) {
						// A cell shows this face if it is kept and the cell between it and the face is not.
						if (kept(face.index(u, v, d)) && (d == 0 || !kept(face.index(u, v, d - 1)))) {
							row |= 1 << u;
						}
					}
					rows[v] = row;
				}
				Voxels.greedy(rows, d, out);
			}
			if (out.size() == 1 && out.getFirst().isFullFace()) {
				rects[face.ordinal()] = NO_RECTS;
			} else {
				rects[face.ordinal()] = out.toArray(NO_RECTS);
				carved |= face.bit();
			}
		}
		this.carvedFaces = carved;
		this.openBoundaries = open;
	}

	/**
	 * Removes the corner of this block where cuts from neighbours end together. {@code ending} holds, by axis, the
	 * cuts that end here; each one arrives along this block's edge on that axis.
	 */
	private void fillInsideCorner(Profile profile, InsideCorners mode, Vertex vertex, int ending) {
		int reach = profile.reach();
		int[] depth = new int[3]; // sub-voxels in from the corner's face on each axis (0 = y, 1 = z, 2 = x)
		int[] c = new int[3];
		for (depth[0] = 0; depth[0] < reach; depth[0]++) {
			for (depth[1] = 0; depth[1] < reach; depth[1]++) {
				for (depth[2] = 0; depth[2] < reach; depth[2]++) {
					boolean remove = switch (mode) {
						case OFF -> false;
						case SINGLE_TEXEL -> depth[0] + depth[1] + depth[2] == 0;
						case PYRAMID -> depth[0] + depth[1] + depth[2] < reach;
						case MITRE -> {
							// Inside the continuation of every cut that ends here.
							boolean all = true;
							for (int axis = 0; axis < 3 && all; axis++) {
								if ((ending & (1 << axis)) != 0) {
									all = profile.removed(depth[(axis + 1) % 3], depth[(axis + 2) % 3]);
								}
							}
							yield all;
						}
					};
					if (remove) {
						for (int axis = 0; axis < 3; axis++) {
							c[axis] = coord(vertex.face(axis), depth[axis]);
						}
						Voxels.clear(kept, Voxels.index(c[2], c[0], c[1]));
					}
				}
			}
		}
	}

	/** Sub-voxel coordinate on the face's axis, {@code depth} cells in from that face (the top of a short block is lower). */
	private int coord(Face face, int depth) {
		if (!face.positive()) {
			return depth;
		}
		return (face == Face.UP ? top : 15) - depth;
	}

	public long key() {
		return key;
	}

	public int mask() {
		return CarveKey.mask(key);
	}

	/** Bits ({@link Face#bit()}) of faces where some cell on the block boundary has been removed. */
	public int openBoundaries() {
		return openBoundaries;
	}

	public boolean kept(int index) {
		return Voxels.get(kept, index);
	}

	public boolean kept(int x, int y, int z) {
		return kept(Voxels.index(x, y, z));
	}

	public int removedCount() {
		return Voxels.COUNT - Voxels.count(kept);
	}

	/**
	 * Bits ({@link Face#bit()}) of faces whose geometry differs from a plain cube face. The caller draws these from
	 * {@link #rects(Face)} and the rest from the original model.
	 */
	public int carvedFaces() {
		return carvedFaces;
	}

	/**
	 * Rectangles facing {@code face}, in its face-local frame. Depth 0 rectangles lie on the block boundary (and should
	 * be culled like normal faces); deeper ones are step faces inside the block. Empty for faces that are not carved.
	 */
	public Rect[] rects(Face face) {
		return rects[face.ordinal()];
	}

	/**
	 * The neighbour-face patch visible through this block's cut on its {@code side} boundary. The neighbour on that
	 * side hides its own face (it sees a full cube here), so this block draws the patch instead.
	 *
	 * @param neighbour the neighbour's carved shape, or {@code null} if it is a plain full block
	 * @return rectangles in the face-local frame of {@code side.opposite()} (facing back into this block), at depth 16
	 */
	public List<Rect> endCaps(Face side, CarvedShape neighbour) {
		Face into = side.opposite();
		int[] rows = new int[16];
		boolean any = false;
		for (int v = 0; v < 16; v++) {
			int row = 0;
			for (int u = 0; u < 16; u++) {
				// Our cell next to the boundary is at depth 15 from the far face; the neighbour's is at depth 0.
				if (!kept(into.index(u, v, 15)) && (neighbour == null || neighbour.kept(into.index(u, v, 0)))) {
					row |= 1 << u;
				}
			}
			rows[v] = row;
			any |= row != 0;
		}
		if (!any) {
			return List.of();
		}
		List<Rect> out = new ArrayList<>();
		Voxels.greedy(rows, 16, out);
		return out;
	}
}
