package milkucha.trmt.client;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.render.ErodedGrassBlockModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class TRMTForgeClient {

    private TRMTForgeClient() {}

    public static void register(BusGroup busGroup) {
        FMLClientSetupEvent.getBus(busGroup).addListener(TRMTForgeClient::onClientSetup);
        RegisterColorHandlersEvent.Block.getBus(busGroup).addListener(TRMTForgeClient::onRegisterBlockColors);
        ModelEvent.ModifyBakingResult.getBus(busGroup).addListener(TRMTForgeClient::onModifyBakingResult);
        AddGuiOverlayLayersEvent.BUS.addListener(TRMTForgeClient::onAddGuiOverlayLayers);
    }

    private static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
            Identifier.fromNamespaceAndPath("trmt", "erosion_debug_hud"),
            (gg, dt) -> ErosionDebugHud.render(gg)
        );
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        TRMTClientConfig.load();
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_GRASS_BLOCK.get(), ChunkSectionLayer.CUTOUT);
            ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_SAND.get(), ChunkSectionLayer.CUTOUT);
        });
    }

    private static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageGrassColor(level, pos)
                        : 0x79C05A,
                TRMTBlocks.ERODED_GRASS_BLOCK.get()
        );
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getResults().blockStateModels();
        for (BlockState state : TRMTBlocks.ERODED_GRASS_BLOCK.get().getStateDefinition().getPossibleStates()) {
            BlockStateModel original = models.get(state);
            if (original != null) {
                models.put(state, new ErodedGrassBlockModel(original));
            }
        }
    }
}
