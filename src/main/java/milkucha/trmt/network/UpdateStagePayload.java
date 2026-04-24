package milkucha.trmt.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record UpdateStagePayload(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) implements CustomPayload {

    public static final Id<UpdateStagePayload> ID = new Id<>(Identifier.of("trmt", "update_stage"));

    public static final PacketCodec<RegistryByteBuf, UpdateStagePayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            BlockPos.PACKET_CODEC.encode(buf, payload.pos());
            buf.writeInt(payload.stage());
            buf.writeFloat(payload.walkedOnCount());
            buf.writeFloat(payload.threshold());
            buf.writeLong(payload.lastTouchedGameTime());
        },
        buf -> new UpdateStagePayload(
            BlockPos.PACKET_CODEC.decode(buf),
            buf.readInt(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readLong()
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
