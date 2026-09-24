package com.jamesdebono.roundededges.gametest;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.ChatVisiblity;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.jamesdebono.roundededges.carve.InsideCorners;
import com.jamesdebono.roundededges.client.RoundedEdgesClient;
import com.jamesdebono.roundededges.client.RoundedEdgesSettings;
import com.jamesdebono.roundededges.client.debug.CarveStats;

/**
 * Builds the prototype test shapes in a flat creative world and screenshots them from fixed viewpoints, with carving
 * on, with the debug tint, and with carving off. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class RoundedEdgesClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			context.getInput().resizeWindow(1600, 900);
			context.runOnClient(client -> client.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			sp.getConnection().waitForChunksRender();
			BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
			int x = feet.getX();
			int y = feet.getY(); // first air layer above the grass
			int z = feet.getZ() - 10; // shapes sit north of the spawn point

			// Lone floating block: all 12 edges cut, 3-edge corners.
			fill(sp, x - 7, y + 2, z, x - 7, y + 2, z);
			// 1x1 pillar, 3 tall, standing on the grass.
			fill(sp, x - 4, y, z, x - 4, y + 2, z);
			// Floating 3x3 platform: mitred outer corners, flat middle.
			fill(sp, x - 1, y + 2, z - 1, x + 1, y + 2, z + 1);
			// Stepped slope rising to the north.
			for (int i = 0; i < 3; i++) {
				fill(sp, x + 4, y, z + 1 - i, x + 6, y + i, z + 1 - i);
			}
			// End cap: A's top-south edge is cut, but C (east of A) has a block on top so its edge isn't;
			// A must draw C's face patch at the end of its cut.
			fill(sp, x + 9, y, z + 1, x + 10, y, z + 1);
			fill(sp, x + 10, y + 1, z + 1, x + 10, y + 1, z + 1);
			// Overhang: a ledge sticking out south from a wall.
			fill(sp, x + 13, y, z - 1, x + 15, y + 3, z - 1);
			fill(sp, x + 13, y + 3, z, x + 15, y + 3, z + 1);
			// Inside corners, as in the design sketches (south of spawn). Three cuts (1A/2A): B with T on top, L to the
			// south and R to the east. Two cuts (1B/2B/2C): the same without T.
			int iz = feet.getZ() + 5;
			int bx3 = x - 4;
			fill(sp, bx3, y, iz, bx3, y + 1, iz);
			fill(sp, bx3, y, iz + 1, bx3, y, iz + 1);
			fill(sp, bx3 + 1, y, iz, bx3 + 1, y, iz);
			int bx2 = x + 2;
			fill(sp, bx2, y, iz, bx2, y, iz + 1);
			fill(sp, bx2 + 1, y, iz, bx2 + 1, y, iz);

			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("time set noon");
			sp.getConnection().waitForChunksRender(false);

			// Overview, then close-ups.
			shoot(context, sp, "overview", x + 4.5, y + 7, z + 13, x + 4.5, y + 1, z);
			shoot(context, sp, "lone_block_corner", x - 7 + 2.2, y + 2 + 1.8, z + 2.2, x - 6.5, y + 2.5, z + 0.5);
			shoot(context, sp, "lone_block_below", x - 7 + 2.0, y + 2 - 1.2, z + 2.0, x - 6.5, y + 2.5, z + 0.5);
			shoot(context, sp, "platform_corner", x + 1 + 2.0, y + 2 + 2.0, z + 1 + 2.0, x + 1.5, y + 3, z + 1.5);
			shoot(context, sp, "slope", x + 8.5, y + 3.5, z + 5, x + 5.5, y + 1, z);
			shoot(context, sp, "end_cap", x + 8.2, y + 1.6, z + 3.2, x + 10, y + 1, z + 2);
			shoot(context, sp, "overhang_under", x + 14.5, y + 1.2, z + 3.5, x + 14.5, y + 3.2, z + 1);

			// Inside corners in each style. V is the corner where the cuts meet.
			for (InsideCorners mode : new InsideCorners[] {InsideCorners.MITRE, InsideCorners.PYRAMID, InsideCorners.SINGLE_TEXEL, InsideCorners.OFF}) {
				setInsideCorners(context, sp, mode);
				String name = mode.name().toLowerCase(java.util.Locale.ROOT);
				double v3x = bx3 + 1;
				double v3y = y + 1;
				double v3z = iz + 1;
				shoot(context, sp, "inside3_" + name, v3x + 0.75, v3y + 0.65, v3z + 0.75, v3x - 0.05, v3y - 0.05, v3z - 0.05);
				double v2x = bx2 + 1;
				double v2z = iz + 1;
				shoot(context, sp, "inside2_" + name, v2x + 0.75, v3y + 0.65, v2z + 0.75, v2x - 0.05, v3y - 0.05, v2z - 0.05);
				// Foot of R's north-east vertical cut, where it ends on the grass.
				shoot(context, sp, "inside3_foot_" + name, bx3 + 2 + 0.7, y + 0.5, iz - 0.7, bx3 + 2, y, iz);
			}
			setInsideCorners(context, sp, InsideCorners.MITRE);

			// Same views with the debug tint (steps blue, end caps red).
			setFlags(context, sp, true, true);
			shoot(context, sp, "tint_overview", x + 4.5, y + 7, z + 13, x + 4.5, y + 1, z);
			shoot(context, sp, "tint_end_cap", x + 8.2, y + 1.6, z + 3.2, x + 10, y + 1, z + 2);
			shoot(context, sp, "tint_lone_block_corner", x - 7 + 2.2, y + 2 + 1.8, z + 2.2, x - 6.5, y + 2.5, z + 0.5);

			// Carving off, for comparison.
			setFlags(context, sp, false, false);
			shoot(context, sp, "off_overview", x + 4.5, y + 7, z + 13, x + 4.5, y + 1, z);

			// Night with a torch: step faces must pick up block light, not render black.
			setFlags(context, sp, true, false);
			sp.getServer().runCommand("time set midnight");
			sp.getServer().runCommand("setblock %d %d %d minecraft:torch".formatted(x - 3, y, z + 1));
			sp.getConnection().waitForChunksRender(false);
			shoot(context, sp, "night_torch_pillar", x - 4 + 2.0, y + 3.2, z + 2.5, x - 3.5, y + 2, z + 0.5);
		}

		// Real terrain: mesh a normal world and report how much carving costs, first with the shape cache cold
		// (this run's shapes were built for the flat world only), then again on a full rebuild.
		context.runOnClient(client -> CarveStats.reset());
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(s -> {
					s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					s.setSeed("rounded-edges");
				})
				.create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(client -> RoundedEdgesClient.LOGGER.info("[natural world, first load] {} (render distance {})",
					CarveStats.summary(), client.options.getEffectiveRenderDistance()));
			context.runOnClient(client -> CarveStats.reset());
			setFlags(context, sp, true, false);
			context.runOnClient(client -> RoundedEdgesClient.LOGGER.info("[natural world, rebuild] {}", CarveStats.summary()));
			sp.getServer().runCommand("gamemode spectator @a");
			BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());
			shoot(context, sp, "natural_terrain", spawn.getX(), spawn.getY() + 25, spawn.getZ(), spawn.getX() + 40, spawn.getY(), spawn.getZ() + 40);
		}
	}

	private static void fill(TestSingleplayerContext sp, int x1, int y1, int z1, int x2, int y2, int z2) {
		sp.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(x1, y1, z1, x2, y2, z2));
	}

	private static void setInsideCorners(ClientGameTestContext context, TestSingleplayerContext sp, InsideCorners mode) {
		context.runOnClient(client -> {
			RoundedEdgesSettings.CARVER.setInsideCorners(mode);
			client.levelExtractor.allChanged();
		});
		context.waitTick();
		sp.getConnection().waitForChunksRender(false);
	}

	private static void setFlags(ClientGameTestContext context, TestSingleplayerContext sp, boolean enabled, boolean tint) {
		context.runOnClient(client -> {
			RoundedEdgesSettings.enabled = enabled;
			RoundedEdgesSettings.debugTint = tint;
			client.levelExtractor.allChanged();
		});
		context.waitTick();
		sp.getConnection().waitForChunksRender(false);
	}

	/** Puts the camera (eye) at cam, looking at look, and takes a screenshot. */
	private static void shoot(ClientGameTestContext context, TestSingleplayerContext sp, String name,
			double camX, double camY, double camZ, double lookX, double lookY, double lookZ) {
		double eyeHeight = context.computeOnClient(client -> (double) client.player.getEyeHeight());
		double dx = lookX - camX;
		double dy = lookY - camY;
		double dz = lookZ - camZ;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runCommand("tp @a %.3f %.3f %.3f %.2f %.2f".formatted(camX, camY - eyeHeight, camZ, yaw, pitch));
		sp.getConnection().waitForClientboundPackets();
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(false);
		context.takeScreenshot(name);
	}
}
