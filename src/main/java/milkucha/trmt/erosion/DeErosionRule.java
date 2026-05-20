package milkucha.trmt.erosion;

import milkucha.trmt.erosion.operation.ErosionTransformSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public record DeErosionRule(
        Predicate<BlockState> matcher,
        Optional<String> naturalConfig,
        Map<Item, ItemTrigger> itemTriggers,
        long timeoutTicks,
        Map<String, Map<String, Long>> timeoutTicksByProperty,
        Optional<FallbackState> fallback
) {
    public DeErosionRule {
        itemTriggers = Map.copyOf(itemTriggers);
    }

    public boolean matches(BlockState state) {
        return matcher.test(state);
    }

    public Optional<ItemTrigger> itemTrigger(Item item) {
        return Optional.ofNullable(itemTriggers.get(item));
    }

    public record ItemTrigger(Optional<String> config, String mode, int consume, int damage, int ticks, int worldEvent) {
    }

    public long timeoutTicks(BlockState state) {
        for (Map.Entry<String, Map<String, Long>> propertyTimeouts : timeoutTicksByProperty.entrySet()) {
            String value = propertyValue(state, propertyTimeouts.getKey());
            if (value == null) {
                continue;
            }

            Long timeout = propertyTimeouts.getValue().get(value);
            if (timeout != null) {
                return timeout;
            }
        }
        return timeoutTicks;
    }

    private static String propertyValue(BlockState state, String propertyName) {
        for (var entry : state.getEntries().entrySet()) {
            if (entry.getKey().getName().equals(propertyName)) {
                return stringify(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String stringify(Property property, Comparable value) {
        return property.name(value);
    }

    public record FallbackState(Block block, Map<String, String> properties, Map<String, String> propertySources) {
        public FallbackState {
            properties = Map.copyOf(properties);
            propertySources = Map.copyOf(propertySources);
        }

        public BlockState toBlockState(BlockState currentState, BlockPos pos) {
            BlockState state = ErosionTransformSupport.withProperties(block.getDefaultState(), properties);
            return ErosionTransformSupport.withPropertySources(state, propertySources, currentState, pos);
        }
    }
}
