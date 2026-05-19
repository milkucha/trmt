package milkucha.trmt.erosion.operation;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakLeavesOperation implements ErosionOperation {

    private final Float dropChance;

    public BreakLeavesOperation(Float dropChance) {
        this.dropChance = dropChance;
    }

    @Override
    public Optional<TransformContext> apply(TransformContext context) {
        float chance = dropChance != null ? dropChance : 0.1f;
        boolean drops = chance >= 1.0f || (chance > 0.0f && ThreadLocalRandom.current().nextFloat() < chance);
        context.world().breakBlock(context.pos(), drops);
        context.manager().removeEntry(context.pos());
        return Optional.empty();
    }
}
