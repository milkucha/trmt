package milkucha.trmt.client;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.render.ErodedGrassBlockModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.ArrayList;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "trmt", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TRMTNeoForgeClient {

    private TRMTNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TRMTClientConfig.load();
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageGrassColor(level, pos)
                        : 0x79C05A,
                TRMTBlocks.ERODED_GRASS_BLOCK.get()
        );
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (ModelResourceLocation mid : new ArrayList<>(models.keySet())) {
            if ("trmt".equals(mid.id().getNamespace())
                    && mid.id().getPath().startsWith("eroded_grass_block")) {
                BakedModel original = models.get(mid);
                if (original != null) {
                    models.put(mid, new ErodedGrassBlockModel(original));
                }
            }
        }
    }
}
