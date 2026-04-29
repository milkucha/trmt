package milkucha.trmt.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VersionResponsePayload(String version) implements CustomPacketPayload {

    public static final Type<VersionResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("trmt", "version_response"));

    public static final StreamCodec<FriendlyByteBuf, VersionResponsePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.version()),
        buf -> new VersionResponsePayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
