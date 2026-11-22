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
 * Bonemeal / brush recovery of eroded blocks. A use acts on a 3x3 square at the target's height, oriented
 * by the player's horizontal facing. Uses needed to recover one stage:
 * <pre>
 * 2 1 2     front-left · front · front-right
 * 1 1 1     left · target · right
 * 2 1 2     back-left · back · back-right
 * </pre>
 * Blocks needing more than one use count the uses on their erosion entry ({@code recoveryUses}, an
 * integer, so no float drift); at the needed count they recover one stage and restart at 0. Only eroded
 * blocks of the tool's family at the same Y take part.
 */
public final class ErosionRecovery {

    /** Uses needed to recover one stage: target, left, right. */
    public static final int USES_TARGET_ROW = 1;
    /** Uses needed to recover one stage: front, back. */
    public static final int USES_FRONT_BACK = 1;
    /** Uses needed to recover one stage: the four corners. */
    public static final int USES_CORNER = 2;

    /** Bonemeal particle count for the clicked block (0 = vanilla's default of 15). */
    private static final int PARTICLES_CENTRE = 0;
    /** Bonemeal particle count for a recovered left, right, front or back block. */
    private static final int PARTICLES_NEIGHBOUR = 2;
    /** Bonemeal particle count for a recovered corner block. */
    private static final int PARTICLES_CORNER = 1;

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
     * Applies one use of {@code tool} to the 3x3 square around {@code centre} (same Y). The caller has checked
     * that {@code tool.acts(centre state)}. {@code facing} is the player's horizontal facing.
     * Brushing skips any sand position a living entity is standing on (centre included).
     *
     * @return the number of blocks that changed state
     */
    public static int recoverArea(ServerWorld world, BlockPos centre, Direction facing, Tool tool) {
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();
        BlockPos front = centre.offset(facing);
        BlockPos back = centre.offset(facing.getOpposite());
        int changed = 0;
        if (recoverAt(world, centre, USES_TARGET_ROW, tool, "centre")) changed++;
        if (recoverAt(world, centre.offset(left), USES_TARGET_ROW, tool, "left")) changed++;
        if (recoverAt(world, centre.offset(right), USES_TARGET_ROW, tool, "right")) changed++;
        if (recoverAt(world, front, USES_FRONT_BACK, tool, "front")) changed++;
        if (recoverAt(world, back, USES_FRONT_BACK, tool, "back")) changed++;
        if (recoverAt(world, front.offset(left), USES_CORNER, tool, "front-left")) changed++;
        if (recoverAt(world, front.offset(right), USES_CORNER, tool, "front-right")) changed++;
        if (recoverAt(world, back.offset(left), USES_CORNER, tool, "back-left")) changed++;
        if (recoverAt(world, back.offset(right), USES_CORNER, tool, "back-right")) changed++;
        return changed;
    }

    private static boolean recoverAt(ServerWorld world, BlockPos pos, int usesNeeded, Tool tool, String role) {
        BlockState state = world.getBlockState(pos);
        if (!tool.acts(state)) return false;
        if (tool == Tool.BRUSH && isOccupied(world, pos)) {
            log(tool, pos, role, "skipped-occupied");
            return false;
        }
        int particles = "centre".equals(role) ? PARTICLES_CENTRE
                : role.contains("-") ? PARTICLES_CORNER   // front-left, front-right, back-left, back-right
                : PARTICLES_NEIGHBOUR;
        if (usesNeeded <= 1) {
            recoverOneStage(world, pos, state, tool, particles);
            log(tool, pos, role, "recovered");
            return true;
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
        int uses = entry.getRecoveryUses() + 1;
        if (uses < usesNeeded) {
            entry.setRecoveryUses(uses);
            log(tool, pos, role, "uses=" + uses + "/" + usesNeeded);
            return false;
        }
        recoverOneStage(world, pos, state, tool, particles); // replaces the entry: progress restarts at 0
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
    private static void recoverOneStage(ServerWorld world, BlockPos pos, BlockState state, Tool tool, int particles) {
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
        world.syncWorldEvent(2005, pos, particles);
    }

    private static void log(Tool tool, BlockPos pos, String role, String result) {
        TestLog.line(() -> "recover tool=" + tool.name + " role=" + role
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + result);
    }
}
