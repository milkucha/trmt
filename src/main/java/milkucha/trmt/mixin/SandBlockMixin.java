package milkucha.trmt.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.block.SandBlock")
public abstract class SandBlockMixin extends Block {

    private static final VoxelShape SAND_COLLISION_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    protected SandBlockMixin(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SAND_COLLISION_SHAPE;
    }
}
