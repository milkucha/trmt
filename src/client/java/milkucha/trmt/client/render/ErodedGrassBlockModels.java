package milkucha.trmt.client.render;

import milkucha.trmt.TRMTBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyBlockModelOnLoad()
                .register((model, context) -> {
                    if (context.state() != null
                            && context.state().isOf(TRMTBlocks.ERODED_GRASS_BLOCK)) {
                        return new ErodedGrassBlockModelWrapper(model);
                    }
                    return model;
                })
        );
    }
}
