package milkucha.trmt.client;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.render.ErodedGrassBlockModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "trmt", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TRMTForgeClient {

    private TRMTForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TRMTClientConfig.load();
        event.enqueueWork(() -> {
            // Register render layers so transparent pixels in the eroded grass overlay
            // and eroded sand are handled correctly.
            ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_GRASS_BLOCK.get(), RenderType.cutoutMipped());
            // ERODED_SAND: nonOpaque block defaults to SOLID without this, causing white pixels when breaking.
            ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_SAND.get(), RenderType.cutoutMipped());
        });
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        // Apply biome grass tint to the eroded_top overlay quad on eroded grass blocks.
        event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageGrassColor(level, pos)
                        : 0x79C05A,
                TRMTBlocks.ERODED_GRASS_BLOCK.get()
        );
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("erosion_debug_hud",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                        ErosionDebugHud.render(guiGraphics));
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        // Iterate over a copy to avoid ConcurrentModificationException.
        for (ResourceLocation loc : new ArrayList<>(models.keySet())) {
            if ("trmt".equals(loc.getNamespace())
                    && loc.getPath().startsWith("eroded_grass_block")) {
                BakedModel original = models.get(loc);
                if (original != null) {
                    models.put(loc, new ErodedGrassBlockModel(original));
                }
            }
        }
    }
}
