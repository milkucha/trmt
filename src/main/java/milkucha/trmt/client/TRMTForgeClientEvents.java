package milkucha.trmt.client;

import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "trmt", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TRMTForgeClientEvents {

    private TRMTForgeClientEvents() {}

    /**
     * /trmt-hud: toggles the client-side debug HUD, saved to trmt-client.json.
     * Separate root from the server's /trmt: Forge's ClientCommandHandler runs a client command whenever
     * its root matches and only forwards unknown roots to the server, so a client /trmt would swallow
     * /trmt convert-to-vanilla etc.
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trmt-hud")
                .executes(ctx -> setHud(ctx.getSource(), !TRMTClientConfig.get().debugHud)));
    }

    private static int setHud(CommandSourceStack source, boolean enabled) {
        ErosionDebugHud.setEnabled(enabled);
        TRMTClientConfig.save();
        source.sendSuccess(() -> Component.literal("[TRMT] Debug HUD " + (enabled ? "on." : "off.")), false);
        return 1;
    }

    /**
     * Tell the server the HUD is on so it starts sending erosion data (nothing is sent while it is off).
     * LoggingIn is posted from ClientPacketListener.handleLogin (play phase), after the FML handshake,
     * so CHANNEL.isRemotePresent is already valid.
     */
    @SubscribeEvent
    public static void onPlayerLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (TRMTClientConfig.get().debugHud) {
            ErosionDebugHud.sendHudState(event.getConnection(), true);
        }
    }

    /**
     * Drop a chunk's cached entries whenever it (re)loads or unloads, so re-watching a chunk never shows
     * stale data. ClientChunkCache.replaceWithPacketData posts ChunkEvent.Load while the chunk packet is
     * handled on the render thread; our sync_chunk handler's enqueueWork is queued on the same thread after it.
     * ChunkEvent is also posted for server levels (same bus in single-player), hence the isClientSide check.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            ClientErosionCache.getInstance().clearChunk(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            ClientErosionCache.getInstance().clearChunk(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Clear cached erosion stages on disconnect so stale data never leaks into the next session.
        ClientErosionCache.getInstance().clear();
    }
}
