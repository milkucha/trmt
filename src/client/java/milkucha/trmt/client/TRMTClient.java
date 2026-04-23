package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TRMTClientConfig.load();

		// Respond to the server's login version query with our own version.
		ClientLoginNetworking.registerGlobalReceiver(TRMTPackets.VERSION_CHECK, (client, handler, buf, listenerAdder) -> {
			buf.readString(32767); // consume server version — server makes the comparison decision
			PacketByteBuf response = PacketByteBufs.create();
			response.writeString(
				FabricLoader.getInstance().getModContainer(TRMT.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString())
					.orElse("0.0.0")
			);
			return CompletableFuture.completedFuture(response);
		});

		ErodedGrassBlockModels.register();
		// Eroded grass models have transparent overlay pixels — must use CUTOUT_MIPPED.
		BlockRenderLayerMap.INSTANCE.putBlock(TRMTBlocks.ERODED_GRASS_BLOCK, RenderLayer.getCutoutMipped());
		// Apply biome grass tint (same as vanilla grass_block) so eroded grass is not gray.
		ColorProviderRegistry.BLOCK.register(
				(state, world, pos, tintIndex) -> world != null && pos != null
						? BiomeColors.getGrassColor(world, pos)
						: 0x79C05A, // default plains grass color fallback (item rendering)
				TRMTBlocks.ERODED_GRASS_BLOCK
		);
		ErosionDebugHud.register();

		// Full chunk sync received on join.
		ClientPlayNetworking.registerGlobalReceiver(TRMTPackets.SYNC_CHUNK, (client, handler, buf, responseSender) -> {
			int chunkX = buf.readInt();
			int chunkZ = buf.readInt();
			int count  = buf.readInt();
			Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(count);
			for (int i = 0; i < count; i++) {
				BlockPos pos                = buf.readBlockPos();
				int      stage              = buf.readInt();
				float    walkedOnCount      = buf.readFloat();
				float    threshold          = buf.readFloat();
				long     lastTouchedGameTime = buf.readLong();
				chunkEntries.put(pos, new ClientErosionCache.Entry(stage, walkedOnCount, threshold, lastTouchedGameTime));
			}
			ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
			client.execute(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
		});

		// Single-block stage update (advance or reset).
		ClientPlayNetworking.registerGlobalReceiver(TRMTPackets.UPDATE_STAGE, (client, handler, buf, responseSender) -> {
			BlockPos pos                = buf.readBlockPos();
			int      stage              = buf.readInt();
			float    walkedOnCount      = buf.readFloat();
			float    threshold          = buf.readFloat();
			long     lastTouchedGameTime = buf.readLong();
			client.execute(() ->
				ClientErosionCache.getInstance().setEntry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime)
			);
		});

		// Clear cached stages when disconnecting so stale data never leaks into the next session.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ClientErosionCache.getInstance().clear());
	}
}
