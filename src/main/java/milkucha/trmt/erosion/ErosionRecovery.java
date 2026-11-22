package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

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
 * <p>
 * Every recovered block shows bonemeal particles. The clicked block uses level event
 * {@link LevelEvent#PARTICLES_AND_SOUND_PLANT_GROWTH} (1505) when bonemeal is used (vanilla's 15 particles plus the
 * bone meal sound, so one sound per use; a brush uses {@link LevelEvent#PARTICLES_PLANT_GROWTH} (2005) there too, since
 * it plays its own sound); its neighbours use {@link LevelEvent#PARTICLES_PLANT_GROWTH} (particles only, fewer of them).
 * Same as home: on 1.20.1 level event 2005 is still handled by the client (LevelRenderer.levelEvent).
 */
public final class ErosionRecovery {

    /** Uses needed to recover one stage: target, left, right. */
    public static final int USES_TARGET_ROW = 1;
    /** Uses needed to recover one stage: front, back. */
    public static final int USES_FRONT_BACK = 1;
    /** Uses needed to recover one stage: the four corners. */
    public static final int USES_CORNER = 2;

    /** Bonemeal particle count for the clicked block (0 = vanilla's default of 15), sent with {@code PARTICLES_AND_SOUND_PLANT_GROWTH} (with sound). */
    private static final int PARTICLES_CENTRE = 0;
    /** Bonemeal particle count for a recovered left, right, front or back block, sent with {@code PARTICLES_PLANT_GROWTH} (no sound). */
    private static final int PARTICLES_NEIGHBOUR = 2;
    /** Bonemeal particle count for a recovered corner block, sent with {@code PARTICLES_PLANT_GROWTH} (no sound). */
    private static final int PARTICLES_CORNER = 1;

    public enum Tool {
        BONEMEAL("bonemeal"), BRUSH("brush");

        final String name;
        Tool(String name) { this.name = name; }

        /** True if {@code state} is an eroded block this tool recovers. */
        public boolean acts(BlockState state) {
            return this == BONEMEAL
                    ? state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get()) || state.is(TRMTBlocks.ERODED_DIRT.get()) || state.is(TRMTBlocks.ERODED_COARSE_DIRT.get())
                    : state.is(TRMTBlocks.ERODED_SAND.get());
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
    public static int recoverArea(ServerLevel world, BlockPos centre, Direction facing, Tool tool) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        BlockPos front = centre.relative(facing);
        BlockPos back = centre.relative(facing.getOpposite());
        int changed = 0;
        if (recoverAt(world, centre, USES_TARGET_ROW, tool, "centre")) changed++;
        if (recoverAt(world, centre.relative(left), USES_TARGET_ROW, tool, "left")) changed++;
        if (recoverAt(world, centre.relative(right), USES_TARGET_ROW, tool, "right")) changed++;
        if (recoverAt(world, front, USES_FRONT_BACK, tool, "front")) changed++;
        if (recoverAt(world, back, USES_FRONT_BACK, tool, "back")) changed++;
        if (recoverAt(world, front.relative(left), USES_CORNER, tool, "front-left")) changed++;
        if (recoverAt(world, front.relative(right), USES_CORNER, tool, "front-right")) changed++;
        if (recoverAt(world, back.relative(left), USES_CORNER, tool, "back-left")) changed++;
        if (recoverAt(world, back.relative(right), USES_CORNER, tool, "back-right")) changed++;
        return changed;
    }

    private static boolean recoverAt(ServerLevel world, BlockPos pos, int usesNeeded, Tool tool, String role) {
        BlockState state = world.getBlockState(pos);
        if (!tool.acts(state)) return false;
        if (tool == Tool.BRUSH && isOccupied(world, pos)) {
            log(tool, pos, role, "skipped-occupied");
            return false;
        }
        boolean centre = "centre".equals(role);
        // Only bonemeal plays its sound (once per use, on the clicked block); the brush has its own sound.
        int event = centre && tool == Tool.BONEMEAL ? LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH : LevelEvent.PARTICLES_PLANT_GROWTH;
        int particles = centre ? PARTICLES_CENTRE
                : role.contains("-") ? PARTICLES_CORNER   // front-left, front-right, back-left, back-right
                : PARTICLES_NEIGHBOUR;
        if (usesNeeded <= 1) {
            recoverOneStage(world, pos, state, tool, event, particles);
            log(tool, pos, role, "recovered");
            return true;
        }
        ErosionMapManager manager = ErosionMapManager.get(world);
        ErosionEntry entry = entryAt(manager, pos);
        if (entry == null) {
            // The progress lives on the erosion entry; an eroded block without one gets a cooldown entry.
            manager.writeCooldownEntry(pos, state.getBlock(), world.getGameTime());
            manager.broadcastEntryUpdate(pos, state.getBlock());
            entry = entryAt(manager, pos);
            if (entry == null) return false; // erosion inactive in this level
        }
        int uses = entry.getRecoveryUses() + 1;
        if (uses < usesNeeded) {
            entry.setRecoveryUses(uses);
            log(tool, pos, role, "uses=" + uses + "/" + usesNeeded);
            return false;
        }
        recoverOneStage(world, pos, state, tool, event, particles); // replaces the entry: progress restarts at 0
        log(tool, pos, role, "recovered");
        return true;
    }

    private static ErosionEntry entryAt(ErosionMapManager manager, BlockPos pos) {
        ChunkErosionMap map = manager.getChunkMap(new ChunkPos(pos));
        return map != null ? map.getEntry(pos) : null;
    }

    /** True if a living, non-spectator entity stands on (or in) the block space at {@code pos}. */
    private static boolean isOccupied(ServerLevel world, BlockPos pos) {
        AABB box = new AABB(pos).expandTowards(0.0, 0.25, 0.0);
        return !world.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    /**
     * One stage back along the erosion chain, with a fresh cooldown entry, HUD update and the level event
     * {@code event} ({@code PARTICLES_AND_SOUND_PLANT_GROWTH} or {@code PARTICLES_PLANT_GROWTH}) carrying {@code particles} as its data.
     */
    private static void recoverOneStage(ServerLevel world, BlockPos pos, BlockState state, Tool tool, int event, int particles) {
        ErosionMapManager manager = ErosionMapManager.get(world);
        long now = world.getGameTime();
        Block block = state.getBlock();
        BlockState next;
        Block cooldownFor; // null = back to vanilla, no entry

        if (block == TRMTBlocks.ERODED_SAND.get()) {
            int stage = state.getValue(ErodedSandBlock.STAGE);
            if (stage > 0) {
                boolean keepWaterlogged = stage > 1 && state.getValue(BlockStateProperties.WATERLOGGED);
                next = state.setValue(ErodedSandBlock.STAGE, stage - 1).setValue(BlockStateProperties.WATERLOGGED, keepWaterlogged);
                cooldownFor = TRMTBlocks.ERODED_SAND.get();
            } else {
                next = Blocks.SAND.defaultBlockState();
                cooldownFor = null;
            }
        } else if (block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) {
            int stage = state.getValue(ErodedGrassBlock.STAGE);
            if (stage > 0) {
                next = state.setValue(ErodedGrassBlock.STAGE, stage - 1);
                cooldownFor = TRMTBlocks.ERODED_GRASS_BLOCK.get();
            } else {
                // Stage 0 → fully recovered, vanilla grass (snowy if snow lies on top).
                next = ErodedGrassBlock.grassStateFor(world, pos);
                cooldownFor = null;
            }
        } else if (block == TRMTBlocks.ERODED_DIRT.get()) {
            int stage = state.getValue(ErodedDirtBlock.STAGE);
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            if (state.getValue(ErodedDirtBlock.FROM_DIRT) && stage <= 1) {
                // Worn from plain dirt: fully recovered, back to vanilla dirt (never grass).
                next = Blocks.DIRT.defaultBlockState();
                cooldownFor = null;
            } else if (stage > 0) {
                next = state.setValue(ErodedDirtBlock.STAGE, stage - 1);
                cooldownFor = TRMTBlocks.ERODED_DIRT.get();
            } else {
                // Stage 0 → most-eroded grass stage, preserving facing.
                next = TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
                        .setValue(ErodedGrassBlock.FACING, facing)
                        .setValue(ErodedGrassBlock.STAGE, 4)
                        .setValue(ErodedGrassBlock.SNOWY, ErodedGrassBlock.snowyAt(world, pos));
                cooldownFor = TRMTBlocks.ERODED_GRASS_BLOCK.get();
            }
        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
            next = TRMTBlocks.ERODED_DIRT.get().defaultBlockState()
                    .setValue(ErodedDirtBlock.FACING, state.getValue(ErodedDirtBlock.FACING))
                    .setValue(ErodedDirtBlock.STAGE, 3)
                    .setValue(ErodedDirtBlock.FROM_DIRT, state.getValue(ErodedDirtBlock.FROM_DIRT));
            cooldownFor = TRMTBlocks.ERODED_DIRT.get();
        } else {
            return;
        }

        world.setBlock(pos, next, Block.UPDATE_ALL);
        manager.removeEntry(pos);
        if (cooldownFor != null) {
            manager.writeCooldownEntry(pos, cooldownFor, now);
            manager.broadcastEntryUpdate(pos, cooldownFor);
        }
        world.levelEvent(event, pos, particles);
    }

    private static void log(Tool tool, BlockPos pos, String role, String result) {
        TestLog.line(() -> "recover tool=" + tool.name + " role=" + role
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + result);
    }
}
