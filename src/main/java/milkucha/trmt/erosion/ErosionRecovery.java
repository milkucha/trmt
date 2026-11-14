package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

/**
 * Bonemeal / brush recovery of eroded blocks. A use always acts on a cross: the target block plus its
 * 4 horizontal neighbours at the same height.
 *
 * <p>The target recovers one stage. The neighbours are treated like the neighbours of a step
 * ({@code ServerPlayerEntityMixin.trmt$stepAdjacent}): they get a fraction of the centre's effect, by
 * direction relative to the player's facing, which accumulates until it is a whole stage. Erosion gives
 * front 0.2, left/right 0.5, back 0 of a step; here front 0.2, left/right 0.5 and back 0.2 of a stage
 * (back has no erosion counterpart and the cross always includes it, so it gets the front's share).
 * Like erosion, the progress restarts from 0 after a stage change (no carry-over). Neighbours must be an
 * eroded block of the tool's family at the same Y (erosion's neighbours are not slope-aware either).
 */
public final class ErosionRecovery {

    /** Share of a stage given to the neighbour in front of the target (away from the player) per use. */
    public static final float FRONT_SHARE = 0.2f;
    /** Share of a stage given to the left and right neighbours per use. */
    public static final float SIDE_SHARE = 0.5f;
    /** Share of a stage given to the neighbour behind the target (towards the player) per use. */
    public static final float BACK_SHARE = 0.2f;

    /** Float tolerance so 5 × 0.2 counts as a whole stage. */
    private static final float EPSILON = 1.0e-4f;

    public enum Tool {
        BONEMEAL("bonemeal"), BRUSH("brush");

        final String name;
        Tool(String name) { this.name = name; }

