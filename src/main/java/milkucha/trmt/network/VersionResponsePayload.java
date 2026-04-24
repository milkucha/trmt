package milkucha.trmt.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VersionResponsePayload(String version) implements CustomPayload {

    public static final Id<VersionResponsePayload> ID = new Id<>(Identifier.of("trmt", "version_response"));

    public static final PacketCodec<PacketByteBuf, VersionResponsePayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeString(payload.version()),
        buf -> new VersionResponsePayload(buf.readString())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
