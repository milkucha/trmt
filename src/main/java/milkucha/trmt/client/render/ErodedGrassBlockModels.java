package milkucha.trmt.client.render;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;

public final class ErodedGrassBlockModels {

    private ErodedGrassBlockModels() {}

    public static void modifyModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : models.entrySet()) {
            ModelResourceLocation mrl = entry.getKey();
            if ("trmt".equals(mrl.id().getNamespace())
                    && "eroded_grass_block".equals(mrl.id().getPath())) {
                entry.setValue(new ErodedGrassBlockModel(entry.getValue()));
            }
        }
    }
}
