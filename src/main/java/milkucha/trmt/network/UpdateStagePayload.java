package milkucha.trmt.network;

import milkucha.trmt.TRMT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Single-block stage update broadcast. */
public record UpdateStagePayload(
	BlockPos pos,
	int stage,
	float walkedOnCount,
	float threshold,
	long lastTouchedGameTime
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<UpdateStagePayload> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TRMT.MOD_ID, "update_stage"));

	public static final StreamCodec<FriendlyByteBuf, UpdateStagePayload> STREAM_CODEC =
		StreamCodec.of((buf, p) -> p.write(buf), UpdateStagePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		buf.writeInt(stage);
		buf.writeFloat(walkedOnCount);
		buf.writeFloat(threshold);
		buf.writeLong(lastTouchedGameTime);
	}

	private static UpdateStagePayload read(FriendlyByteBuf buf) {
		return new UpdateStagePayload(
			buf.readBlockPos(),
			buf.readInt(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readLong()
		);
	}

	@Override
	public CustomPacketPayload.Type<UpdateStagePayload> type() {
		return TYPE;
	}
}
