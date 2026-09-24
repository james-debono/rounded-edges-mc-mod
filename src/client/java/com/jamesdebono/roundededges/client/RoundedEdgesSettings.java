package com.jamesdebono.roundededges.client;

import com.jamesdebono.roundededges.carve.Carver;
import com.jamesdebono.roundededges.carve.Profile;

/** Runtime switches for the prototype. Read from chunk-build threads, so the flags are volatile. */
public final class RoundedEdgesSettings {
	public static final Carver CARVER = new Carver(Profile.STAIR_2);

	public static volatile boolean enabled = true;
	public static volatile boolean debugTint = false;

	private RoundedEdgesSettings() {
	}
}
