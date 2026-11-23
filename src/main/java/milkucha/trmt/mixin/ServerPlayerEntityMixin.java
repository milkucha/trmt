package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTEffects;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.path.PathBuffHolder;
import milkucha.trmt.path.PathSpeedBuff;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements PathBuffHolder {

    /** Path speed buff state of this player (see {@link PathSpeedBuff}). */
    @Unique
    private final PathSpeedBuff.State trmt$pathBuff = new PathSpeedBuff.State();

    @Override
    public PathSpeedBuff.State trmt$getPathBuff() {
        return trmt$pathBuff;
    }

    /**
     * Path speed buff: a dismount has just put the player down at their dismount spot (only caller in 1.20.1:
     * {@code LivingEntity.onDismounted}, i.e. every {@code stopRiding}); evaluate it on foot right away.
     */
    @Inject(method = "requestTeleportAndDismount", at = @At("TAIL"))
    private void trmt$onPlacedAfterDismount(double x, double y, double z, CallbackInfo ci) {
        PathSpeedBuff.onPlacedAfterDismount((ServerPlayerEntity) (Object) this, trmt$pathBuff);
    }

    /** Last block position this player (or their vehicle) stood on; kept while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    /** Consecutive ticks the player (or their vehicle) has been off the ground. */
    @Unique
    private int trmt$airTicks = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Determine whether the player is mounted and, if so, delegate ground detection to the vehicle.
        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle != null;
        boolean onGround = mounted ? vehicle.isOnGround() : player.isOnGround();

        // Safety net for the path speed buff: drop it if its carrier is no longer what the player stands on/controls;
        // on the first tick after a dismount, re-gate the spot the player is at now.
        PathSpeedBuff.tickCheck(player, trmt$pathBuff);

        if (!onGround) {
            if (trmt$airTicks < Integer.MAX_VALUE) trmt$airTicks++;
            return;
        }
        boolean landedFromJump = trmt$airTicks >= ErosionMapManager.LANDING_MIN_AIR_TICKS;
        // Path speed buff: any time off the ground counts, as a landing has always re-evaluated the buff.
        boolean wasAirborne = trmt$airTicks > 0;
        trmt$airTicks = 0;

        // getBlockPos() returns the block at the entity's Y coordinate (feet level).
        // The block they are *standing on* is one below.
        BlockPos groundPos = (mounted ? vehicle.getBlockPos() : player.getBlockPos()).down();

        // Sunken blocks (e.g. ERODED_SAND stages 1–4) have a collision height < 1, so the
        // player's feet land inside the block space and getBlockPos().down() resolves one block
        // too low. Correct by checking one block up when groundPos yields nothing tracked.
        World world = player.getWorld();
        BlockState groundUpState = world.getBlockState(groundPos.up());
        if (groundUpState.isOf(TRMTBlocks.ERODED_SAND)) {
            groundPos = groundPos.up();
        }

        // Only process when the player (or vehicle) moves onto a new block column, or lands after a
        // real jump/fall. Staying in the same column without a real jump is not a step: this covers
        // standing still, a brief onGround flicker, and sinking when the block below gets lower
        // (a snow layer removed, a snow block turned into layers, an eroded sand stage).
        boolean sameColumn = ErosionMapManager.sameColumn(groundPos, trmt$lastGroundPos);
        boolean sameBlock = groundPos.equals(trmt$lastGroundPos);
        trmt$lastGroundPos = groundPos.toImmutable();

        // Path speed buff: one evaluation per new ground block (or landing after any airborne tick), on its own
        // trigger rather than the erosion step rule below. Deliberately before the step-rule, sneaking and Lightness
        // returns (the buff is about the path, not about wearing it) and independent of the erosion toggles.
        if (!sameBlock || wasAirborne) {
            PathSpeedBuff.onNewGroundBlock(player, vehicle, trmt$pathBuff);
        }

        if (sameColumn && !landedFromJump) {
            return;
        }

        if (player.isSneaking()) return;

        // Potion of Lightness suppresses erosion for the affected player or their mount.
        if (!mounted && player.hasStatusEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;
        if (vehicle instanceof LivingEntity livingVehicle
                && livingVehicle.hasStatusEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        // Transformation chain:
        //   grass_block ──► eroded_grass_block (s0→s4) ──► eroded_dirt (s0→s3) ──► eroded_coarse_dirt (final)
        //   dirt ────────► eroded_dirt (s1→s3) ──► eroded_coarse_dirt (final)
        // Apply player erosion multiplier; mounted players get an additional configurable boost.
        float mult = TRMTConfig.get().erosionMultipliers.player
                * (mounted ? TRMTConfig.get().erosionMultipliers.mounted : 1.0f);

        ErosionMapManager manager = ErosionMapManager.get(world);
        long gameTime = world.getTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        // Check for vegetation at the player's feet level (one block above the ground).
        // Vegetation has no collision so the player passes through it — track and break it.
        // This fires regardless of what the ground block is so that vegetation on any surface
        // can be trampled, even when the ground block's own erosion category is disabled.
        BlockPos abovePos = groundPos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(aboveState.getBlock())) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, abovePos, aboveState);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
        }
        if (erosion.snowEnabled && aboveState.isOf(Blocks.SNOW)) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryReduceSnow(world, manager, abovePos, aboveState, player);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
            // Snow spreads to neighbouring snow layers in the usual cross; the snow layer insulates the
            // ground block, which gets no step and no spread.
            Direction snowFacing = player.getHorizontalFacing();
            trmt$spreadSnow(world, manager, abovePos, Blocks.SNOW, snowFacing, mult, gameTime, player);
            return;
        }

        boolean tracked = (erosion.grassEnabled && (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (state.isOf(Blocks.DIRT) || state.isOf(TRMTBlocks.ERODED_DIRT) || state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)))
                || (erosion.sandEnabled && (state.isOf(Blocks.SAND) || state.isOf(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block))
                || (erosion.snowEnabled && state.isOf(Blocks.SNOW_BLOCK));

        if (!tracked) {
            return;
        }

        // A permanent stage neither de-erodes nor erodes further: the step does nothing to it
        // (its neighbours still get their share below).
        if (!BlockThresholds.isPermanentStage(state)) {
            manager.onStep(groundPos, block, 1.0f * mult, gameTime);
            trmt$tryTransform(world, manager, groundPos);
            manager.broadcastEntryUpdate(groundPos, block);
        }

        // A snow block spreads the cross to neighbouring snow blocks only; other neighbours get nothing.
        if (state.isOf(Blocks.SNOW_BLOCK)) {
            trmt$spreadSnow(world, manager, groundPos, Blocks.SNOW_BLOCK, player.getHorizontalFacing(), mult, gameTime, player);
            return;
        }

        // Spread erosion to adjacent blocks based on the player's facing direction.
        // Front (the direction the player faces): +0.2
        // Left and right: +0.5 each
        // Back: nothing
        Direction facing = player.getHorizontalFacing();
        Direction left  = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        trmt$stepAdjacent(world, manager, groundPos.offset(facing), 0.2f * mult, gameTime);
        trmt$stepAdjacent(world, manager, groundPos.offset(left),   0.5f * mult, gameTime);
        trmt$stepAdjacent(world, manager, groundPos.offset(right),  0.5f * mult, gameTime);
    }

    @Unique
    private static void trmt$stepAdjacent(World world, ErosionMapManager manager,
                                           BlockPos pos, float amount, long gameTime) {
        BlockState adjState = world.getBlockState(pos);
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        // Snow never takes spread: snow blocks are not spread targets, and a block under a snow
        // layer is insulated (as it is when stepped on directly).
        if (erosion.snowEnabled && world.getBlockState(pos.up()).isOf(Blocks.SNOW)) return;
        if ((erosion.grassEnabled && (adjState.isOf(Blocks.GRASS_BLOCK) || adjState.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (adjState.isOf(Blocks.DIRT) || adjState.isOf(TRMTBlocks.ERODED_DIRT) || adjState.isOf(TRMTBlocks.ERODED_COARSE_DIRT)))
                || (erosion.sandEnabled && (adjState.isOf(Blocks.SAND) || adjState.isOf(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(adjState.getBlock()))) {
            if (BlockThresholds.isPermanentStage(adjState)) return; // permanent stages do not erode further
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            trmt$tryTransform(world, manager, pos);
            manager.broadcastEntryUpdate(pos, adjState.getBlock());
        }
    }

    /**
     * Cross spread for snow, same weights as the other blocks: front 0.2, left and right 0.5, back nothing.
     * Only a neighbour of the same kind at exactly {@code origin.offset(dir)} is stepped (a snow layer next to
     * a snow layer, a snow block next to a snow block); height differences are ignored.
     */
    @Unique
    private static void trmt$spreadSnow(World world, ErosionMapManager manager, BlockPos origin,
                                         Block kind, Direction facing, float mult, long gameTime, Entity walker) {
        trmt$stepSnowAdjacent(world, manager, origin.offset(facing), kind, 0.2f * mult, gameTime, walker);
        trmt$stepSnowAdjacent(world, manager, origin.offset(facing.rotateYCounterclockwise()), kind, 0.5f * mult, gameTime, walker);
        trmt$stepSnowAdjacent(world, manager, origin.offset(facing.rotateYClockwise()), kind, 0.5f * mult, gameTime, walker);
    }

    @Unique
    private static void trmt$stepSnowAdjacent(World world, ErosionMapManager manager, BlockPos pos,
                                               Block kind, float amount, long gameTime, Entity walker) {
        BlockState adjState = world.getBlockState(pos);
        if (!adjState.isOf(kind)) return;
        manager.onStep(pos, kind, amount, gameTime);
        if (kind == Blocks.SNOW) {
            trmt$tryReduceSnow(world, manager, pos, adjState, walker);
        } else {
            trmt$tryTransform(world, manager, pos);
        }
        manager.broadcastEntryUpdate(pos, kind);
    }

    @Unique
    private static void trmt$tryBreakVegetation(World world, ErosionMapManager manager,
                                                 BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        // For double-height plants, remove the upper half first (no drops from upper half).
        if (state.getBlock() instanceof TallPlantBlock
                && state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.up();
            if (world.getBlockState(upper).isOf(state.getBlock())) {
                world.removeBlock(upper, false);
            }
            // Tall grass degrades to short grass rather than breaking entirely.
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

    @Unique
    private static void trmt$tryReduceSnow(World world, ErosionMapManager manager,
                                            BlockPos pos, BlockState state, Entity breaker) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        int layers = state.get(SnowBlock.LAYERS);
        if (layers > 1) {
            world.setBlockState(pos, state.with(SnowBlock.LAYERS, layers - 1), Block.NOTIFY_ALL);
        } else {
            float dropChance = TRMTConfig.get().erosionThresholds.snow.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            // The vanilla snow loot table only drops when an entity breaks the block
            // (entity_properties "this"), so the walker must be passed as the breaker.
            world.breakBlock(pos, drops, breaker);
        }
        manager.removeEntry(pos);
    }

    /**
     * Checks whether the block at {@code pos} has accumulated enough erosion to transform,
     * and if so, advances it to the next stage in the chain and clears its entry.
     */
    @Unique
    private static void trmt$tryTransform(World world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) {
            return;
        }

        // Threshold reached — advance visual stage or transform the block.
        if (state.isOf(Blocks.SNOW_BLOCK)) {
            world.setBlockState(pos, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 7), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(Blocks.SAND)) {
            if (!BlockThresholds.canSandErodeFurther(world, pos)) return;
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_SAND.getDefaultState()
                            .with(ErodedSandBlock.FACING, erodedFacing)
                            .with(ErodedSandBlock.STAGE, 0),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getTime());
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_SAND)) {
            if (!BlockThresholds.canSandErodeFurther(world, pos)) return;
            int stage = state.get(ErodedSandBlock.STAGE);
            if (stage < 4) {
                world.setBlockState(pos, state.with(ErodedSandBlock.STAGE, stage + 1), Block.NOTIFY_ALL);
            }
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getTime());
            return;
        }

        if (BlockThresholds.isLeaves(state.getBlock())) {
            float dropChance = TRMTConfig.get().erosionThresholds.leaves.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            world.breakBlock(pos, drops);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(Blocks.GRASS_BLOCK)) {
            if (world.getBlockState(pos.up()).isOf(Blocks.WATER)) return;
            // Threshold reached — place the real eroded grass block at stage 0.
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                            .with(ErodedGrassBlock.FACING, erodedFacing)
                            .with(ErodedGrassBlock.STAGE, 0)
                            .with(ErodedGrassBlock.SNOWY, ErodedGrassBlock.snowyAt(world, pos)),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getTime());
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            if (world.getBlockState(pos.up()).isOf(Blocks.WATER)) return;
            Direction facing = state.get(ErodedGrassBlock.FACING);
            int currentStage = state.get(ErodedGrassBlock.STAGE);
            if (currentStage < 4) {
                world.setBlockState(pos, state.with(ErodedGrassBlock.STAGE, currentStage + 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getTime());
                return;
            }
            // Stage 4 reached — convert to eroded_dirt, carrying FACING forward.
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime());
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_DIRT)) {
            if (world.getBlockState(pos.up()).isOf(Blocks.WATER)) return;
            Direction facing = state.get(ErodedDirtBlock.FACING);
            int currentStage = state.get(ErodedDirtBlock.STAGE);
            if (currentStage < 3) {
                // Advance to the next visual stage, preserving facing.
                world.setBlockState(pos,
                        state.with(ErodedDirtBlock.STAGE, currentStage + 1),
                        Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime());
                return;
            }
            // Stage 3 reached — carry rotation forward to eroded_coarse_dirt.
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_COARSE_DIRT.getDefaultState()
                            .with(ErodedDirtBlock.FACING, facing)
                            .with(ErodedDirtBlock.FROM_DIRT, state.get(ErodedDirtBlock.FROM_DIRT)),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_COARSE_DIRT, world.getTime());
            return;
        }

        // eroded_coarse_dirt is the final stage: it returns here, its step (onStep) only refreshes the de-erosion timer.
        if (!state.isOf(Blocks.DIRT)) return;
        if (world.getBlockState(pos.up()).isOf(Blocks.WATER)) return;
        Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
        world.setBlockState(pos,
                TRMTBlocks.ERODED_DIRT.getDefaultState()
                        .with(ErodedDirtBlock.FACING, erodedFacing)
                        .with(ErodedDirtBlock.STAGE, 1)
                        .with(ErodedDirtBlock.FROM_DIRT, true),
                Block.NOTIFY_ALL);
        manager.removeEntry(pos);
        manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime());
    }

    /**
     * Maps a position rotation index (0–3, matching {@link BlockThresholds#posRotation})
     * to the corresponding {@link Direction} for the FACING block-state property.
     * 0 = SOUTH (0°), 1 = WEST (90° CW), 2 = NORTH (180°), 3 = EAST (270° CW).
     */
    @Unique
    private static Direction trmt$rotationToFacing(int rotation) {
        return switch (rotation) {
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }
}
