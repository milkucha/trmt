package milkucha.trmt.client;

import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassModels;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;


public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ErodedGrassModels.register();
		// Eroded grass models have transparent holes — must use CUTOUT_MIPPED.
		BlockRenderLayerMap.INSTANCE.putBlock(Blocks.GRASS_BLOCK, RenderLayer.getCutoutMipped());
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
			client.execute(() -> {
				ClientErosionCache.getInstance().setEntry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
				if (client.world != null) {
					client.world.scheduleBlockRerenderIfNeeded(
						pos,
						Blocks.AIR.getDefaultState(),
						client.world.getBlockState(pos)
					);
				}
			});
		});

		// Clear cached stages when disconnecting so stale data never leaks into the next session.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ClientErosionCache.getInstance().clear());
	}
}
