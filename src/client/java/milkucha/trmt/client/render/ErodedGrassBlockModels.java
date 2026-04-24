package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.util.ModelIdentifier;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyModelAfterBake().register((model, context) -> {
                ModelIdentifier mid = context.topLevelId();
                if (mid != null
                        && "trmt".equals(mid.id().getNamespace())
                        && "eroded_grass_block".equals(mid.id().getPath())) {
                    return new ErodedGrassBlockModel(model);
                }
                return model;
            })
        );
    }
}
