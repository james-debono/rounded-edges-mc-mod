package com.jamesdebono.roundededges.client.model;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.jamesdebono.roundededges.carve.CarveKey;
import com.jamesdebono.roundededges.carve.Face;

/**
 * Block states, light and AO shade around one block (offsets -2..2), read lazily and cached. One instance per
 * chunk-build thread, reset for each block by bumping a generation counter.
 */
final class Cells implements CarveKey.Cells {
	private static final int RADIUS = 2;
	private static final int SIZE = 2 * RADIUS + 1;
	private static final ThreadLocal<Cells> PER_THREAD = ThreadLocal.withInitial(Cells::new);

	private final BlockState[] states = new BlockState[SIZE * SIZE * SIZE];
	private final int[] light = new int[states.length];
	private final float[] shade = new float[states.length];
	private final int[] stateGen = new int[states.length];
	private final int[] lightGen = new int[states.length];
	private final int[] shadeGen = new int[states.length];
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private int generation;
	private BlockAndTintGetter level;
	private BlockPos origin;

	static Cells around(BlockAndTintGetter level, BlockPos origin) {
		Cells cells = PER_THREAD.get();
		cells.level = level;
		cells.origin = origin;
		cells.generation++;
		return cells;
	}

	private static int index(int dx, int dy, int dz) {
		return ((dx + RADIUS) * SIZE + dy + RADIUS) * SIZE + dz + RADIUS;
	}

	BlockAndTintGetter level() {
		return level;
	}

	BlockPos pos(int dx, int dy, int dz) {
		return cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
	}

	BlockState state(int dx, int dy, int dz) {
		int i = index(dx, dy, dz);
		if (stateGen[i] != generation) {
			states[i] = level.getBlockState(pos(dx, dy, dz));
			stateGen[i] = generation;
		}
		return states[i];
	}

	@Override
	public int kind(int dx, int dy, int dz) {
		BlockState state = state(dx, dy, dz);
		if (state.isAir()) {
			return CarveKey.EMPTY;
		}
		if (ChamferedModel.isCarvable(state)) {
			return CarveKey.CARVABLE;
		}
		if (state.isSolidRender()) {
			return CarveKey.SOLID;
		}
		return isOpen(state, dx, dy, dz) ? CarveKey.EMPTY : CarveKey.SOLID;
	}

	/**
	 * Decorations you can walk through (grass, flowers, torches, cobwebs...) count as empty, so edges still round
	 * under them. They don't hide faces against our block, so the cut can't open a hole. Fluids, snow layers and
	 * anything with collision (leaves, glass, slabs...) stay solid.
	 */
	private boolean isOpen(BlockState state, int dx, int dy, int dz) {
		return state.getFluidState().isEmpty()
				&& !(state.getBlock() instanceof SnowLayerBlock)
				&& state.getCollisionShape(level, pos(dx, dy, dz)).isEmpty();
	}

	int light(int dx, int dy, int dz) {
		int i = index(dx, dy, dz);
		if (lightGen[i] != generation) {
			light[i] = LightCoordsUtil.getLightCoords(level, pos(dx, dy, dz));
			lightGen[i] = generation;
		}
		return light[i];
	}

	/** Vanilla AO shade: 1 for open cells, about 0.2 for full opaque blocks. */
	float shade(int dx, int dy, int dz) {
		int i = index(dx, dy, dz);
		if (shadeGen[i] != generation) {
			BlockState state = state(dx, dy, dz);
			shade[i] = state.getShadeBrightness(level, pos(dx, dy, dz));
			shadeGen[i] = generation;
		}
		return shade[i];
	}

	/** The neighbourhood as seen from the neighbour on the given side (reads up to 2 away from the origin). */
	CarveKey.Cells from(Face side) {
		return (dx, dy, dz) -> kind(dx + side.dx, dy + side.dy, dz + side.dz);
	}
}
