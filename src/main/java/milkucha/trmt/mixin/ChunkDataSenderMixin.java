package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a chunk's erosion data to a player right after vanilla sends them the chunk itself.
 * Since 1.20.2 chunks are sent in batches by {@link ChunkDataSender#sendChunkBatches}; the private static
 * {@code sendChunkData(ServerPlayNetworkHandler, ServerWorld, WorldChunk)} is the only place on the server
 * that builds and sends a {@code ChunkDataS2CPacket}. Injecting at TAIL puts our packet after the chunk
 * packet on the same connection, so the client's clear-on-chunk-load always runs before our data arrives.
 * Fabric API 0.116.x has no chunk-watch event.
 */
@Mixin(ChunkDataSender.class)
public class ChunkDataSenderMixin {

    @Inject(method = "sendChunkData", at = @At("TAIL"))
    private static void trmt$syncErosionOnChunkSend(ServerPlayNetworkHandler handler, ServerWorld world,
                                                    WorldChunk chunk, CallbackInfo ci) {
        ErosionMapManager.get(world).sendChunkSyncToPlayer(handler.player, chunk.getPos());
    }
}
