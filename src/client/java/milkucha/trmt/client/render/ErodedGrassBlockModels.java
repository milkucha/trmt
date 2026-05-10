package milkucha.trmt.client.render;

import milkucha.trmt.TRMTBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyBlockModelAfterBake()
                .register((model, context) -> {
                    if (context.state() != null
                            && context.state().is(TRMTBlocks.ERODED_GRASS_BLOCK)) {
                        return new ErodedGrassBlockModel(model);
                    }
                    return model;
                })
        );
    }
}
