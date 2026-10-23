package milkucha.trmt.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: the client's debug HUD state. The server only sends erosion sync while it is on. */
public record HudStatePayload(boolean enabled) implements CustomPayload {

    public static final Id<HudStatePayload> ID = new Id<>(TRMTPackets.HUD_STATE);

    public static final PacketCodec<RegistryByteBuf, HudStatePayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeBoolean(payload.enabled()),
        buf -> new HudStatePayload(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
