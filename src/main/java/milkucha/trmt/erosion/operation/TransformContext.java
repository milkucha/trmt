package milkucha.trmt.erosion.operation;

import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record TransformContext(
        World world,
        ErosionMapManager manager,
        BlockPos pos,
        BlockState state,
        BlockState nextState,
        boolean continueTracking
) {

    public TransformContext(World world, ErosionMapManager manager, BlockPos pos, BlockState state) {
        this(world, manager, pos, state, null, true);
    }

    public boolean hasProposedState() {
        return nextState != null;
    }

    public TransformContext propose(BlockState proposedState) {
        return new TransformContext(world, manager, pos, state, proposedState, continueTracking);
    }

    public TransformContext stopTracking() {
        return new TransformContext(world, manager, pos, state, nextState, false);
    }
}
