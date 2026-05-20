package milkucha.trmt.erosion.operation;

import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Map;

public final class ErosionTransformSupport {

    private ErosionTransformSupport() {
    }

    public static void clearEntry(ErosionMapManager manager, BlockPos pos, BlockState state,
                                  boolean continueTracking, long gameTime) {
        var history = manager.getHistory(pos);
        manager.removeEntry(pos);
        if (continueTracking) {
            manager.writeCooldownEntry(pos, state.getBlock(), gameTime, history);
        }
    }

    public static void clearEntryWithCurrent(ErosionMapManager manager, BlockPos pos, BlockState state,
                                             BlockState nextState, boolean continueTracking, long gameTime) {
        var history = manager.getHistoryWithCurrent(pos, state);
        manager.removeEntry(pos);
        if (continueTracking) {
            manager.writeCooldownEntry(pos, nextState.getBlock(), gameTime, history);
        }
    }

    public static Block resolveBlock(String identifier) {
        Identifier id = toBlockIdentifier(identifier);
        return Registries.BLOCK.getOrEmpty(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown erosion block identifier: " + identifier));
    }

    public static Identifier toBlockIdentifier(String identifier) {
        String normalized = identifier.toLowerCase();
        Identifier id = Identifier.tryParse(normalized);
        if (id == null) {
            throw new IllegalArgumentException("Invalid erosion block identifier: " + identifier);
        }
        return id;
    }

    public static IntProperty getStageProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntProperty intProperty && property.getName().equals("stage")) {
                return intProperty;
            }
        }
        throw new IllegalArgumentException("Block state has no erosion stage: " + state);
    }

    public static BlockState withProperties(BlockState state, Map<String, String> properties) {
        BlockState nextState = state;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            nextState = withProperty(nextState, property.getKey(), property.getValue());
        }
        return nextState;
    }

    public static BlockState withPropertySources(BlockState state, Map<String, String> propertySources,
                                                 BlockState sourceState, BlockPos pos) {
        BlockState nextState = state;
        for (Map.Entry<String, String> propertySource : propertySources.entrySet()) {
            nextState = withProperty(nextState, propertySource.getKey(),
                    resolvePropertySource(propertySource.getKey(), propertySource.getValue(), sourceState, pos));
        }
        return nextState;
    }

    private static String resolvePropertySource(String propertyName, String source, BlockState sourceState, BlockPos pos) {
        return switch (source) {
            case "position" -> {
                if (!Properties.HORIZONTAL_FACING.getName().equals(propertyName)) {
                    throw new IllegalArgumentException("Property source 'position' only supports horizontal facing");
                }
                yield facingFromPosition(pos).asString();
            }
            case "carry" -> propertyValue(sourceState, propertyName);
            default -> throw new IllegalArgumentException("Unsupported property source: " + source);
        };
    }

    public static String propertyValue(BlockState state, String propertyName) {
        for (var entry : state.getEntries().entrySet()) {
            if (entry.getKey().getName().equals(propertyName)) {
                return propertyValue(entry.getKey(), entry.getValue());
            }
        }
        throw new IllegalArgumentException("Block state has no property '" + propertyName + "': " + state);
    }

    public static BlockState withProperty(BlockState state, String propertyName, String propertyValue) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return withProperty(state, property, propertyValue);
            }
        }
        throw new IllegalArgumentException("Block state has no property '" + propertyName + "': " + state);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withProperty(BlockState state, Property property, String propertyValue) {
        var value = property.parse(propertyValue);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid value '" + propertyValue + "' for property '" + property.getName() + "' on " + state);
        }

        return (BlockState) state.with(property, (Comparable) value.get());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String propertyValue(Property property, Comparable value) {
        return property.name(value);
    }

    public static Direction rotationToFacing(int rotation) {
        return switch (rotation) {
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }

    public static Direction facingFromPosition(BlockPos pos) {
        return rotationToFacing(BlockThresholds.posRotation(pos));
    }
}
