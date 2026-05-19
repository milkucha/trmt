package milkucha.trmt.erosion.operation;

import java.util.Optional;

public final class ClearIfNoStateOperation implements ErosionOperation {

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        if (context.hasProposedState()) {
            return Optional.of(context);
        }

        ErosionTransformSupport.clearEntry(
                context.manager(),
                context.pos(),
                context.state(),
                context.continueTracking(),
                context.world().getTime()
        );
        return Optional.empty();
    }
}
