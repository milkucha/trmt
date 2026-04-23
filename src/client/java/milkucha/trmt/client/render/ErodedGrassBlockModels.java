package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.util.ModelIdentifier;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyModelAfterBake().register((model, context) -> {
                if (context.id() instanceof ModelIdentifier mid
                        && "trmt".equals(mid.getNamespace())
                        && "eroded_grass_block".equals(mid.getPath())) {
                    return new ErodedGrassBlockModel(model);
                }
                return model;
            })
        );
    }
}
