package milkucha.trmt.erosion;

import milkucha.trmt.network.TRMTPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TRMTUpdateStagePayload(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime)
        implements CustomPacketPayload {

    public static final Type<TRMTUpdateStagePayload> TYPE = new Type<>(TRMTPackets.UPDATE_STAGE);

    public static final StreamCodec<FriendlyByteBuf, TRMTUpdateStagePayload> CODEC =
            StreamCodec.of(TRMTUpdateStagePayload::encode, TRMTUpdateStagePayload::decode);

    private static void encode(FriendlyByteBuf buf, TRMTUpdateStagePayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeInt(payload.stage());
        buf.writeFloat(payload.walkedOnCount());
        buf.writeFloat(payload.threshold());
        buf.writeLong(payload.lastTouchedGameTime());
    }

    private static TRMTUpdateStagePayload decode(FriendlyByteBuf buf) {
        BlockPos pos   = buf.readBlockPos();
        int   stage    = buf.readInt();
        float walked   = buf.readFloat();
        float thresh   = buf.readFloat();
        long  time     = buf.readLong();
        return new TRMTUpdateStagePayload(pos, stage, walked, thresh, time);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
