package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

/**
 * Block-state model wrapper for the sunken eroded sand states (stage 1-4).
 * The model keeps ambient occlusion (side faces match vanilla neighbours), but the
 * lowered top face is rendered flat-lit: vanilla-style AO on an inset top face samples
 * the full-cube neighbours at the face's own level and darkens it by ~40-60 %.
 * Only the quad's AO flag changes; the render layer is untouched.
 */
public class ErodedSandBlockModel extends WrapperBlockStateModel {

    public ErodedSandBlockModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        emitter.pushTransform(quad -> {
            if (quad.nominalFace() == Direction.UP) {
                quad.ambientOcclusion(TriState.FALSE);
            }
            return true;
        });
        super.emitQuads(emitter, world, pos, state, random, cullTest);
        emitter.popTransform();
    }
}
