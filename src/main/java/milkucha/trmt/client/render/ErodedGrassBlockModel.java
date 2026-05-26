package milkucha.trmt.client.render;

import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import java.util.Collection;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ErodedGrassBlockModel implements BlockStateModel {

    private final BlockStateModel wrapped;

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> list) {
        wrapped.collectParts(random, list);
    }

    @Override
    public TextureAtlasSprite particleIcon() {
        return wrapped.particleIcon();
    }

    @Override
    public Collection<ChunkSectionLayer> getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return List.of(ChunkSectionLayer.CUTOUT);
    }
}
