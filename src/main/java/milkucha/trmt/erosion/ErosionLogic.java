package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.erosion.operation.TransformContext;
import milkucha.trmt.erosion.operation.TransformRule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

public final class ErosionLogic {

    private static List<TransformRule> transformRules = List.of();
    private static boolean transformRulesEnabled = true;

    private ErosionLogic() {}

    public static boolean isTracked(BlockState state) {
        if (!transformRulesEnabled) {
            return false;
        }

        return getTransformRules().stream().anyMatch(rule -> rule.tracks(state));
    }

    public static boolean isErodedBlock(Block block) {
        return block == TRMTBlocks.ERODED_GRASS_BLOCK
                || block == TRMTBlocks.ERODED_DIRT
                || block == TRMTBlocks.ERODED_COARSE_DIRT
                || block == TRMTBlocks.ERODED_SAND;
    }

    public static BlockPos getGroundPos(Entity entity) {
        BlockPos groundPos = entity.getBlockPos().down();
        World world = entity.getWorld();
        BlockState groundUpState = world.getBlockState(groundPos.up());
        if (groundUpState.isOf(TRMTBlocks.ERODED_SAND) || groundUpState.isOf(Blocks.SAND)) {
            return groundPos.up();
        }
        return groundPos;
    }

    public static void stepVegetation(World world, ErosionMapManager manager,
                                      BlockPos groundPos, float amount, long gameTime) {
        stepIfTracked(world, manager, groundPos.up(), amount, gameTime);
    }

    public static boolean stepGround(World world, ErosionMapManager manager,
                                     BlockPos groundPos, float amount, long gameTime) {
        stepVegetation(world, manager, groundPos, amount, gameTime);

        BlockState state = world.getBlockState(groundPos);
        if (!isTracked(state)) {
            return false;
        }

        Block block = state.getBlock();
        manager.onStep(groundPos, block, amount, gameTime);
        tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);
        return true;
    }

    public static void stepIfTracked(World world, ErosionMapManager manager, BlockPos pos, float amount, long gameTime) {
        BlockState state = world.getBlockState(pos);

        if (isTracked(state)) {
            manager.onStep(pos, state.getBlock(), amount, gameTime);
            tryTransform(world, manager, pos);
            manager.broadcastEntryUpdate(pos, state.getBlock());
        }
    }

    public static void tryTransform(World world, ErosionMapManager manager, BlockPos pos) {
        if (!transformRulesEnabled) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        TransformContext context = new TransformContext(world, manager, pos, state);
        for (TransformRule rule : getTransformRules()) {
            if (rule.matches(state)) {
                rule.apply(context);
                return;
            }
        }
    }

    private static List<TransformRule> getTransformRules() {
        return transformRules;
    }

    public static void replaceTransformRules(List<TransformRule> rules, boolean enabled) {
        transformRules = List.copyOf(rules);
        transformRulesEnabled = enabled;
    }

    public static boolean areTransformRulesEnabled() {
        return transformRulesEnabled;
    }

    public static Optional<ErosionThresholdRange> getThresholdRange(Block block) {
        if (!transformRulesEnabled) {
            return Optional.empty();
        }

        BlockState defaultState = block.getDefaultState();
        return getTransformRules().stream()
                .filter(rule -> rule.threshold().isPresent())
                .filter(rule -> rule.tracks(defaultState))
                .map(rule -> rule.threshold().get())
                .findFirst();
    }
}
