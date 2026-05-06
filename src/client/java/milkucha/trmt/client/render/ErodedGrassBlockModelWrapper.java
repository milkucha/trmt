package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.WrapperGroupableModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.GroupableModel;

public class ErodedGrassBlockModelWrapper extends WrapperGroupableModel {

    public ErodedGrassBlockModelWrapper(GroupableModel wrapped) {
        super(wrapped);
    }

    @Override
    public BakedModel bake(Baker baker) {
        return new ErodedGrassBlockModel(wrapped.bake(baker));
    }
}
