package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.util.ModelIdentifier;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyModelAfterBake().register((model, context) -> {
                if (context.id() instanceof ModelIdentifier mid && "trmt".equals(mid.getNamespace())) {
                    if ("eroded_grass_block".equals(mid.getPath())) {
                        return new ErodedGrassBlockModel(model);
                    }
                    // Sunken eroded sand (stage 1-4): top face without AO, see ErodedSandBlockModel.
                    if ("eroded_sand".equals(mid.getPath()) && isSunkenVariant(mid.getVariant())) {
                        return new ErodedSandBlockModel(model);
                    }
                }
                return model;
            })
        );
    }

    /** Block-state variant string, e.g. "facing=south,stage=2"; stage 0 is full height. */
    private static boolean isSunkenVariant(String variant) {
        for (String prop : variant.split(",")) {
            if (prop.startsWith("stage=")) {
                return !"stage=0".equals(prop);
            }
        }
        return false;
    }
}
