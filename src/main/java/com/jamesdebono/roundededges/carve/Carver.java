package com.jamesdebono.roundededges.carve;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches the {@link CarvedShape} for each {@link CarveKey} under one profile. Thread-safe; the
 * inside-corner mode can be switched at runtime.
 */
public final class Carver {
	private final Profile profile;
	private final Map<Long, CarvedShape> cache = new ConcurrentHashMap<>();
	private volatile InsideCorners insideCorners;

	public Carver(Profile profile) {
		this(profile, InsideCorners.MITRE);
	}

	public Carver(Profile profile, InsideCorners insideCorners) {
		this.profile = profile;
		this.insideCorners = insideCorners;
	}

	public Profile profile() {
		return profile;
	}

	public InsideCorners insideCorners() {
		return insideCorners;
	}

	public void setInsideCorners(InsideCorners insideCorners) {
		this.insideCorners = insideCorners;
		cache.clear();
	}

	public CarvedShape shape(long key) {
		InsideCorners mode = insideCorners;
		// The mode is part of the cache key so a build racing with a mode switch can't leave a stale shape behind.
		long cacheKey = key | (long) mode.ordinal() << 40;
		CarvedShape shape = cache.get(cacheKey);
		if (shape == null) {
			// Racing threads may both build it; the results are identical, so either one is fine to keep.
			shape = new CarvedShape(profile, mode, key);
			cache.putIfAbsent(cacheKey, shape);
		}
		return shape;
	}
}
