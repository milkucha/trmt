package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Predicate;

public class ErodedGrassBlockModel extends WrapperBlockStateModel {

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockRenderView world, BlockPos pos, BlockState state,
                          Random random, Predicate<Direction> cullTest) {
        emitter.pushTransform(quad -> {
            if (quad.nominalFace() != Direction.DOWN) {
                quad.renderLayer(BlockRenderLayer.CUTOUT);
            }
            return true;
        });
        super.emitQuads(emitter, world, pos, state, random, cullTest);
        emitter.popTransform();
    }
}
