package milkucha.trmt.client;

import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "trmt", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TRMTForgeClientEvents {

    private TRMTForgeClientEvents() {}

    @SubscribeEvent
    public static void onPlayerLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Clear cached erosion stages on disconnect so stale data never leaks into the next session.
        ClientErosionCache.getInstance().clear();
    }
}
