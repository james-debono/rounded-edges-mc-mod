package com.jamesdebono.roundededges.client.debug;

import java.util.concurrent.atomic.LongAdder;

/** Timing for the carved path of {@code ChamferedModel}, summed across chunk-build threads. */
public final class CarveStats {
	private static final LongAdder CARVED_BLOCKS = new LongAdder();
	private static final LongAdder CARVED_NANOS = new LongAdder();
	private static final LongAdder END_CAP_BLOCKS = new LongAdder();

	private CarveStats() {
	}

	public static void record(long nanos, boolean hadEndCaps) {
		CARVED_BLOCKS.increment();
		CARVED_NANOS.add(nanos);
		if (hadEndCaps) {
			END_CAP_BLOCKS.increment();
		}
	}

	public static void reset() {
		CARVED_BLOCKS.reset();
		CARVED_NANOS.reset();
		END_CAP_BLOCKS.reset();
	}

	public static String summary() {
		long blocks = CARVED_BLOCKS.sum();
		double avgMicros = blocks == 0 ? 0 : CARVED_NANOS.sum() / 1000.0 / blocks;
		return String.format("Rounded edges: %,d carved blocks meshed (%,d with end caps), avg %.2f µs each, total %.1f ms",
				blocks, END_CAP_BLOCKS.sum(), avgMicros, CARVED_NANOS.sum() / 1_000_000.0);
	}
}
