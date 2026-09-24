package com.jamesdebono.roundededges.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Keeps the carve distance up to date as the camera moves. Chunk builds note which sections contain anything
 * carvable; when the camera has moved far enough, those that crossed the carve distance are re-meshed (with their
 * neighbours, whose end caps depend on whether this section is carved).
 */
public final class CarveDistance {
	/** Blocks the camera must move before the carve distance is re-measured. */
	private static final int MOVE_BEFORE_UPDATE = 8;

	private static final Set<Long> SECTIONS_WITH_CARVING = ConcurrentHashMap.newKeySet();
	private static final ThreadLocal<long[]> LAST_NOTED = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
	private static ClientLevel trackedLevel;

	private CarveDistance() {
	}

	/** Called from chunk-build threads for each block that is (or would be, if near) carved. */
	public static void noteCarvable(BlockPos pos) {
		long section = SectionPos.asLong(pos);
		long[] last = LAST_NOTED.get();
		if (last[0] != section) {
			last[0] = section;
			SECTIONS_WITH_CARVING.add(section);
		}
	}

	static void tick(Minecraft client) {
		if (client.level != trackedLevel) {
			trackedLevel = client.level;
			SECTIONS_WITH_CARVING.clear();
			RoundedEdgesSettings.anchor = RoundedEdgesSettings.UNSET;
		}
		if (client.level == null) {
			return;
		}
		BlockPos camera = client.gameRenderer.mainCamera().blockPosition();
		long previous = RoundedEdgesSettings.anchor;
		if (previous != RoundedEdgesSettings.UNSET && camera.distSqr(BlockPos.of(previous)) < MOVE_BEFORE_UPDATE * MOVE_BEFORE_UPDATE) {
			return;
		}
		RoundedEdgesSettings.anchor = camera.asLong();
		if (!RoundedEdgesSettings.enabled || RoundedEdgesSettings.carveDistance <= 0) {
			return;
		}
		long current = camera.asLong();
		for (long section : SECTIONS_WITH_CARVING) {
			int sx = SectionPos.x(section);
			int sy = SectionPos.y(section);
			int sz = SectionPos.z(section);
			if (RoundedEdgesSettings.isNear(sx, sy, sz, previous) != RoundedEdgesSettings.isNear(sx, sy, sz, current)) {
				client.levelExtractor.setSectionDirtyWithNeighbors(sx, sy, sz);
			}
		}
	}
}
