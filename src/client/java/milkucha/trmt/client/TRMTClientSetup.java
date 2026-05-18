package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = TRMT.MOD_ID, bus = Bus.MOD, value = Dist.CLIENT)
public final class TRMTClientSetup {

	private TRMTClientSetup() {}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			TRMTClientConfig.load();
			ErodedGrassBlockModels.register();
			ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_GRASS_BLOCK.get(), RenderType.cutoutMipped());
			ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_SAND.get(), RenderType.cutoutMipped());
			ErosionDebugHud.register();
		});
	}

	@SubscribeEvent
	public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
		event.register(
			(state, level, pos, tintIndex) ->
				level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : 0x79C05A,
			TRMTBlocks.ERODED_GRASS_BLOCK.get());
	}

	@SubscribeEvent
	public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar reg = event.registrar("1");
		reg.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.STREAM_CODEC, TRMTClientSetup::handleSyncChunk);
		reg.playToClient(UpdateStagePayload.TYPE, UpdateStagePayload.STREAM_CODEC, TRMTClientSetup::handleUpdateStage);
	}

	private static void handleSyncChunk(SyncChunkPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>();
			payload.entries().forEach((pos, en) ->
				chunkEntries.put(pos, new ClientErosionCache.Entry(
					en.stage(), en.walkedOnCount(), en.threshold(), en.lastTouchedGameTime())));
			ClientErosionCache.getInstance().setChunk(payload.chunkPos(), chunkEntries);
		});
	}

	private static void handleUpdateStage(UpdateStagePayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() ->
			ClientErosionCache.getInstance().setEntry(
				payload.pos(),
				payload.stage(),
				payload.walkedOnCount(),
				payload.threshold(),
				payload.lastTouchedGameTime()
			));
	}
}
