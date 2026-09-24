package com.jamesdebono.roundededges.carve;

/**
 * A rectangle of cells in a {@link Face}'s face-local frame, in sixteenths of a block. Covers cells
 * {@code u0 <= u < u1}, {@code v0 <= v < v1}; {@code depth} is the distance of the quad's plane from the face.
 */
public record Rect(int u0, int v0, int u1, int v1, int depth) {
	public int area() {
		return (u1 - u0) * (v1 - v0);
	}

	public boolean isFullFace() {
		return u0 == 0 && v0 == 0 && u1 == 16 && v1 == 16 && depth == 0;
	}
}
