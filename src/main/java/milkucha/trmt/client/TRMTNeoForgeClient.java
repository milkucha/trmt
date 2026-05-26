package milkucha.trmt.client;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.render.ErodedGrassBlockModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.Map;

@EventBusSubscriber(modid = "trmt", value = Dist.CLIENT)
public final class TRMTNeoForgeClient {

    private TRMTNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TRMTClientConfig.load();
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_GRASS_BLOCK.get(), ChunkSectionLayer.CUTOUT);
            ItemBlockRenderTypes.setRenderLayer(TRMTBlocks.ERODED_SAND.get(), ChunkSectionLayer.CUTOUT);
        });
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
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (BlockState state : TRMTBlocks.ERODED_GRASS_BLOCK.get().getStateDefinition().getPossibleStates()) {
            BlockStateModel original = models.get(state);
            if (original != null) {
                models.put(state, new ErodedGrassBlockModel(original));
            }
        }
    }
}
