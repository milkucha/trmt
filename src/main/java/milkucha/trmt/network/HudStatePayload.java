package milkucha.trmt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the client's debug HUD state. The server only sends erosion sync while it is on. */
public record HudStatePayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<HudStatePayload> ID = new Type<>(TRMTPackets.HUD_STATE);

    public static final StreamCodec<RegistryFriendlyByteBuf, HudStatePayload> CODEC = StreamCodec.ofMember(
        (payload, buf) -> buf.writeBoolean(payload.enabled()),
        buf -> new HudStatePayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
