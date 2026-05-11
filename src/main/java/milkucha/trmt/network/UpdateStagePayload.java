package milkucha.trmt.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Payload for a single-block stage update.
 */
public record UpdateStagePayload(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) implements CustomPayload {
    public static final Id<UpdateStagePayload> ID = new Id<>(Identifier.of("trmt", "update_stage"));
    
    public static final PacketCodec<PacketByteBuf, UpdateStagePayload> CODEC = CustomPayload.codecOf(
            (value, buf) -> {
                buf.writeBlockPos(value.pos());
                buf.writeInt(value.stage());
                buf.writeFloat(value.walkedOnCount());
                buf.writeFloat(value.threshold());
                buf.writeLong(value.lastTouchedGameTime());
            },
            buf -> new UpdateStagePayload(
                    buf.readBlockPos(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readLong()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
