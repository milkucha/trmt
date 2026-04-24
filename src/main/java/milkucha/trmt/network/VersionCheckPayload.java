package milkucha.trmt.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VersionCheckPayload(String version) implements CustomPayload {

    public static final Id<VersionCheckPayload> ID = new Id<>(Identifier.of("trmt", "version_check"));

    public static final PacketCodec<PacketByteBuf, VersionCheckPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeString(payload.version()),
        buf -> new VersionCheckPayload(buf.readString())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
