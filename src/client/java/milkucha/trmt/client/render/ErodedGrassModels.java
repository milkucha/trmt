package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;

/**
 * Registers the 5 eroded grass block models (stages 1–5) and installs the
 * GrassErosionProxyModel over the vanilla grass_block model at bake time.
 */
public final class ErodedGrassModels {

    private static final Identifier[] MODEL_IDS = new Identifier[5];
    static {
        for (int i = 0; i < 5; i++) {
            MODEL_IDS[i] = new Identifier("trmt", "block/grass_block_eroded_" + i);
        }
    }

    private ErodedGrassModels() {}

    public static void register() {
        ModelLoadingPlugin.register(pluginContext -> {
            // Load the 5 eroded stage models.
            for (Identifier id : MODEL_IDS) {
                pluginContext.addModels(id);
            }

            // After baking, wrap the vanilla grass_block model with our proxy.
            ModelIdentifier grassFalse = new ModelIdentifier("minecraft", "grass_block", "snowy=false");
            ModelIdentifier grassTrue  = new ModelIdentifier("minecraft", "grass_block", "snowy=true");
            pluginContext.modifyModelAfterBake().register((model, context) -> {
                if (context.id().equals(grassFalse) || context.id().equals(grassTrue)) {
                    return new GrassErosionProxyModel(model);
                }
                return model;
            });
        });
    }

    /**
     * Returns the baked model for the given erosion stage (1–5).
     * Stage 1 → eroded_0 model, ..., stage 5 → eroded_4 model.
     */
    public static BakedModel getModel(int stage) {
        if (stage < 1 || stage > 5) return null;
        return MinecraftClient.getInstance().getBakedModelManager().getModel(MODEL_IDS[stage - 1]);
    }
}
