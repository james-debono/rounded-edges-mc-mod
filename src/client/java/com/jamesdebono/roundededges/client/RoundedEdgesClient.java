package com.jamesdebono.roundededges.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import com.jamesdebono.roundededges.client.model.ChamferedModel;

public class RoundedEdgesClient implements ClientModInitializer {
	public static final String MOD_ID = "rounded_edges";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		CarvableBlocks.init();
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, ctx) ->
				ChamferedModel.isCarvable(ctx.state()) ? new ChamferedModel(model, RoundedEdgesSettings.CARVER) : model));
		ClientTickEvents.END_CLIENT_TICK.register(CarveDistance::tick);
	}
}
