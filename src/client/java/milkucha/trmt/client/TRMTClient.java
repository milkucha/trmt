package milkucha.trmt.client;

import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.render.ErodedGrassModels;
import milkucha.trmt.erosion.ErosionMapManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.RenderLayer;

public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ErodedGrassModels.register();
		// Eroded grass models have transparent holes — must use CUTOUT_MIPPED.
		BlockRenderLayerMap.INSTANCE.putBlock(Blocks.GRASS_BLOCK, RenderLayer.getCutoutMipped());
		ErosionDebugHud.register();

		// When erosion stage advances the block state doesn't change, so the chunk section
		// is never automatically rebuilt. Drain positions queued by the server tick and
		// force a section re-render so the proxy model picks up the new stage.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world == null) return;
			ErosionMapManager.getInstance().drainRerenders(pos ->
				client.world.scheduleBlockRerenderIfNeeded(
					pos,
					Blocks.AIR.getDefaultState(),
					client.world.getBlockState(pos)
				)
			);
		});
	}
}
