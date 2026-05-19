package milkucha.trmt.erosion.operation;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

import java.util.Optional;

public final class NextStateOperation implements ErosionOperation {

    private final Block block;
    private final Integer stage;
    private final String facingMode;

    public NextStateOperation(Block block, Integer stage, String facingMode) {
        this.block = block;
        this.stage = stage;
        this.facingMode = facingMode;
        if (stage != null) {
            ErosionTransformSupport.withStage(block.getDefaultState(), stage);
        }
    }

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        if (context.hasProposedState()) {
            return Optional.of(context);
        }

        BlockState nextState = block.getDefaultState();
        if (stage != null) {
            nextState = ErosionTransformSupport.withStage(nextState, stage);
        }

        Direction facing = switch (facingMode) {
            case "position" -> ErosionTransformSupport.facingFromPosition(context.pos());
            case "carry" -> ErosionTransformSupport.getFacing(context.state());
            case "none" -> null;
            default -> throw new IllegalArgumentException("Unsupported facing mode: " + facingMode);
        };
        if (facing != null) {
            nextState = ErosionTransformSupport.withFacing(nextState, facing);
        }

        return Optional.of(context.propose(nextState));
    }
}
