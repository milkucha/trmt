package milkucha.trmt.erosion.operation;

import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class ErosionTransformSupport {

    private ErosionTransformSupport() {
    }

    public static void clearEntry(ErosionMapManager manager, BlockPos pos) {
        manager.removeEntry(pos);
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
        if (normalized.indexOf(':') >= 0) {
            Identifier id = Identifier.tryParse(normalized);
            if (id == null) {
                throw new IllegalArgumentException("Invalid erosion block identifier: " + identifier);
            }
            return id;
        }

        String namespace = normalized.startsWith("eroded_") ? "trmt" : "minecraft";
        return Identifier.of(namespace, normalized);
    }

    public static IntProperty getStageProperty(BlockState state) {
        if (state.contains(ErodedSandBlock.STAGE)) return ErodedSandBlock.STAGE;
        if (state.contains(ErodedGrassBlock.STAGE)) return ErodedGrassBlock.STAGE;
        if (state.contains(ErodedDirtBlock.STAGE)) return ErodedDirtBlock.STAGE;
        throw new IllegalArgumentException("Block state has no erosion stage: " + state);
    }

    public static BlockState withStage(BlockState state, int stage) {
        if (state.contains(ErodedSandBlock.STAGE)) return state.with(ErodedSandBlock.STAGE, stage);
        if (state.contains(ErodedGrassBlock.STAGE)) return state.with(ErodedGrassBlock.STAGE, stage);
        if (state.contains(ErodedDirtBlock.STAGE)) return state.with(ErodedDirtBlock.STAGE, stage);
        throw new IllegalArgumentException("Cannot set erosion stage on block state: " + state);
    }

    public static Direction getFacing(BlockState state) {
        if (state.contains(ErodedSandBlock.FACING)) return state.get(ErodedSandBlock.FACING);
        if (state.contains(ErodedGrassBlock.FACING)) return state.get(ErodedGrassBlock.FACING);
        if (state.contains(ErodedDirtBlock.FACING)) return state.get(ErodedDirtBlock.FACING);
        return Direction.SOUTH;
    }

    public static BlockState withFacing(BlockState state, Direction facing) {
        if (state.contains(ErodedSandBlock.FACING)) return state.with(ErodedSandBlock.FACING, facing);
        if (state.contains(ErodedGrassBlock.FACING)) return state.with(ErodedGrassBlock.FACING, facing);
        if (state.contains(ErodedDirtBlock.FACING)) return state.with(ErodedDirtBlock.FACING, facing);
        return state;
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
