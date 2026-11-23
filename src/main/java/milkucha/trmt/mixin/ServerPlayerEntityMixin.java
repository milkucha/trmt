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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin implements PathBuffHolder {

    /** Path speed buff state of this player (see {@link PathSpeedBuff}). */
    @Unique
    private final PathSpeedBuff.State trmt$pathBuff = new PathSpeedBuff.State();

    @Override
    public PathSpeedBuff.State trmt$getPathBuff() {
        return trmt$pathBuff;
    }

    // Path speed buff, dismount: ServerPlayer no longer overrides dismountTo on 1.21.11, so the
    // "placed after dismount" hook is in EntityMixin (Entity.dismountTo), next to removePassenger.

    /** Last block position this player (or their vehicle) stood on; kept while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    /** Consecutive ticks the player (or their vehicle) has been off the ground. */
    @Unique
    private int trmt$airTicks = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // Determine whether the player is mounted and, if so, delegate ground detection to the vehicle.
        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle != null;
        boolean onGround = mounted ? vehicle.onGround() : player.onGround();

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

        // blockPosition() returns the block at the entity's Y coordinate (feet level).
        // The block they are *standing on* is one below.
        BlockPos groundPos = (mounted ? vehicle.blockPosition() : player.blockPosition()).below();

        // Sunken blocks (e.g. ERODED_SAND stages 1–4) have a collision height < 1, so the
        // player's feet land inside the block space and blockPosition().below() resolves one block
        // too low. Correct by checking one block up when groundPos yields nothing tracked.
        Level world = player.level();
        BlockState groundUpState = world.getBlockState(groundPos.above());
        if (groundUpState.is(TRMTBlocks.ERODED_SAND.get())) {
            groundPos = groundPos.above();
        }

        // Only process when the player (or vehicle) moves onto a new block column, or lands after a
        // real jump/fall. Staying in the same column without a real jump is not a step: this covers
        // standing still, a brief onGround flicker, and sinking when the block below gets lower
        // (a snow layer removed, a snow block turned into layers, an eroded sand stage).
        boolean sameColumn = ErosionMapManager.sameColumn(groundPos, trmt$lastGroundPos);
        boolean sameBlock = groundPos.equals(trmt$lastGroundPos);
        trmt$lastGroundPos = groundPos.immutable();

        // Path speed buff: one evaluation per new ground block (or landing after any airborne tick), on its own
        // trigger rather than the erosion step rule below. Deliberately before the step-rule, sneaking and Lightness
        // returns (the buff is about the path, not about wearing it) and independent of the erosion toggles.
        if (!sameBlock || wasAirborne) {
            PathSpeedBuff.onNewGroundBlock(player, vehicle, trmt$pathBuff);
        }

        if (sameColumn && !landedFromJump) {
            return;
        }

        if (player.isShiftKeyDown()) return;

        // Potion of Lightness suppresses erosion for the affected player or their mount.
        if (!mounted && player.hasEffect(TRMTEffects.LIGHTNESS)) return;
        if (vehicle instanceof LivingEntity livingVehicle
                && livingVehicle.hasEffect(TRMTEffects.LIGHTNESS)) return;

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        // Transformation chain:
        //   grass_block ──► eroded_grass_block (s0→s4) ──► eroded_dirt (s0→s3) ──► eroded_coarse_dirt (final)
        //   dirt ────────► eroded_dirt (s1→s3) ──► eroded_coarse_dirt (final)
        // Apply player erosion multiplier; mounted players get an additional configurable boost.
        float mult = TRMTConfig.get().erosionMultipliers.player
                * (mounted ? TRMTConfig.get().erosionMultipliers.mounted : 1.0f);

        ErosionMapManager manager = ErosionMapManager.get(world);
        long gameTime = world.getGameTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        // Check for vegetation at the player's feet level (one block above the ground).
        // Vegetation has no collision so the player passes through it — track and break it.
        // This fires regardless of what the ground block is so that vegetation on any surface
        // can be trampled, even when the ground block's own erosion category is disabled.
        BlockPos abovePos = groundPos.above();
        BlockState aboveState = world.getBlockState(abovePos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(aboveState.getBlock())) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, abovePos, aboveState);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
        }
        if (erosion.snowEnabled && aboveState.is(Blocks.SNOW)) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryReduceSnow(world, manager, abovePos, aboveState, player);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
            // Snow spreads to neighbouring snow layers in the usual cross; the snow layer insulates the
            // ground block, which gets no step and no spread.
            Direction snowFacing = player.getDirection();
            trmt$spreadSnow(world, manager, abovePos, Blocks.SNOW, snowFacing, mult, gameTime, player);
            return;
        }

        boolean tracked = (erosion.grassEnabled && (state.is(Blocks.GRASS_BLOCK) || state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())))
                || (erosion.dirtEnabled && (state.is(Blocks.DIRT) || state.is(TRMTBlocks.ERODED_DIRT.get()) || state.is(TRMTBlocks.ERODED_COARSE_DIRT.get())))
                || (erosion.sandEnabled && (state.is(Blocks.SAND) || state.is(TRMTBlocks.ERODED_SAND.get())))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block))
                || (erosion.snowEnabled && state.is(Blocks.SNOW_BLOCK));

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
        if (state.is(Blocks.SNOW_BLOCK)) {
            trmt$spreadSnow(world, manager, groundPos, Blocks.SNOW_BLOCK, player.getDirection(), mult, gameTime, player);
            return;
        }

        // Spread erosion to adjacent blocks based on the player's facing direction.
        // Front (the direction the player faces): +0.2
        // Left and right: +0.5 each
        // Back: nothing
        Direction facing = player.getDirection();
        Direction left  = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        trmt$stepAdjacent(world, manager, groundPos.relative(facing), 0.2f * mult, gameTime);
        trmt$stepAdjacent(world, manager, groundPos.relative(left),   0.5f * mult, gameTime);
        trmt$stepAdjacent(world, manager, groundPos.relative(right),  0.5f * mult, gameTime);
    }

    @Unique
    private static void trmt$stepAdjacent(Level world, ErosionMapManager manager,
                                           BlockPos pos, float amount, long gameTime) {
        BlockState adjState = world.getBlockState(pos);
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        // Snow never takes spread: snow blocks are not spread targets, and a block under a snow
        // layer is insulated (as it is when stepped on directly).
        if (erosion.snowEnabled && world.getBlockState(pos.above()).is(Blocks.SNOW)) return;
        if ((erosion.grassEnabled && (adjState.is(Blocks.GRASS_BLOCK) || adjState.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())))
                || (erosion.dirtEnabled && (adjState.is(Blocks.DIRT) || adjState.is(TRMTBlocks.ERODED_DIRT.get()) || adjState.is(TRMTBlocks.ERODED_COARSE_DIRT.get())))
                || (erosion.sandEnabled && (adjState.is(Blocks.SAND) || adjState.is(TRMTBlocks.ERODED_SAND.get())))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(adjState.getBlock()))) {
            if (BlockThresholds.isPermanentStage(adjState)) return; // permanent stages do not erode further
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            trmt$tryTransform(world, manager, pos);
            manager.broadcastEntryUpdate(pos, adjState.getBlock());
        }
    }

    /**
     * Cross spread for snow, same weights as the other blocks: front 0.2, left and right 0.5, back nothing.
     * Only a neighbour of the same kind at exactly {@code origin.relative(dir)} is stepped (a snow layer next to
     * a snow layer, a snow block next to a snow block); height differences are ignored.
     */
    @Unique
    private static void trmt$spreadSnow(Level world, ErosionMapManager manager, BlockPos origin,
                                         Block kind, Direction facing, float mult, long gameTime, Entity walker) {
        trmt$stepSnowAdjacent(world, manager, origin.relative(facing), kind, 0.2f * mult, gameTime, walker);
        trmt$stepSnowAdjacent(world, manager, origin.relative(facing.getCounterClockWise()), kind, 0.5f * mult, gameTime, walker);
        trmt$stepSnowAdjacent(world, manager, origin.relative(facing.getClockWise()), kind, 0.5f * mult, gameTime, walker);
    }

    @Unique
    private static void trmt$stepSnowAdjacent(Level world, ErosionMapManager manager, BlockPos pos,
                                               Block kind, float amount, long gameTime, Entity walker) {
        BlockState adjState = world.getBlockState(pos);
        if (!adjState.is(kind)) return;
        manager.onStep(pos, kind, amount, gameTime);
        if (kind == Blocks.SNOW) {
            trmt$tryReduceSnow(world, manager, pos, adjState, walker);
        } else {
            trmt$tryTransform(world, manager, pos);
        }
        manager.broadcastEntryUpdate(pos, kind);
    }

    @Unique
    private static void trmt$tryBreakVegetation(Level world, ErosionMapManager manager,
                                                 BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(ChunkPos.containing(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        // For double-height plants, remove the upper half first (no drops from upper half).
        if (state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.above();
            if (world.getBlockState(upper).is(state.getBlock())) {
                world.removeBlock(upper, false);
            }
            // Tall grass degrades to short grass rather than breaking entirely.
            if (state.is(Blocks.TALL_GRASS)) {
                world.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                return;
            }
        }

        float dropChance = TRMTConfig.get().erosionThresholds.vegetation.dropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        world.destroyBlock(pos, drops);
        manager.removeEntry(pos);
    }

    @Unique
    private static void trmt$tryReduceSnow(Level world, ErosionMapManager manager,
                                            BlockPos pos, BlockState state, Entity breaker) {
        ErosionEntry entry = manager.getChunkMap(ChunkPos.containing(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        int layers = state.getValue(SnowLayerBlock.LAYERS);
        if (layers > 1) {
            world.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL);
        } else {
            float dropChance = TRMTConfig.get().erosionThresholds.snow.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            // The vanilla snow loot table only drops when an entity breaks the block
            // (entity_properties "this"), so the walker must be passed as the breaker.
            world.destroyBlock(pos, drops, breaker);
        }
        manager.removeEntry(pos);
    }

    /**
     * Checks whether the block at {@code pos} has accumulated enough erosion to transform,
     * and if so, advances it to the next stage in the chain and clears its entry.
     */
    @Unique
    private static void trmt$tryTransform(Level world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        ErosionEntry entry = manager.getChunkMap(ChunkPos.containing(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) {
            return;
        }

        // Threshold reached — advance visual stage or transform the block.
        if (state.is(Blocks.SNOW_BLOCK)) {
            world.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7), Block.UPDATE_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.is(Blocks.SAND)) {
            if (!BlockThresholds.canSandErodeFurther(world, pos)) return;
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlock(pos,
                    TRMTBlocks.ERODED_SAND.get().defaultBlockState()
                            .setValue(ErodedSandBlock.FACING, erodedFacing)
                            .setValue(ErodedSandBlock.STAGE, 0),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), world.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_SAND.get())) {
            if (!BlockThresholds.canSandErodeFurther(world, pos)) return;
            int stage = state.getValue(ErodedSandBlock.STAGE);
            if (stage < 4) {
                world.setBlock(pos, state.setValue(ErodedSandBlock.STAGE, stage + 1), Block.UPDATE_ALL);
            }
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), world.getGameTime());
            return;
        }

        if (BlockThresholds.isLeaves(state.getBlock())) {
            float dropChance = TRMTConfig.get().erosionThresholds.leaves.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            world.destroyBlock(pos, drops);
            manager.removeEntry(pos);
            return;
        }

        if (state.is(Blocks.GRASS_BLOCK)) {
            if (world.getBlockState(pos.above()).is(Blocks.WATER)) return;
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlock(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
                            .setValue(ErodedGrassBlock.FACING, erodedFacing)
                            .setValue(ErodedGrassBlock.STAGE, 0)
                            .setValue(ErodedGrassBlock.SNOWY, ErodedGrassBlock.snowyAt(world, pos)),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), world.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())) {
            if (world.getBlockState(pos.above()).is(Blocks.WATER)) return;
            Direction facing = state.getValue(ErodedGrassBlock.FACING);
            int currentStage = state.getValue(ErodedGrassBlock.STAGE);
            if (currentStage < 4) {
                world.setBlock(pos, state.setValue(ErodedGrassBlock.STAGE, currentStage + 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), world.getGameTime());
                return;
            }
            // Stage 4 reached — convert to eroded_dirt, carrying FACING forward.
            world.setBlock(pos,
                    TRMTBlocks.ERODED_DIRT.get().defaultBlockState().setValue(ErodedDirtBlock.FACING, facing),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), world.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_DIRT.get())) {
            if (world.getBlockState(pos.above()).is(Blocks.WATER)) return;
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            int currentStage = state.getValue(ErodedDirtBlock.STAGE);
            if (currentStage < 3) {
                // Advance to the next visual stage, preserving facing.
                world.setBlock(pos,
                        state.setValue(ErodedDirtBlock.STAGE, currentStage + 1),
                        Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), world.getGameTime());
                return;
            }
            // Stage 3 reached — carry rotation forward to eroded_coarse_dirt.
            world.setBlock(pos,
                    TRMTBlocks.ERODED_COARSE_DIRT.get().defaultBlockState()
                            .setValue(ErodedDirtBlock.FACING, facing)
                            .setValue(ErodedDirtBlock.FROM_DIRT, state.getValue(ErodedDirtBlock.FROM_DIRT)),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_COARSE_DIRT.get(), world.getGameTime());
            return;
        }

        // eroded_coarse_dirt is the final stage: it returns here, its step (onStep) only refreshes the de-erosion timer.
        if (!state.is(Blocks.DIRT)) return;
        if (world.getBlockState(pos.above()).is(Blocks.WATER)) return;
        Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
        world.setBlock(pos,
                TRMTBlocks.ERODED_DIRT.get().defaultBlockState()
                        .setValue(ErodedDirtBlock.FACING, erodedFacing)
                        .setValue(ErodedDirtBlock.STAGE, 1)
                        .setValue(ErodedDirtBlock.FROM_DIRT, true),
                Block.UPDATE_ALL);
        manager.removeEntry(pos);
        manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), world.getGameTime());
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
