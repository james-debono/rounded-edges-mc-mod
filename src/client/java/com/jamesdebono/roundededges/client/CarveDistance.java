package com.jamesdebono.roundededges.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Keeps the carve distance up to date as the camera moves. Chunk builds note which sections contain anything
 * carvable; when the camera has moved far enough, those that crossed a carve distance are re-meshed, plus the
 * neighbours that have carving of their own (their end caps depend on whether this section is carved).
 */
public final class CarveDistance {
	/** Blocks the camera must move before the carve distance is re-measured. */
	private static final int MOVE_BEFORE_UPDATE = 8;

	/** Sections handed to the renderer for re-meshing per tick (20 ticks a second). */
	private static final int REBUILDS_PER_TICK = 24;

	private static final Set<Long> SECTIONS_WITH_CARVING = ConcurrentHashMap.newKeySet();
	private static final java.util.ArrayDeque<Long> PENDING = new java.util.ArrayDeque<>();
	private static final Set<Long> QUEUED = new java.util.HashSet<>();
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
			PENDING.clear();
			QUEUED.clear();
			RoundedEdgesSettings.anchor = RoundedEdgesSettings.UNSET;
		}
		if (client.level == null) {
			return;
		}
		// Hand queued rebuilds to the renderer a few at a time, so a batch doesn't land in one frame.
		for (int i = 0; i < REBUILDS_PER_TICK && !PENDING.isEmpty(); i++) {
			long section = PENDING.removeFirst();
			QUEUED.remove(section);
			client.levelExtractor.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
		}
		BlockPos camera = client.gameRenderer.mainCamera().blockPosition();
		long previous = RoundedEdgesSettings.anchor;
		if (previous != RoundedEdgesSettings.UNSET && camera.distSqr(BlockPos.of(previous)) < MOVE_BEFORE_UPDATE * MOVE_BEFORE_UPDATE) {
			return;
		}
		long current = camera.asLong();
		RoundedEdgesSettings.anchor = current;
		if (!RoundedEdgesSettings.enabled) {
			return;
		}
		Set<Long> dirty = new java.util.HashSet<>();
		for (long section : SECTIONS_WITH_CARVING) {
			int sx = SectionPos.x(section);
			int sy = SectionPos.y(section);
			int sz = SectionPos.z(section);
			if (changed(sx, sy, sz, previous, current)) {
				dirty.add(section);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							long neighbour = SectionPos.asLong(sx + dx, sy + dy, sz + dz);
							// Only carved neighbours draw end caps that depend on this section.
							if (SECTIONS_WITH_CARVING.contains(neighbour) && RoundedEdgesSettings.isNear(sx + dx, sy + dy, sz + dz, current, false)) {
								dirty.add(neighbour);
							}
						}
					}
				}
			}
		}
		for (long section : dirty) {
			if (QUEUED.add(section)) {
				PENDING.addLast(section);
			}
		}
	}

	private static boolean changed(int sx, int sy, int sz, long previous, long current) {
		return RoundedEdgesSettings.isNear(sx, sy, sz, previous, false) != RoundedEdgesSettings.isNear(sx, sy, sz, current, false)
				|| RoundedEdgesSettings.isNear(sx, sy, sz, previous, true) != RoundedEdgesSettings.isNear(sx, sy, sz, current, true);
	}
}
