package com.jamesdebono.roundededges.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;

import com.jamesdebono.roundededges.carve.CarveKey;
import com.jamesdebono.roundededges.carve.CarvedShape;
import com.jamesdebono.roundededges.carve.Carver;
import com.jamesdebono.roundededges.carve.Face;
import com.jamesdebono.roundededges.carve.Rect;
import com.jamesdebono.roundededges.client.CarvableBlocks;
import com.jamesdebono.roundededges.client.CarveDistance;
import com.jamesdebono.roundededges.client.RoundedEdgesSettings;
import com.jamesdebono.roundededges.client.debug.CarveStats;

/**
 * Wraps a full-cube block model and carves the stepped chamfer into its exposed edges at chunk-build time.
 *
 * <p>Everything this block needs is inside its 3x3x3 neighbourhood, so vanilla's rebuild-on-neighbour-change is
 * enough to keep it up to date.
 */
public class ChamferedModel extends WrapperBlockStateModel {
	private static final Direction[] DIRECTIONS = Direction.values(); // same order as Face
	private static final int STEP_TINT = 0xFF8080FF;
	private static final int END_CAP_TINT = 0xFFFF7070;
	private static final int WHITE = -1;

	private final Carver carver;
	/** Texture layer for each original quad. Lives on the model, so a resource reload starts afresh. */
	private final Map<BakedQuad, Optional<FaceLayer>> layers = new ConcurrentHashMap<>();

	public ChamferedModel(BlockStateModel wrapped, Carver carver) {
		super(wrapped);
		this.carver = carver;
	}

	/** Blocks that get carved; see {@link CarvableBlocks}. */
	public static boolean isCarvable(BlockState state) {
		return CarvableBlocks.contains(state);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		// Outside chunk builds (pistons, falling blocks, entity renderers) the level may be empty or the position
		// only approximate, so the neighbourhood can't be trusted: draw the plain cube.
		if (!RoundedEdgesSettings.enabled || level.getBlockState(pos) != state) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}

