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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldEvents;

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
 * block (the brush has its own sound). 1.21.1 differs from 1.20.1 here: world event 2005 ({@code PLANT_FERTILIZED},
 * particles without sound) no longer exists, and {@code BoneMealItem.createParticles} (behind
 * {@link WorldEvents#BONE_MEAL_USED}) no longer turns a count of 0 into 15 and draws nothing on a block that is
 * not {@code Fertilizable} (all eroded blocks). So the sound comes from {@code BONE_MEAL_USED} with data 0 (sound,
 * no particles), and all particles are sent from the server ({@link #spawnBonemealParticles}), placed like
 * vanilla's {@code ParticleUtil.spawnParticlesAround}.
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

    // Particle placement, as vanilla ParticleUtil.spawnParticlesAround called from BoneMealItem.createParticles.
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
        recoverOneStage(world, pos, state, tool, particles, sound); // replaces the entry: progress restarts at 0
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

    /**
     * One stage back along the erosion chain, with a fresh cooldown entry, HUD update, {@code particles} bonemeal
     * particles and, if {@code sound}, the bone meal sound.
     */
    private static void recoverOneStage(ServerWorld world, BlockPos pos, BlockState state, Tool tool, int particles, boolean sound) {
        ErosionMapManager manager = ErosionMapManager.get(world);
        long now = world.getTime();
        Block block = state.getBlock();
        BlockState next;
        Block cooldownFor; // null = back to vanilla, no entry

        if (block == TRMTBlocks.ERODED_SAND) {
            int stage = state.get(ErodedSandBlock.STAGE);
            if (stage > 0) {
                boolean keepWaterlogged = stage > 1 && state.get(Properties.WATERLOGGED);
                next = state.with(ErodedSandBlock.STAGE, stage - 1).with(Properties.WATERLOGGED, keepWaterlogged);
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
                // Stage 0 → fully recovered, vanilla grass (snowy if snow lies on top).
                next = ErodedGrassBlock.grassStateFor(world, pos);
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
                        .with(ErodedGrassBlock.STAGE, 4)
                        .with(ErodedGrassBlock.SNOWY, ErodedGrassBlock.snowyAt(world, pos));
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
        // Data 0: createParticles draws 0 particles (and none on an eroded block anyway), so this is the sound only.
        if (sound) world.syncWorldEvent(WorldEvents.BONE_MEAL_USED, pos, 0);
        // The shape is the state before recovery, which the client still has when a world event arrives (as on 1.20.1).
        spawnBonemealParticles(world, pos, state, particles);
    }

    /**
     * Sends {@code count} bonemeal particles ({@code HAPPY_VILLAGER}) for the block at {@code pos}, placed like vanilla
     * {@code ParticleUtil.spawnParticlesAround}: a full opaque block gets {@code count x 3} particles over a 6x1x6 area
     * on top of it (points with air below are skipped, vanilla's {@code force = false}); any other block gets
     * {@code count} particles inside its outline. Each particle is its own count-0 packet, so the client uses the
     * exact position and the given velocity.
     */
    private static void spawnBonemealParticles(ServerWorld world, BlockPos pos, BlockState shapeState, int count) {
        if (count <= 0) return;
        BlockPos base;
        double spread;
        double height;
        boolean force;
        if (shapeState.isOpaqueFullCube(world, pos)) {
            base = pos.up();
            count *= FULL_BLOCK_COUNT_FACTOR;
            spread = FULL_BLOCK_SPREAD;
            height = FULL_BLOCK_HEIGHT;
            force = false;
        } else {
            base = pos;
            spread = INNER_SPREAD;
            height = shapeState.isAir() ? 1.0 : shapeState.getOutlineShape(world, pos).getMax(Direction.Axis.Y);
            force = true;
        }
        Random random = world.getRandom();
        double offset = 0.5 - spread;
        for (int i = 0; i < count; i++) {
            double vx = random.nextGaussian() * VELOCITY_SIGMA;
            double vy = random.nextGaussian() * VELOCITY_SIGMA;
            double vz = random.nextGaussian() * VELOCITY_SIGMA;
            double x = base.getX() + offset + random.nextDouble() * spread * 2.0;
            double y = base.getY() + random.nextDouble() * height;
            double z = base.getZ() + offset + random.nextDouble() * spread * 2.0;
            if (!force && world.getBlockState(BlockPos.ofFloored(x, y, z).down()).isAir()) continue;
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, vx, vy, vz, 1.0);
        }
    }

    private static void log(Tool tool, BlockPos pos, String role, String result) {
        TestLog.line(() -> "recover tool=" + tool.name + " role=" + role
                + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + result);
    }
}
