package milkucha.trmt.erosion.operation;

import java.util.Optional;

public final class RequiresAirOperation implements ErosionOperation {

    private final String side;

    public RequiresAirOperation(String side) {
        this.side = side;
    }

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        boolean passes = switch (side) {
            case "top" -> context.world().getBlockState(context.pos().up()).isAir();
            default -> throw new IllegalArgumentException("Unsupported requires_air side: " + side);
        };
        return passes ? Optional.of(context) : Optional.empty();
    }
}
