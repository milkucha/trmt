package milkucha.trmt.erosion.operation;

import net.minecraft.state.property.IntProperty;

import java.util.Optional;

public final class NextStageOperation implements ErosionOperation {

    private final int maxStage;

    public NextStageOperation(int maxStage) {
        this.maxStage = maxStage;
    }

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        IntProperty stageProperty = ErosionTransformSupport.getStageProperty(context.state());
        int currentStage = context.state().get(stageProperty);
        if (currentStage < maxStage) {
            return Optional.of(context.propose(context.state().with(stageProperty, currentStage + 1)));
        }
        return Optional.of(context);
    }
}
