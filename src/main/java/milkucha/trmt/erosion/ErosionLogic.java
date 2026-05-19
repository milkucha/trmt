package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.operation.TransformContext;
import milkucha.trmt.erosion.operation.TransformRule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class ErosionLogic {

    private static List<TransformRule> transformRules = List.of();
    private static boolean transformRulesEnabled = true;

    private ErosionLogic() {}

    public static boolean isTracked(BlockState state, TRMTConfig.ErosionToggles erosion) {
        if (!transformRulesEnabled) {
            return false;
        }

        Block block = state.getBlock();
        if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            return erosion.grassEnabled;
        }
        if (state.isOf(Blocks.DIRT) || state.isOf(TRMTBlocks.ERODED_DIRT)) {
            return erosion.dirtEnabled;
        }
        if (state.isOf(Blocks.SAND) || state.isOf(TRMTBlocks.ERODED_SAND)) {
            return erosion.sandEnabled;
        }
        if (BlockThresholds.isLeaves(block)) {
            return erosion.leavesEnabled;
        }

        return getTransformRules().stream().anyMatch(rule -> rule.matches(state));
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
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        BlockPos vegPos = groundPos.up();
        BlockState vegState = world.getBlockState(vegPos);
        if (!erosion.vegetationEnabled || !BlockThresholds.isVegetation(vegState.getBlock())) {
            return;
        }

        manager.onStep(vegPos, vegState.getBlock(), amount, gameTime);
        tryBreakVegetation(world, manager, vegPos, vegState);
        manager.broadcastEntryUpdate(vegPos, vegState.getBlock());
    }

    public static boolean stepGround(World world, ErosionMapManager manager,
                                     BlockPos groundPos, float amount, long gameTime) {
        stepVegetation(world, manager, groundPos, amount, gameTime);

        BlockState state = world.getBlockState(groundPos);
        if (!isTracked(state, TRMTConfig.get().erosion)) {
            return false;
        }

        Block block = state.getBlock();
        manager.onStep(groundPos, block, amount, gameTime);
        tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);
        return true;
    }

    public static void stepIfTracked(World world, ErosionMapManager manager,
                                     BlockPos pos, float amount, long gameTime) {
        BlockState state = world.getBlockState(pos);
        if (!isTracked(state, TRMTConfig.get().erosion)) {
            return;
        }

        manager.onStep(pos, state.getBlock(), amount, gameTime);
        tryTransform(world, manager, pos);
    }

    public static void tryBreakVegetation(World world, ErosionMapManager manager,
                                          BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.getBlock() instanceof TallPlantBlock
                && state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.up();
            if (world.getBlockState(upper).isOf(state.getBlock())) {
                world.removeBlock(upper, false);
            }
            if (state.isOf(Blocks.TALL_GRASS)) {
                world.setBlockState(pos, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                return;
            }
        }

        float dropChance = TRMTConfig.get().erosionThresholds.vegetation.dropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        world.breakBlock(pos, drops);
        manager.removeEntry(pos);
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

    public static void replaceTransformRules(List<TransformRule> rules) {
        replaceTransformRules(rules, true);
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
                .filter(rule -> rule.matches(defaultState))
                .map(rule -> rule.threshold().get())
                .findFirst();
    }
}