		long start = System.nanoTime();
		Cells cells = Cells.around(level, pos);
		long key = CarveKey.compute(cells);
		if (key != 0 || CarvableBlocks.fixedMask(state) >= 0) {
			// Remember this section has carving, so it's re-meshed when it crosses the carve distance.
			CarveDistance.noteCarvable(pos);
		}
		if (key == 0 || !cells.isNear(state, 0, 0, 0)) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}

		CarvedShape shape = carver.shape(key);
		int carvedFaces = shape.carvedFaces();
		boolean tint = RoundedEdgesSettings.debugTint;

		// 1. The original model, minus the faces we redraw.
		emitter.pushTransform(quad -> {
			Direction face = quad.nominalFace();
			return face == null || (carvedFaces & (1 << face.ordinal())) == 0;
		});
		super.emitQuads(emitter, level, pos, state, random, cullTest);
		emitter.popTransform();

		// 2. Carved faces: shrunken boundary faces (depth 0, lit and culled like normal faces) and step faces inside
		// the block. Textures follow the original face, including the random variant picked for this position.
		List<BlockStateModelPart> parts = partsAt(wrapped, state, pos);
		CornerLight light = CornerLight.of(cells, Minecraft.getInstance().options.ambientOcclusion().get());
		boolean emissive = state.emissiveRendering(); // e.g. magma renders full-bright
		boolean cutoutLeaves = Minecraft.getInstance().options.cutoutLeaves().get();
		boolean opaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state); // leaves drawn solid when cutout is off
		// A 15/16-tall block's own top sits one row down; it's still an ordinary face, so the renderer lights it.
		int topDepth = CarveKey.isShort(key) ? 1 : 0;
		for (Face face : Face.VALUES) {
			if ((carvedFaces & face.bit()) == 0) {
				continue;
			}
			Direction dir = DIRECTIONS[face.ordinal()];
			List<FaceLayer> faceLayers = layersOf(parts, face, particleMaterialLayer(wrapped));
			int surface = face == Face.UP ? topDepth : 0;
			for (Rect rect : shape.rects(face)) {
				if (rect.depth() == 0 && cullTest.test(dir)) {
					continue;
				}
				boolean step = rect.depth() != surface;
				for (FaceLayer layer : faceLayers) {
					emitRect(emitter, face, rect, layer, layer.tintIndex(), step ? light : null,
							step && tint ? STEP_TINT : WHITE, emissive, opaque);
				}
			}
		}

		// 3. Neighbour patches: where our cut opens onto a neighbour that hides its face against us (it sees a full
		// cube here), draw the hidden part of that face ourselves: the neighbour's own face quads, clipped to the cut.
		// Covers cuts ending against a block whose edge isn't cut, and slabs, fences, glass etc. beside the cut.
		boolean hadEndCaps = false;
		for (Face side : Face.VALUES) {
			if ((shape.openBoundaries() & side.bit()) == 0) {
				continue;
			}
			BlockState neighbour = cells.state(side.dx, side.dy, side.dz);
			Face into = side.opposite();
			Direction intoDir = DIRECTIONS[into.ordinal()];
			if (neighbour.getRenderShape() != RenderShape.MODEL || Block.shouldRenderFace(neighbour, state, intoDir)) {
				continue; // no model face there, or the neighbour draws it itself
			}
			boolean neighbourCarved = cells.kind(side.dx, side.dy, side.dz) == CarveKey.CARVABLE;
			CarvedShape neighbourShape = neighbourCarved ? carver.shape(CarveKey.compute(cells.from(side))) : null;
			List<Rect> caps = shape.endCaps(side, neighbourShape);
			if (caps.isEmpty()) {
				continue;
			}
			boolean neighbourOpaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, neighbour);
			boolean neighbourEmissive = neighbour.emissiveRendering();
			BlockPos neighbourPos = cells.pos(side.dx, side.dy, side.dz).immutable();
			BlockStateModel neighbourModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(neighbour);
			for (BlockStateModelPart part : partsAt(neighbourModel, neighbour, neighbourPos)) {
				for (BakedQuad quad : part.getQuads(intoDir)) {
					FaceLayer layer = layerOf(quad, into);
					if (layer == null) {
						continue;
					}
					float[] bounds = bounds(quad, into);
					// The renderer would tint our quads as this block, not the neighbour, so resolve its tint here.
					int color = tint ? END_CAP_TINT : tintColor(level, neighbourPos, neighbour, layer.tintIndex());
					for (Rect rect : caps) {
						float u0 = Math.max(rect.u0() / 16f, bounds[0]);
						float v0 = Math.max(rect.v0() / 16f, bounds[1]);
						float u1 = Math.min(rect.u1() / 16f, bounds[2]);
						float v1 = Math.min(rect.v1() / 16f, bounds[3]);
						if (u0 < u1 && v0 < v1) {
							emitQuad(emitter, into, u0, v0, u1, v1, 1f, layer, -1, light, color, neighbourEmissive, neighbourOpaque);
							hadEndCaps = true;
						}
					}
				}
			}
		}

		CarveStats.record(System.nanoTime() - start, hadEndCaps);
	}

	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		Cells cells = RoundedEdgesSettings.enabled ? Cells.around(level, pos) : null;
		if (cells == null || level.getBlockState(pos) != state || !cells.isNear(state, 0, 0, 0) || CarveKey.compute(cells) == 0) {
			return super.createGeometryKey(level, pos, state, random);
		}
		// Carved geometry depends on the neighbourhood; don't let renderers cache it.
		return null;
	}

	/** The model parts for a position, picked with the same seed the renderer uses, so random variants match. */
	private static List<BlockStateModelPart> partsAt(BlockStateModel model, BlockState state, BlockPos pos) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		RandomSource random = RANDOM.get();
		random.setSeed(state.getSeed(pos));
		model.collectParts(random, parts);
		return parts;
	}

	private static final ThreadLocal<RandomSource> RANDOM = ThreadLocal.withInitial(RandomSource::create);

	/** Texture layers of the parts' quads on one face; falls back to the particle texture if there are none. */
	private List<FaceLayer> layersOf(List<BlockStateModelPart> parts, Face face, FaceLayer fallback) {
		List<FaceLayer> result = new ArrayList<>(2);
		Direction dir = DIRECTIONS[face.ordinal()];
		for (BlockStateModelPart part : parts) {
			for (BakedQuad quad : part.getQuads(dir)) {
				FaceLayer layer = layerOf(quad, face);
				if (layer != null) {
					result.add(layer);
				}
			}
			// Faces set in from the block boundary (the top of a path or farmland) have no cull face.
			for (BakedQuad quad : part.getQuads(null)) {
				FaceLayer layer = quad.direction() == dir ? layerOf(quad, face) : null;
				if (layer != null) {
					result.add(layer);
				}
			}
		}
		if (result.isEmpty()) {
			result.add(fallback);
		}
		return result;
	}

	private @Nullable FaceLayer layerOf(BakedQuad quad, Face face) {
		return layers.computeIfAbsent(quad, q -> Optional.ofNullable(FaceLayer.of(q, face))).orElse(null);
	}

	/** A quad's extent on a face, as {u0, v0, u1, v1} in that face's frame (0..1). */
	private static float[] bounds(BakedQuad quad, Face face) {
		float[] b = {1, 1, 0, 0};
		for (int i = 0; i < 4; i++) {
			Vector3fc p = quad.position(i);
			float u = face.u(p.x(), p.y(), p.z());
			float v = face.v(p.x(), p.y(), p.z());
			b[0] = Math.min(b[0], u);
			b[1] = Math.min(b[1], v);
			b[2] = Math.max(b[2], u);
			b[3] = Math.max(b[3], v);
		}
		return b;
	}

	/** The particle texture laid out like vanilla's default cube UVs. */
	private static FaceLayer particleMaterialLayer(BlockStateModel model) {
		return new FaceLayer(model.particleMaterial(), -1, 1, 0, 0, 0, -1, 1);
	}

	private static int tintColor(BlockAndTintGetter level, BlockPos pos, BlockState state, int tintIndex) {
		if (tintIndex < 0) {
			return WHITE;
		}
		BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
		return source == null ? WHITE : 0xFF000000 | source.colorInWorld(state, level, pos);
	}

	/**
	 * Emits one rectangle facing {@code face}. With {@code light} the quad sits inside the block and is lit here,
	 * smoothly per vertex; without it, it's a boundary face and the renderer lights (and culls) it like a normal one.
	 */
	private static void emitRect(QuadEmitter emitter, Face face, Rect rect, FaceLayer layer, int tintIndex,
			@Nullable CornerLight light, int color, boolean emissive, boolean opaque) {
		emitQuad(emitter, face, rect.u0() / 16f, rect.v0() / 16f, rect.u1() / 16f, rect.v1() / 16f, rect.depth() / 16f,
				layer, tintIndex, light, color, emissive, opaque);
	}

	/** Like {@link #emitRect} for any rectangle on the face (0..1 in its frame) at {@code depth} (0..1) in from it. */
	private static void emitQuad(QuadEmitter emitter, Face face, float u0, float v0, float u1, float v1, float depth,
			FaceLayer layer, int tintIndex, @Nullable CornerLight light, int color, boolean emissive, boolean opaque) {
		emitter.square(DIRECTIONS[face.ordinal()], u0, v0, u1, v1, depth);
		for (int i = 0; i < 4; i++) {
			float x = emitter.x(i);
			float y = emitter.y(i);
			float z = emitter.z(i);
			float u = face.u(x, y, z);
			float v = face.v(x, y, z);
			emitter.uv(i, layer.s(u, v), layer.t(u, v));
			int vertexColor = color;
			if (light != null) {
				emitter.lightmap(i, light.lightAt(x, y, z));
				int shade = Math.round(light.shadeAt(x, y, z) * 255);
				vertexColor = ARGB.multiply(ARGB.color(255, shade, shade, shade), color);
			}
			if (vertexColor != WHITE) {
				emitter.color(i, vertexColor);
			}
		}
		emitter.materialBake(layer.material(), MutableQuadView.BAKE_NORMALIZED);
		if (opaque) {
			emitter.chunkLayer(ChunkSectionLayer.SOLID);
		}
		emitter.tintIndex(tintIndex);
		emitter.ambientOcclusion(light != null ? TriState.FALSE : TriState.DEFAULT);
		emitter.emissive(emissive);
		emitter.emit();
		CarveStats.quad();
	}
}
