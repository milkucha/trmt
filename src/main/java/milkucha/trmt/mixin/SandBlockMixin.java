package milkucha.trmt.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.block.SandBlock")
public abstract class SandBlockMixin extends Block {

    private static final VoxelShape SAND_COLLISION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    protected SandBlockMixin(Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SAND_COLLISION_SHAPE;
    }
}
