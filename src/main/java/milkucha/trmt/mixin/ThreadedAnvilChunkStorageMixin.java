package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a chunk's erosion data to a player right after vanilla sends them the chunk itself.
 * {@code sendChunkDataPackets} is the single point where a full chunk is sent to a player
 * (on watch start and when a watched chunk finishes loading). Fabric API 0.92.7 has no chunk-watch event.
 */
@Mixin(ThreadedAnvilChunkStorage.class)
public class ThreadedAnvilChunkStorageMixin {

    @Inject(method = "sendChunkDataPackets", at = @At("TAIL"))
    private void trmt$syncErosionOnChunkSend(ServerPlayerEntity player, MutableObject<ChunkDataS2CPacket> cachedDataPacket,
                                             WorldChunk chunk, CallbackInfo ci) {
        ErosionMapManager.getInstance().sendChunkSyncToPlayer(player, chunk.getPos());
    }
}
