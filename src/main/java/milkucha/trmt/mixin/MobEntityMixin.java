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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(Mob.class)
public class MobEntityMixin {

    /** Last block position this mob stood on; kept while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    /** Consecutive ticks this mob has been off the ground. */
    @Unique
    private int trmt$airTicks = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        Mob mob = (Mob)(Object) this;

        if (!(mob.level() instanceof ServerLevel)) return;

        if (!mob.onGround()) {
            if (trmt$airTicks < Integer.MAX_VALUE) trmt$airTicks++;
            return;
        }
        boolean landedFromJump = trmt$airTicks >= ErosionMapManager.LANDING_MIN_AIR_TICKS;
        trmt$airTicks = 0;

        boolean leashed = mob.isLeashed();
        if (!trmt$erodes(mob, leashed)) return;

        if (mob.hasEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;

        BlockPos groundPos = mob.blockPosition().below();

        Level world = mob.level();
        BlockState groundUpState = world.getBlockState(groundPos.above());
        if (groundUpState.is(TRMTBlocks.ERODED_SAND.get())) {
            groundPos = groundPos.above();
        }

        // New block column, or a landing after a real jump/fall (see ServerPlayerEntityMixin).
        boolean sameColumn = ErosionMapManager.sameColumn(groundPos, trmt$lastGroundPos);
        trmt$lastGroundPos = groundPos.immutable();
        if (sameColumn && !landedFromJump) return;

        TRMTConfig.Multipliers m = TRMTConfig.get().erosionMultipliers;
        float mult = m.player * (leashed ? m.leash : m.freeRoaming);

        ErosionMapManager manager = ErosionMapManager.get(world);
        long gameTime = world.getGameTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        BlockPos abovePos = groundPos.above();
        BlockState aboveState = world.getBlockState(abovePos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(aboveState.getBlock())) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, abovePos, aboveState);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
        }
        if (erosion.snowEnabled && aboveState.is(Blocks.SNOW)) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryReduceSnow(world, manager, abovePos, aboveState, mob);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
            // Cross spread to neighbouring snow layers; the layer insulates the ground block from direct erosion.
            trmt$spreadSnow(world, manager, abovePos, Blocks.SNOW, mob.getDirection(), mult, gameTime, mob);
            return;
        }

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        boolean tracked = (erosion.grassEnabled && (state.is(Blocks.GRASS_BLOCK) || state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())))
                || (erosion.dirtEnabled && (state.is(Blocks.DIRT) || state.is(TRMTBlocks.ERODED_DIRT.get()) || state.is(TRMTBlocks.ERODED_COARSE_DIRT.get())))
                || (erosion.sandEnabled && (state.is(Blocks.SAND) || state.is(TRMTBlocks.ERODED_SAND.get())))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block))
                || (erosion.snowEnabled && state.is(Blocks.SNOW_BLOCK));

        if (!tracked) return;

        // A permanent stage neither de-erodes nor erodes further: the step does nothing to it.
        if (BlockThresholds.isPermanentStage(state)) return;

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);

        // A snow block spreads the cross to neighbouring snow blocks only (mobs spread nowhere else).
        if (state.is(Blocks.SNOW_BLOCK)) {
            trmt$spreadSnow(world, manager, groundPos, Blocks.SNOW_BLOCK, mob.getDirection(), mult, gameTime, mob);
        }
    }

    /**
     * Which mobs erode: adult mobs no player rides, if leashed or if their type is in
     * {@code erosionMobWhitelist}. Each rule below is one line so it can be changed on its own.
     */
    @Unique
    private static boolean trmt$erodes(Mob mob, boolean leashed) {
        if (mob.isBaby()) return false; // babies never erode (leashed or not)
        // A player riding this mob already erodes through ServerPlayerEntityMixin's mounted path: don't count twice (leashed or not).
        if (mob.getPassengers().stream().anyMatch(p -> p instanceof Player)) return false;
        if (leashed) return true; // leashed: erodes once, with the leash multiplier, whitelisted or not
        return BlockThresholds.isErodingMob(mob.getType()); // free-roaming: whitelist, entity type ids only
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
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.above();
            if (world.getBlockState(upper).is(state.getBlock())) {
                world.removeBlock(upper, false);
            }
            if (state.is(Blocks.TALL_GRASS)) {
                world.setBlock(pos, Blocks.GRASS.defaultBlockState(), Block.UPDATE_ALL);
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
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        int layers = state.getValue(SnowLayerBlock.LAYERS);
        if (layers > 1) {
            world.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL);
        } else {
            float dropChance = TRMTConfig.get().erosionThresholds.snow.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            // The vanilla snow loot table only drops when an entity breaks the block.
            world.destroyBlock(pos, drops, breaker);
        }
        manager.removeEntry(pos);
    }

    @Unique
    private static void trmt$tryTransform(Level world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

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
                world.setBlock(pos, state.setValue(ErodedDirtBlock.STAGE, currentStage + 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT.get(), world.getGameTime());
                return;
            }
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
