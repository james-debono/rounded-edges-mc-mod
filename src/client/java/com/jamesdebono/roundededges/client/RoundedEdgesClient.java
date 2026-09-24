package com.jamesdebono.roundededges.client;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import com.jamesdebono.roundededges.carve.InsideCorners;
import com.jamesdebono.roundededges.client.debug.CarveStats;
import com.jamesdebono.roundededges.client.model.ChamferedModel;

public class RoundedEdgesClient implements ClientModInitializer {
	public static final String MOD_ID = "rounded_edges";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "debug"));

	@Override
	public void onInitializeClient() {
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, ctx) ->
				ChamferedModel.isCarvable(ctx.state()) ? new ChamferedModel(model, RoundedEdgesSettings.CARVER) : model));

		KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rounded_edges.toggle", GLFW.GLFW_KEY_J, CATEGORY));
		KeyMapping tint = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rounded_edges.debug_tint", GLFW.GLFW_KEY_K, CATEGORY));
		KeyMapping stats = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rounded_edges.stats", GLFW.GLFW_KEY_U, CATEGORY));
		KeyMapping insideCorners = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rounded_edges.inside_corners", GLFW.GLFW_KEY_I, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggle.consumeClick()) {
				RoundedEdgesSettings.enabled = !RoundedEdgesSettings.enabled;
				rebuild(client, "Rounded edges " + (RoundedEdgesSettings.enabled ? "ON" : "OFF"));
			}
			while (tint.consumeClick()) {
				RoundedEdgesSettings.debugTint = !RoundedEdgesSettings.debugTint;
				rebuild(client, "Debug tint " + (RoundedEdgesSettings.debugTint ? "ON (steps blue, end caps red)" : "OFF"));
			}
			while (insideCorners.consumeClick()) {
				InsideCorners mode = RoundedEdgesSettings.CARVER.insideCorners().next();
				RoundedEdgesSettings.CARVER.setInsideCorners(mode);
				rebuild(client, "Inside corners: " + mode.label());
			}
			while (stats.consumeClick()) {
				String summary = CarveStats.summary();
				LOGGER.info(summary);
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.literal(summary));
				}
			}
		});
	}

	private static void rebuild(Minecraft client, String message) {
		CarveStats.reset();
		client.levelExtractor.allChanged();
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal(message));
		}
	}
}
