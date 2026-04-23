package milkucha.trmt.mixin;

import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.block.SandBlock")
public abstract class SandBlockMixin extends Block {

    protected SandBlockMixin(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return ErodedSandBlock.SUNKEN_SHAPE;
    }
}
