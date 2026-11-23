package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
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
 * Every recovered block shows bonemeal particles; only bonemeal plays a sound, once per use, on the clicked
 * block (the brush has its own sound). 1.21+ differs from 1.20.1 here: level event 2005 ({@code PLANT_FERTILIZED},
 * particles without sound) no longer exists, and {@code BoneMealItem.addGrowthParticles} (behind
 * {@link LevelEvent#PARTICLES_AND_SOUND_PLANT_GROWTH}, 1505) draws nothing on a block that is not
 * {@code BonemealableBlock} (all eroded blocks). So the sound comes from 1505 with data 0 (sound, no particles),
 * and all particles are sent from the server ({@link #spawnBonemealParticles}), placed like vanilla's
 * {@code ParticleUtils.spawnParticles} / {@code spawnParticleInBlock}.
 */
public final class ErosionRecovery {

    /** Uses needed to recover one stage: target, left, right. */
    public static final int USES_TARGET_ROW = 1;
    /** Uses needed to recover one stage: front, back. */
    public static final int USES_FRONT_BACK = 1;
    /** Uses needed to recover one stage: the four corners. */
    public static final int USES_CORNER = 2;

    /** Bonemeal particle count for the clicked block (vanilla bonemeal's 15), bonemeal and brush. */
    private static final int PARTICLES_CENTRE = 15;
    /** Bonemeal particle count for a recovered left, right, front or back block. */
    private static final int PARTICLES_NEIGHBOUR = 2;
    /** Bonemeal particle count for a recovered corner block. */
    private static final int PARTICLES_CORNER = 1;

    // Particle placement, as vanilla ParticleUtils.spawnParticles called from BoneMealItem.addGrowthParticles.
    /** Full opaque block (eroded grass/dirt/coarse dirt, sand stage 0): count multiplier of vanilla's spreading form. */
    private static final int FULL_BLOCK_COUNT_FACTOR = 3;
    /** Full opaque block: horizontal half-width of the particle area, centred on the block (vanilla 3.0). */
    private static final double FULL_BLOCK_SPREAD = 3.0;
    /** Full opaque block: height range of the particles above the block's top (vanilla 1.0). */
    private static final double FULL_BLOCK_HEIGHT = 1.0;
    /** Other blocks (sunken eroded sand): horizontal half-width, i.e. inside the block (vanilla 0.5). */
    private static final double INNER_SPREAD = 0.5;
    /** Standard deviation of each particle velocity component (vanilla 0.02). */
    private static final double VELOCITY_SIGMA = 0.02;

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
        boolean sound = centre && tool == Tool.BONEMEAL;
        int particles = centre ? PARTICLES_CENTRE
                : role.contains("-") ? PARTICLES_CORNER   // front-left, front-right, back-left, back-right
                : PARTICLES_NEIGHBOUR;
        if (usesNeeded <= 1) {
            recoverOneStage(world, pos, state, tool, particles, sound);
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
            if (entry == null) return false; // erosion inactive in this world
        }
        int uses = entry.getRecoveryUses() + 1;
        if (uses < usesNeeded) {
            entry.setRecoveryUses(uses);
            log(tool, pos, role, "uses=" + uses + "/" + usesNeeded);
            return false;
        }
        recoverOneStage(world, pos, state, tool, particles, sound); // replaces the entry: progress restarts at 0
        log(tool, pos, role, "recovered");
        return true;
    }

    private static ErosionEntry entryAt(ErosionMapManager manager, BlockPos pos) {
        ChunkErosionMap map = manager.getChunkMap(ChunkPos.containing(pos));
        return map != null ? map.getEntry(pos) : null;
    }

    /** True if a living, non-spectator entity stands on (or in) the block space at {@code pos}. */
    private static boolean isOccupied(ServerLevel world, BlockPos pos) {
        AABB box = new AABB(pos).expandTowards(0.0, 0.25, 0.0);
        return !world.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    /**
     * One stage back along the erosion chain, with a fresh cooldown entry, HUD update, {@code particles} bonemeal
     * particles and, if {@code sound}, the bone meal sound.
     */
    private static void recoverOneStage(ServerLevel world, BlockPos pos, BlockState state, Tool tool, int particles, boolean sound) {
        ErosionMapManager manager = ErosionMapManager.get(world);
        long now = world.getGameTime();
        Block block = state.getBlock();
        BlockState next;
        Block cooldownFor; // null = back to vanilla, no entry

        if (block == TRMTBlocks.ERODED_SAND.get()) {
            int stage = state.getValue(ErodedSandBlock.STAGE);
            if (stage > 0) {
                boolean keepWaterlogged = stage > 1 && state.getValue(ErodedSandBlock.WATERLOGGED);
                next = state.setValue(ErodedSandBlock.STAGE, stage - 1).setValue(ErodedSandBlock.WATERLOGGED, keepWaterlogged);
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
        // Data 0: addGrowthParticles draws 0 particles (and none on an eroded block anyway), so this is the sound only.
        if (sound) world.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
        // The shape is the state before recovery, which the client still has when a level event arrives (as on 1.20.1).
        spawnBonemealParticles(world, pos, state, particles);
    }

    /**
     * Sends {@code count} bonemeal particles ({@code HAPPY_VILLAGER}) for the block at {@code pos}, placed like vanilla
     * {@code ParticleUtils.spawnParticles}: a full opaque block gets {@code count x 3} particles over a 6x1x6 area
     * on top of it (points with air below are skipped, vanilla's {@code allowInAir = false}); any other block gets
     * {@code count} particles inside its outline ({@code spawnParticleInBlock}). Each particle is its own count-0
     * packet, so the client uses the exact position and the given velocity.
     */
    private static void spawnBonemealParticles(ServerLevel world, BlockPos pos, BlockState shapeState, int count) {
        if (count <= 0) return;
        BlockPos base;
        double spread;
        double height;
        boolean force;
        if (shapeState.isSolidRender()) {
            base = pos.above();
            count *= FULL_BLOCK_COUNT_FACTOR;
            spread = FULL_BLOCK_SPREAD;
            height = FULL_BLOCK_HEIGHT;
            force = false;
        } else {
            base = pos;
            spread = INNER_SPREAD;
            height = shapeState.isAir() ? 1.0 : shapeState.getShape(world, pos).max(Direction.Axis.Y);
            force = true;
        }
        RandomSource random = world.getRandom();
        double offset = 0.5 - spread;
        for (int i = 0; i < count; i++) {
            double vx = random.nextGaussian() * VELOCITY_SIGMA;
            double vy = random.nextGaussian() * VELOCITY_SIGMA;
            double vz = random.nextGaussian() * VELOCITY_SIGMA;
            double x = base.getX() + offset + random.nextDouble() * spread * 2.0;
            double y = base.getY() + random.nextDouble() * height;
            double z = base.getZ() + offset + random.nextDouble() * spread * 2.0;
            if (!force && world.getBlockState(BlockPos.containing(x, y, z).below()).isAir()) continue;
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, vx, vy, vz, 1.0);
        }
    }

    private static void log(Tool tool, BlockPos pos, String role, String result) {
        TestLog.line(() -> "recover tool=" + tool.name + " role=" + role
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + result);
    }
}
