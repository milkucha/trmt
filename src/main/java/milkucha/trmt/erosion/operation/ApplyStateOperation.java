package milkucha.trmt.erosion.operation;

import net.minecraft.block.Block;

import java.util.Optional;

public final class ApplyStateOperation implements ErosionOperation {

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        if (!context.hasProposedState()) {
            return Optional.of(context);
        }

        context.world().setBlockState(context.pos(), context.nextState(), Block.NOTIFY_ALL);
        ErosionTransformSupport.clearEntryWithCurrent(
                context.manager(),
                context.pos(),
                context.state(),
                context.nextState(),
                context.continueTracking(),
                context.world().getTime()
        );
        return Optional.empty();
    }
}
