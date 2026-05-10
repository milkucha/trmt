package milkucha.trmt.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VersionCheckPayload(String version) implements CustomPacketPayload {

    public static final Type<VersionCheckPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("trmt", "version_check"));

    public static final StreamCodec<FriendlyByteBuf, VersionCheckPayload> CODEC = StreamCodec.ofMember(
        (payload, buf) -> buf.writeUtf(payload.version()),
        buf -> new VersionCheckPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
