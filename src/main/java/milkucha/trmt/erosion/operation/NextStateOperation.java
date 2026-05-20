package milkucha.trmt.erosion.operation;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import java.util.Map;
import java.util.Optional;

public final class NextStateOperation implements ErosionOperation {

    private final Block block;
    private final Map<String, String> properties;
    private final Map<String, String> propertySources;

    public NextStateOperation(Block block, Map<String, String> properties, Map<String, String> propertySources) {
        this.block = block;
        this.properties = Map.copyOf(properties);
        this.propertySources = Map.copyOf(propertySources);
        ErosionTransformSupport.withProperties(block.getDefaultState(), properties);
    }

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        if (context.hasProposedState()) {
            return Optional.of(context);
        }

        BlockState nextState = ErosionTransformSupport.withProperties(block.getDefaultState(), properties);
        nextState = ErosionTransformSupport.withPropertySources(nextState, propertySources, context.state(), context.pos());

        return Optional.of(context.propose(nextState));
    }
}
