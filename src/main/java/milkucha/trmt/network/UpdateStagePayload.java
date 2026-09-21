package milkucha.trmt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Single-block stage update. Sent to all players whenever a stage advances or resets. */
public record UpdateStagePayload(BlockPos pos, int stage, float walkedOnCount, float threshold,
                                  long lastTouchedGameTime) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(TRMTPackets.MOD_ID, "update_stage");
    public static final CustomPacketPayload.Type<UpdateStagePayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateStagePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeInt(payload.stage);
                buf.writeFloat(payload.walkedOnCount);
                buf.writeFloat(payload.threshold);
                buf.writeLong(payload.lastTouchedGameTime);
            },
            buf -> new UpdateStagePayload(buf.readBlockPos(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
