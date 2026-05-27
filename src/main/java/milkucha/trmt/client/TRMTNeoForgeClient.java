package milkucha.trmt.client;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.render.ErodedGrassBlockModel;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "trmt", value = Dist.CLIENT)
public final class TRMTNeoForgeClient {

    private TRMTNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TRMTClientConfig.load();
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(
                List.of(BlockTintSources.grassBlock()),
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

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        ErosionDebugHud.render(event.getGuiGraphics());
    }
}
