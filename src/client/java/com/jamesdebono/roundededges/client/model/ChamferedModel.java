package com.jamesdebono.roundededges.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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
		if (key == 0 || !cells.isNear(0, 0, 0)) {
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
		for (Face face : Face.VALUES) {
			if ((carvedFaces & face.bit()) == 0) {
				continue;
			}
			Direction dir = DIRECTIONS[face.ordinal()];
			List<FaceLayer> faceLayers = layersOf(parts, face, particleMaterialLayer(wrapped));
			for (Rect rect : shape.rects(face)) {
				boolean boundary = rect.depth() == 0;
				if (boundary && cullTest.test(dir)) {
					continue;
				}
				for (FaceLayer layer : faceLayers) {
					emitRect(emitter, face, rect, layer, layer.tintIndex(), boundary ? null : light,
							boundary || !tint ? WHITE : STEP_TINT, emissive, opaque);
				}
			}
		}

		// 3. End caps: where a cut runs into a full-cube neighbour whose own cut doesn't continue, and that neighbour
		// hides its face against us (it sees a full cube here), draw its face patch ourselves.
		boolean hadEndCaps = false;
		for (Face side : Face.VALUES) {
			if ((shape.openBoundaries() & side.bit()) == 0) {
				continue;
			}
			BlockState neighbour = cells.state(side.dx, side.dy, side.dz);
			Face into = side.opposite();
			boolean fullCube = neighbour.isSolidRender() || isCarvable(neighbour);
			if (!fullCube || Block.shouldRenderFace(neighbour, state, DIRECTIONS[into.ordinal()])) {
				continue; // no face there, or the neighbour draws it itself
			}
			boolean neighbourCarved = cells.kind(side.dx, side.dy, side.dz) == CarveKey.CARVABLE;
			CarvedShape neighbourShape = neighbourCarved ? carver.shape(CarveKey.compute(cells.from(side))) : null;
			List<Rect> caps = shape.endCaps(side, neighbourShape);
			if (caps.isEmpty()) {
				continue;
			}
			hadEndCaps = true;
			boolean neighbourOpaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, neighbour);
			BlockPos neighbourPos = cells.pos(side.dx, side.dy, side.dz).immutable();
			BlockStateModel neighbourModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(neighbour);
			List<FaceLayer> neighbourLayers = layersOf(partsAt(neighbourModel, neighbour, neighbourPos), into, particleMaterialLayer(neighbourModel));
			for (FaceLayer layer : neighbourLayers) {
				// The renderer would tint our quads as this block, not the neighbour, so resolve its tint here.
				int color = tint ? END_CAP_TINT : tintColor(level, neighbourPos, neighbour, layer.tintIndex());
				for (Rect rect : caps) {
					emitRect(emitter, into, rect, layer, -1, light, color, neighbour.emissiveRendering(), neighbourOpaque);
				}
			}
		}

		CarveStats.record(System.nanoTime() - start, hadEndCaps);
	}

	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		Cells cells = RoundedEdgesSettings.enabled ? Cells.around(level, pos) : null;
		if (cells == null || level.getBlockState(pos) != state || !cells.isNear(0, 0, 0) || CarveKey.compute(cells) == 0) {
			return super.createGeometryKey(level, pos, state, random);
		}
		// Carved geometry depends on the neighbourhood; don't let renderers cache it.
		return null;
	}

	/** The model parts for a position, picked with the same seed the renderer uses, so random variants match. */
	private static List<BlockStateModelPart> partsAt(BlockStateModel model, BlockState state, BlockPos pos) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(state.getSeed(pos)), parts);
		return parts;
	}

	/** Texture layers of the parts' quads on one face; falls back to the particle texture if there are none. */
	private List<FaceLayer> layersOf(List<BlockStateModelPart> parts, Face face, FaceLayer fallback) {
		List<FaceLayer> result = new ArrayList<>(2);
		Direction dir = DIRECTIONS[face.ordinal()];
		for (BlockStateModelPart part : parts) {
			for (BakedQuad quad : part.getQuads(dir)) {
				layers.computeIfAbsent(quad, q -> Optional.ofNullable(FaceLayer.of(q, face))).ifPresent(result::add);
			}
		}
		if (result.isEmpty()) {
			result.add(fallback);
		}
		return result;
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
		emitter.square(DIRECTIONS[face.ordinal()], rect.u0() / 16f, rect.v0() / 16f, rect.u1() / 16f, rect.v1() / 16f, rect.depth() / 16f);
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
