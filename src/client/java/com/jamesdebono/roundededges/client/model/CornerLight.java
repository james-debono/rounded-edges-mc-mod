package com.jamesdebono.roundededges.client.model;

import net.minecraft.util.LightCoordsUtil;

/**
 * Smooth lighting for faces inside a carved block. Cut faces sit inside the block's own cell, where the renderer only
 * sees the block's own (dark) light. Every cut face is within a couple of sixteenths of a block corner, and the open
 * space it looks into is around that corner. So each of the block's 8 corners takes the average light of the open cells
 * touching it, and faces interpolate between corners by position.
 */
final class CornerLight {
	/** Per corner (index bits: 1 = east, 2 = up, 4 = south): smooth block light, sky light and AO shade. */
	private final float[] block = new float[8];
	private final float[] sky = new float[8];
	private final float[] shade = new float[8];

	private CornerLight() {
	}

	static CornerLight of(Cells cells, boolean smooth) {
		CornerLight result = new CornerLight();
		if (!smooth) {
			// Flat lighting: the brightest open cell around the block, everywhere.
			int light = 0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if ((dx | dy | dz) != 0 && cells.shade(dx, dy, dz) >= 1f) {
							light = LightCoordsUtil.max(light, cells.light(dx, dy, dz));
						}
					}
				}
			}
			java.util.Arrays.fill(result.block, LightCoordsUtil.smoothBlock(light));
			java.util.Arrays.fill(result.sky, LightCoordsUtil.smoothSky(light));
			java.util.Arrays.fill(result.shade, 1f);
			return result;
		}
		for (int corner = 0; corner < 8; corner++) {
			int sx = (corner & 1) != 0 ? 1 : -1;
			int sy = (corner & 2) != 0 ? 1 : -1;
			int sz = (corner & 4) != 0 ? 1 : -1;
			int open = 0;
			float blockSum = 0;
			float skySum = 0;
			for (int i = 1; i < 8; i++) { // the 7 other cells touching this corner
				int dx = (i & 1) != 0 ? sx : 0;
				int dy = (i & 2) != 0 ? sy : 0;
				int dz = (i & 4) != 0 ? sz : 0;
				if (cells.shade(dx, dy, dz) < 1f) {
					continue; // opaque: light doesn't come from there
				}
				int light = cells.light(dx, dy, dz);
				blockSum += LightCoordsUtil.smoothBlock(light);
				skySum += LightCoordsUtil.smoothSky(light);
				open++;
			}
			if (open > 0) {
				result.block[corner] = blockSum / open;
				result.sky[corner] = skySum / open;
			}
			// A corner on a flat open face has 4 open cells; fewer means a crevice, which vanilla AO darkens.
			result.shade[corner] = Math.min(1f, 0.2f + 0.8f * open / 4f);
		}
		return result;
	}

	/** Packed lightmap coordinates at a position in block units (0..1 on each axis). */
	int lightAt(float x, float y, float z) {
		return LightCoordsUtil.smoothPack(Math.round(interpolate(block, x, y, z)), Math.round(interpolate(sky, x, y, z)));
	}

	float shadeAt(float x, float y, float z) {
		return interpolate(shade, x, y, z);
	}

	private static float interpolate(float[] values, float x, float y, float z) {
		float result = 0;
		for (int corner = 0; corner < 8; corner++) {
			float w = ((corner & 1) != 0 ? x : 1 - x) * ((corner & 2) != 0 ? y : 1 - y) * ((corner & 4) != 0 ? z : 1 - z);
			result += w * values[corner];
		}
		return result;
	}
}