        /** True if {@code state} is an eroded block this tool recovers. */
        public boolean acts(BlockState state) {
            return this == BONEMEAL
                    ? state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK) || state.isOf(TRMTBlocks.ERODED_DIRT) || state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)
                    : state.isOf(TRMTBlocks.ERODED_SAND);
        }
    }

    private ErosionRecovery() {}

    /**
     * Applies one use of {@code tool} to {@code centre} and its cross neighbours. The caller has checked that
     * {@code tool.acts(centre state)}. {@code facing} is the player's horizontal facing.
     * Brushing skips any sand position a living entity is standing on (centre included).
     *
     * @return the number of blocks that changed state
     */
    public static int recoverCross(ServerWorld world, BlockPos centre, Direction facing, Tool tool) {
        int changed = 0;
        if (recoverCentre(world, centre, tool)) changed++;
        if (recoverNeighbour(world, centre.offset(facing), FRONT_SHARE, tool, "front")) changed++;
        if (recoverNeighbour(world, centre.offset(facing.rotateYCounterclockwise()), SIDE_SHARE, tool, "left")) changed++;
        if (recoverNeighbour(world, centre.offset(facing.rotateYClockwise()), SIDE_SHARE, tool, "right")) changed++;
        if (recoverNeighbour(world, centre.offset(facing.getOpposite()), BACK_SHARE, tool, "back")) changed++;
        return changed;
    }

    private static boolean recoverCentre(ServerWorld world, BlockPos pos, Tool tool) {
        BlockState state = world.getBlockState(pos);
        if (!tool.acts(state)) return false;
        if (tool == Tool.BRUSH && isOccupied(world, pos)) {
            log(tool, pos, "centre", "skipped-occupied");
            return false;
        }
        recoverOneStage(world, pos, state, tool);
        log(tool, pos, "centre", "recovered");
        return true;
    }

    private static boolean recoverNeighbour(ServerWorld world, BlockPos pos, float share, Tool tool, String role) {
        BlockState state = world.getBlockState(pos);
        if (!tool.acts(state)) return false;
        if (tool == Tool.BRUSH && isOccupied(world, pos)) {
            log(tool, pos, role, "skipped-occupied");
            return false;
        }
        ErosionMapManager manager = ErosionMapManager.get(world);
        ErosionEntry entry = entryAt(manager, pos);
        if (entry == null) {
            // The progress lives on the erosion entry; an eroded block without one gets a cooldown entry.
            manager.writeCooldownEntry(pos, state.getBlock(), world.getTime());
            manager.broadcastEntryUpdate(pos, state.getBlock());
            entry = entryAt(manager, pos);
            if (entry == null) return false; // erosion inactive in this world
        }
        float credit = entry.getRecoveryCredit() + share;
        if (credit < 1.0f - EPSILON) {
            entry.setRecoveryCredit(credit);
            log(tool, pos, role, "credit=" + credit);
            return false;
        }
        recoverOneStage(world, pos, state, tool); // replaces the entry: progress restarts at 0
        log(tool, pos, role, "recovered");
        return true;
    }

    private static ErosionEntry entryAt(ErosionMapManager manager, BlockPos pos) {
        ChunkErosionMap map = manager.getChunkMap(new ChunkPos(pos));
        return map != null ? map.getEntry(pos) : null;
    }

    /** True if a living, non-spectator entity stands on (or in) the block space at {@code pos}. */
    private static boolean isOccupied(ServerWorld world, BlockPos pos) {
        Box box = new Box(pos).stretch(0.0, 0.25, 0.0);
        return !world.getEntitiesByClass(LivingEntity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    /** One stage back along the erosion chain, with a fresh cooldown entry, HUD update and bonemeal particles. */
    private static void recoverOneStage(ServerWorld world, BlockPos pos, BlockState state, Tool tool) {
        ErosionMapManager manager = ErosionMapManager.get(world);
        long now = world.getTime();
        Block block = state.getBlock();
        BlockState next;
        Block cooldownFor; // null = back to vanilla, no entry

        if (block == TRMTBlocks.ERODED_SAND) {
            int stage = state.get(ErodedSandBlock.STAGE);
            if (stage > 0) {
                boolean keepWaterlogged = stage > 1 && state.get(ErodedSandBlock.WATERLOGGED);
                next = state.with(ErodedSandBlock.STAGE, stage - 1).with(ErodedSandBlock.WATERLOGGED, keepWaterlogged);
                cooldownFor = TRMTBlocks.ERODED_SAND;
            } else {
                next = Blocks.SAND.getDefaultState();
                cooldownFor = null;
            }
        } else if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            int stage = state.get(ErodedGrassBlock.STAGE);
            if (stage > 0) {
                next = state.with(ErodedGrassBlock.STAGE, stage - 1);
                cooldownFor = TRMTBlocks.ERODED_GRASS_BLOCK;
            } else {
                // Stage 0 → fully recovered, vanilla grass.
                next = Blocks.GRASS_BLOCK.getDefaultState();
                cooldownFor = null;
            }
        } else if (block == TRMTBlocks.ERODED_DIRT) {
            int stage = state.get(ErodedDirtBlock.STAGE);
            Direction facing = state.get(ErodedDirtBlock.FACING);
            if (state.get(ErodedDirtBlock.FROM_DIRT) && stage <= 1) {
                // Worn from plain dirt: fully recovered, back to vanilla dirt (never grass).
                next = Blocks.DIRT.getDefaultState();
                cooldownFor = null;
            } else if (stage > 0) {
                next = state.with(ErodedDirtBlock.STAGE, stage - 1);
                cooldownFor = TRMTBlocks.ERODED_DIRT;
            } else {
                // Stage 0 → most-eroded grass stage, preserving facing.
                next = TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                        .with(ErodedGrassBlock.FACING, facing)
                        .with(ErodedGrassBlock.STAGE, 4);
                cooldownFor = TRMTBlocks.ERODED_GRASS_BLOCK;
            }
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
            next = TRMTBlocks.ERODED_DIRT.getDefaultState()
                    .with(ErodedDirtBlock.FACING, state.get(ErodedDirtBlock.FACING))
                    .with(ErodedDirtBlock.STAGE, 3)
                    .with(ErodedDirtBlock.FROM_DIRT, state.get(ErodedDirtBlock.FROM_DIRT));
            cooldownFor = TRMTBlocks.ERODED_DIRT;
        } else {
            return;
        }

        world.setBlockState(pos, next, Block.NOTIFY_ALL);
        manager.removeEntry(pos);
        if (cooldownFor != null) {
            manager.writeCooldownEntry(pos, cooldownFor, now);
            manager.broadcastEntryUpdate(pos, cooldownFor);
        }
        world.syncWorldEvent(2005, pos, 0);
    }

    private static void log(Tool tool, BlockPos pos, String role, String result) {
        TestLog.line(() -> "recover tool=" + tool.name + " role=" + role
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + result);
    }
}
