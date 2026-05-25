package milkucha.trmt.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ColoredFallingBlock.class)
public abstract class SandBlockMixin extends Block {

    private static final VoxelShape SAND_COLLISION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    protected SandBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SAND_COLLISION_SHAPE;
    }
}
