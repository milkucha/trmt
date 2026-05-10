package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

public class ErodedGrassBlockModel extends WrapperBlockStateModel {

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        emitter.pushTransform(quad -> {
            if (quad.nominalFace() != Direction.DOWN) {
                quad.chunkLayer(ChunkSectionLayer.CUTOUT);
            }
            return true;
        });
        super.emitQuads(emitter, world, pos, state, random, cullTest);
        emitter.popTransform();
    }
}
