package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.function.Predicate;

/**
 * BlockStateModel wrapper for ErodedGrassBlock block-state models for 1.21.2+.
 * Delegates rendering to the wrapped model.
 */
public class ErodedGrassBlockModel implements BlockStateModel, FabricBlockStateModel {

    private final BlockStateModel wrapped;

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void addParts(Random random, List<BlockModelPart> parts) {
        wrapped.addParts(random, parts);
    }

    @Override
    public Sprite particleSprite() {
        return wrapped.particleSprite();
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockRenderView world, BlockPos pos, BlockState state, Random random, Predicate<Direction> facePredicate) {
        // Delegate to the wrapped model's FRAPI implementation if it exists
        if (wrapped instanceof FabricBlockStateModel fabricWrapped) {
            fabricWrapped.emitQuads(emitter, world, pos, state, random, facePredicate);
        }
    }
}
