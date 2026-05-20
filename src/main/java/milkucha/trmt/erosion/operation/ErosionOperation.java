package milkucha.trmt.erosion.operation;

import java.util.Optional;

@FunctionalInterface
public interface ErosionOperation {
    Optional<TransformContext> apply(TransformContext context);
}
