package milkucha.trmt.erosion.operation;

import milkucha.trmt.erosion.ErosionThresholdRange;
import net.minecraft.block.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public record TransformRule(
        Predicate<BlockState> matcher,
        Optional<ErosionThresholdRange> threshold,
        List<ErosionOperation> operations
) {

    public boolean matches(BlockState state) {
        return matcher.test(state);
    }

    public boolean tracks(BlockState state) {
        return matches(state) && operations.stream()
                .filter(TrackingConditionOperation.class::isInstance)
                .map(TrackingConditionOperation.class::cast)
                .allMatch(TrackingConditionOperation::allowsTracking);
    }

    public void apply(TransformContext context) {
        Optional<TransformContext> current = Optional.of(context);
        for (ErosionOperation operation : operations) {
            current = current.flatMap(operation::apply);
            if (current.isEmpty()) {
                return;
            }
        }
    }
}
