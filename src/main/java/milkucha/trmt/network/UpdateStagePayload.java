package milkucha.trmt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateStagePayload(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) implements CustomPacketPayload {

    public static final Type<UpdateStagePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("trmt", "update_stage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateStagePayload> CODEC = StreamCodec.ofMember(
        (payload, buf) -> {
            BlockPos.STREAM_CODEC.encode(buf, payload.pos());
            buf.writeInt(payload.stage());
            buf.writeFloat(payload.walkedOnCount());
            buf.writeFloat(payload.threshold());
            buf.writeLong(payload.lastTouchedGameTime());
        },
        buf -> new UpdateStagePayload(
            BlockPos.STREAM_CODEC.decode(buf),
            buf.readInt(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readLong()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
