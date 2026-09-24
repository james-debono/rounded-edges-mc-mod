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

import com.jamesdebono.roundededges.client.debug.CarveStats;
import com.jamesdebono.roundededges.client.model.ChamferedModel;

public class RoundedEdgesClient implements ClientModInitializer {
	public static final String MOD_ID = "rounded_edges";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	@Override
	public void onInitializeClient() {
		CarvableBlocks.init();
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, ctx) ->
				ChamferedModel.isCarvable(ctx.state()) ? new ChamferedModel(model, RoundedEdgesSettings.CARVER) : model));

		KeyMapping distance = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rounded_edges.carve_distance_toggle", GLFW.GLFW_KEY_I, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CarveDistance.tick(client);
			while (distance.consumeClick()) {
				RoundedEdgesSettings.carveDistance = RoundedEdgesSettings.carveDistance == 64 ? 32 : 64;
				rebuild(client, "Carve distance: " + RoundedEdgesSettings.carveDistance + " blocks");
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
