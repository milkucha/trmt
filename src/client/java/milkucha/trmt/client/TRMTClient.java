package milkucha.trmt.client;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.erosion.TRMTSyncChunkPayload;
import milkucha.trmt.erosion.TRMTUpdateStagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TRMTClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TRMTClientConfig.load();

        ErodedGrassBlockModels.register();
        BlockColorRegistry.register(List.of(BlockTintSources.grassBlock()), TRMTBlocks.ERODED_GRASS_BLOCK);
        ErosionDebugHud.register();

        ClientPlayNetworking.registerGlobalReceiver(TRMTSyncChunkPayload.TYPE, (payload, context) -> {
            ChunkPos chunkPos = payload.chunkPos();
            Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>();
            payload.entries().forEach((pos, entry) ->
                chunkEntries.put(pos, new ClientErosionCache.Entry(
                    entry.getErosionStage(),
                    entry.getWalkedOnCount(),
                    entry.getThreshold(),
                    entry.getLastTouchedGameTime()
                ))
            );
            context.client().execute(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
        });

        ClientPlayNetworking.registerGlobalReceiver(TRMTUpdateStagePayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                ClientErosionCache.getInstance().setEntry(
                    payload.pos(), payload.stage(),
                    payload.walkedOnCount(), payload.threshold(),
                    payload.lastTouchedGameTime()
                )
            );
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientErosionCache.getInstance().clear());
    }
}
