package milkucha.trmt.client.render;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

public class ErodedGrassBlockModel extends DelegateBlockStateModel {

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        super(wrapped);
    }
}
