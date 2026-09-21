package milkucha.trmt.client.render;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;

/**
 * FabricBlockStateModel wrapper for ErodedGrassBlock block-state models.
 * Applies the CUTOUT chunk layer to every non-DOWN quad so that transparent pixels in
 * the eroded-top overlay and the grass_block_side_overlay are discarded correctly.
 * Stage texture selection and FACING Y-rotation are already baked into the wrapped
 * model by the block-state system, so this class needs no per-position lookups.
 *
 * <p>{@link FabricBlockStateModel} is automatically implemented on every {@link BlockStateModel}
 * via Mixin interface injection, so {@code emitQuads} below is a valid {@code @Override} even
 * though this class only declares {@code implements BlockStateModel}.
 */
public class ErodedGrassBlockModel implements BlockStateModel {

    private final BlockStateModel wrapped;

    public ErodedGrassBlockModel(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
        wrapped.collectParts(random, output);
    }

    @Override
    public Material.Baked particleMaterial() {
        return wrapped.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return wrapped.materialFlags();
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                           RandomSource random, Predicate<Direction> cullTest) {
        emitter.pushTransform(quad -> {
            if (quad.nominalFace() != Direction.DOWN) {
                quad.chunkLayer(ChunkSectionLayer.CUTOUT);
            }
            return true;
        });
        ((FabricBlockStateModel) wrapped).emitQuads(emitter, level, pos, state, random, cullTest);
        emitter.popTransform();
    }
}
