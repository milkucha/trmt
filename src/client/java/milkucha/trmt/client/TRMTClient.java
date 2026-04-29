package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.mixin.BlockRenderLayersAccessor;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import milkucha.trmt.network.VersionCheckPayload;
import milkucha.trmt.network.VersionResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TRMTClientConfig.load();

		// Respond to the server's configuration-phase version query with our own version.
		ClientConfigurationNetworking.registerGlobalReceiver(VersionCheckPayload.ID, (payload, context) -> {
			String myVersion = FabricLoader.getInstance().getModContainer(TRMT.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
			context.responseSender().sendPacket(new VersionResponsePayload(myVersion));
		});

		BlockRenderLayersAccessor.trmt$getBlocks().put(TRMTBlocks.ERODED_GRASS_BLOCK, BlockRenderLayer.CUTOUT);
		ColorProviderRegistry.BLOCK.register(
				(state, world, pos, tintIndex) -> world != null && pos != null
						? BiomeColors.getGrassColor(world, pos)
						: 0x79C05A,
				TRMTBlocks.ERODED_GRASS_BLOCK
		);
		ErosionDebugHud.register();

		// Full chunk sync received on join.
		ClientPlayNetworking.registerGlobalReceiver(SyncChunkPayload.ID, (payload, context) -> {
			Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(payload.entries().size());
			for (SyncChunkPayload.Entry e : payload.entries()) {
				chunkEntries.put(e.pos(), new ClientErosionCache.Entry(e.stage(), e.walkedOnCount(), e.threshold(), e.lastTouchedGameTime()));
			}
			ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
			context.client().execute(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
		});

		// Single-block stage update (advance or reset).
		ClientPlayNetworking.registerGlobalReceiver(UpdateStagePayload.ID, (payload, context) ->
			context.client().execute(() ->
				ClientErosionCache.getInstance().setEntry(payload.pos(), payload.stage(), payload.walkedOnCount(), payload.threshold(), payload.lastTouchedGameTime())
			)
		);

		// Clear cached stages when disconnecting so stale data never leaks into the next session.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ClientErosionCache.getInstance().clear());
	}
}
