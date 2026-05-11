package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.network.TRMTPackets;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TRMTClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		TRMTClientConfig.load();

		// Register login version sync (Legacy Identifier-based Login Networking)
		ClientLoginNetworking.registerGlobalReceiver(TRMTPackets.VERSION_CHECK,
				(client, handler, buf, listenerAdder) -> {
					String serverVersion = buf.readString();
					TRMT.LOGGER.info("Server TRMT version: {}", serverVersion);

					PacketByteBuf response = PacketByteBufs.create();
					// VERSION check response (legacy)
					response.writeString("1.0.0"); // Hardcoded or get from TRMT
					return CompletableFuture.completedFuture(response);
				});

		// Register play-channel receivers (Modern CustomPayload system)
		ClientPlayNetworking.registerGlobalReceiver(SyncChunkPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				// Map SyncChunkPayload entries to ClientErosionCache entries
				Map<BlockPos, ClientErosionCache.Entry> entries = new HashMap<>();
				payload.entries().forEach((pos, entry) -> {
					if (entry != null) {
						entries.put(pos, new ClientErosionCache.Entry(entry.getErosionStage(), entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime()));
					}
				});
				ClientErosionCache.getInstance().setChunk(payload.chunkPos(), entries);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(UpdateStagePayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				ClientErosionCache.getInstance().setEntry(payload.pos(), payload.stage(), payload.walkedOnCount(),
						payload.threshold(), payload.lastTouchedGameTime());
			});
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientErosionCache.getInstance().clear();
		});

		ErodedGrassBlockModels.register();
		
		// Eroded grass models have transparent overlay pixels — must use CUTOUT.
		BlockRenderLayerMap.putBlock(TRMTBlocks.ERODED_GRASS_BLOCK, BlockRenderLayer.CUTOUT);
		
		// Apply biome grass tint (same as vanilla grass_block) so eroded grass is not
		// gray.
		ColorProviderRegistry.BLOCK.register(
				(state, world, pos, tintIndex) -> pos != null ? BiomeColors.getGrassColor(world, pos) : -1,
				TRMTBlocks.ERODED_GRASS_BLOCK);

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ErosionDebugHud.register();
		}
	}
}
