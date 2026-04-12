package milkucha.trmt.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Coarse dirt produced by foot-traffic erosion. Visually identical to vanilla coarse dirt
 * but one pixel shorter (15/16 of a block), matching the height of a dirt path block.
 * Never placed by players or generated naturally — only set by the erosion system.
 */
public class ErodedCoarseDirtBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

    public ErodedCoarseDirtBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
