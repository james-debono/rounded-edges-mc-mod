package com.jamesdebono.roundededges.client.model;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;

import com.jamesdebono.roundededges.carve.CarveKey;
import com.jamesdebono.roundededges.carve.Face;
import com.jamesdebono.roundededges.client.CarvableBlocks;
import com.jamesdebono.roundededges.client.RoundedEdgesSettings;

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
			// Beyond the carve distance a carvable block is drawn uncut, so treat it like any solid block.
			return isNear(state, dx, dy, dz) ? CarveKey.CARVABLE : CarveKey.SOLID;
		}
		// Anything that isn't a full opaque cube (water, torches, fences, glass, slabs, plants...) leaves room for
		// the edge to round. Where such a block hides part of its face against ours, the model draws that part
		// back into the cut, so nothing opens up.
		return state.isSolidRender() ? CarveKey.SOLID : CarveKey.EMPTY;
	}

	@Override
	public int fixedMask(int dx, int dy, int dz) {
		return kind(dx, dy, dz) == CarveKey.CARVABLE ? CarvableBlocks.fixedMask(state(dx, dy, dz)) : -1;
	}

	@Override
	public boolean isShort(int dx, int dy, int dz) {
		return kind(dx, dy, dz) == CarveKey.CARVABLE && CarvableBlocks.isShort(state(dx, dy, dz));
	}

	/** Whether the carvable block at this offset is within its carve distance. */
	boolean isNear(BlockState state, int dx, int dy, int dz) {
		return RoundedEdgesSettings.isNear(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, CarvableBlocks.isLeaves(state));
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
		return new CarveKey.Cells() {
			@Override
			public int kind(int dx, int dy, int dz) {
				return Cells.this.kind(dx + side.dx, dy + side.dy, dz + side.dz);
			}

			@Override
			public int fixedMask(int dx, int dy, int dz) {
				return Cells.this.fixedMask(dx + side.dx, dy + side.dy, dz + side.dz);
			}

			@Override
			public boolean isShort(int dx, int dy, int dz) {
				return Cells.this.isShort(dx + side.dx, dy + side.dy, dz + side.dz);
			}
		};
	}
}
