package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a chunk's erosion data to a player right after vanilla sends them the chunk itself.
 * Chunks are sent in batches by {@link PlayerChunkSender#sendNextChunks}; the private static
 * {@code sendChunk(ServerGamePacketListenerImpl, ServerLevel, LevelChunk)} is the only place on the server
 * that builds and sends a {@code ClientboundLevelChunkWithLightPacket}. Injecting at TAIL puts our packet after
 * the chunk packet on the same connection, so the client's clear-on-chunk-load always runs before our data arrives.
 * (Named after the Yarn class, {@code ChunkDataSender}, like the other mixins on this branch.)
 */
@Mixin(PlayerChunkSender.class)
public class ChunkDataSenderMixin {

    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void trmt$syncErosionOnChunkSend(ServerGamePacketListenerImpl handler, ServerLevel level,
                                                    LevelChunk chunk, CallbackInfo ci) {
        ErosionMapManager.getInstance().sendChunkSyncToPlayer(handler.player, chunk.getPos());
    }
}
