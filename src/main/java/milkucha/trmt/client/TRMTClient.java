package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = TRMT.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TRMTClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TRMTClientConfig.load();
        NeoForge.EVENT_BUS.addListener(ErosionDebugHud::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) ->
            ClientErosionCache.getInstance().clear());
    }

    @SubscribeEvent
    public static void onRegisterColorHandlers(RegisterColorHandlersEvent.Block event) {
        event.register(
            (state, level, pos, tintIndex) -> level != null && pos != null
                ? BiomeColors.getAverageGrassColor(level, pos)
                : 0x79C05A,
            TRMTBlocks.ERODED_GRASS_BLOCK.get()
        );
    }
}
