package milkucha.trmt.client.render;

import milkucha.trmt.TRMTBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyBlockModelAfterBake().register(ModelModifier.WRAP_LAST_PHASE,
                    (model, context) -> context.state().is(TRMTBlocks.ERODED_GRASS_BLOCK)
                            ? new ErodedGrassBlockModel(model)
                            : model)
        );
    }
}
