package milkucha.trmt.erosion.operation;

import milkucha.trmt.TRMTConfig;

import java.util.Optional;

public final class RequiresConfigOperation implements ErosionOperation, TrackingConditionOperation {

    private final String key;

    public RequiresConfigOperation(String key) {
        this.key = key;
    }

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        return allowsTracking() ? Optional.of(context) : Optional.empty();
    }

    @Override
    public boolean allowsTracking() {
        TRMTConfig config = TRMTConfig.get();
        return switch (key) {
            case "erosion.grassEnabled" -> config.erosion.grassEnabled;
            case "erosion.dirtEnabled" -> config.erosion.dirtEnabled;
            case "erosion.sandEnabled" -> config.erosion.sandEnabled;
            case "erosion.leavesEnabled" -> config.erosion.leavesEnabled;
            case "erosion.vegetationEnabled" -> config.erosion.vegetationEnabled;
            default -> throw new IllegalArgumentException("Unknown TRMT config key: " + key);
        };
    }
}
