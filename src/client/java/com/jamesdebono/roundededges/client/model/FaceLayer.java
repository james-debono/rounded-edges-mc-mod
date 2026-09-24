package com.jamesdebono.roundededges.client.model;

import org.joml.Vector3fc;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;

import com.jamesdebono.roundededges.carve.Face;

/**
 * One texture layer of a block face (e.g. grass has a dirt side plus a tinted overlay), with the mapping from
 * face-local position to texture coordinates taken from the original quad. Copying that mapping keeps the model's
 * random rotation/mirroring on carved faces, and step faces show the texture that would be at their position.
 */
record FaceLayer(Material.Baked material, int tintIndex, float su, float sv, float s0, float tu, float tv, float t0) {
	/** Builds a layer from an original full-face quad; {@code null} if its texture mapping can't be recovered. */
	static FaceLayer of(BakedQuad quad, Face face) {
		BakedQuad.MaterialInfo info = quad.materialInfo();
		TextureAtlasSprite sprite = info.sprite();
		float[] u = new float[3];
		float[] v = new float[3];
		float[] s = new float[3];
		float[] t = new float[3];
		for (int i = 0; i < 3; i++) {
			Vector3fc p = quad.position(i);
			u[i] = face.u(p.x(), p.y(), p.z());
			v[i] = face.v(p.x(), p.y(), p.z());
			long uv = quad.packedUV(i);
			s[i] = (UVPair.unpackU(uv) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
			t[i] = (UVPair.unpackV(uv) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
		}
		// Solve the affine map (u, v) -> (s, t) through three of the quad's corners.
		float du1 = u[1] - u[0];
		float dv1 = v[1] - v[0];
		float du2 = u[2] - u[0];
		float dv2 = v[2] - v[0];
		float det = du1 * dv2 - dv1 * du2;
		if (Math.abs(det) < 1e-6f) {
			return null;
		}
		float su = ((s[1] - s[0]) * dv2 - dv1 * (s[2] - s[0])) / det;
		float sv = (du1 * (s[2] - s[0]) - (s[1] - s[0]) * du2) / det;
		float tu = ((t[1] - t[0]) * dv2 - dv1 * (t[2] - t[0])) / det;
		float tv = (du1 * (t[2] - t[0]) - (t[1] - t[0]) * du2) / det;
		return new FaceLayer(new Material.Baked(sprite, false), info.isTinted() ? info.tintIndex() : -1,
				su, sv, s[0] - su * u[0] - sv * v[0], tu, tv, t[0] - tu * u[0] - tv * v[0]);
	}

	/** Normalised texture coordinates (0..1 across the sprite) for a face-local position. */
	float s(float u, float v) {
		return su * u + sv * v + s0;
	}

	float t(float u, float v) {
		return tu * u + tv * v + t0;
	}
}
