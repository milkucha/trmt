package milkucha.trmt.client;

import milkucha.trmt.client.network.ClientErosionCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "trmt", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TRMTNeoForgeClientEvents {

    private TRMTNeoForgeClientEvents() {}

    @SubscribeEvent
    public static void onPlayerLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientErosionCache.getInstance().clear();
    }
}
