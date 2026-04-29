package milkucha.trmt.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ColoredFallingBlock.class)
public abstract class SandBlockMixin extends Block {

    private static final VoxelShape SAND_COLLISION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    protected SandBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SAND_COLLISION_SHAPE;
    }
}
