package milkucha.trmt.erosion.operation;

import java.util.Optional;

public final class StopTrackingOperation implements ErosionOperation {

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        return Optional.of(context.stopTracking());
    }
}
