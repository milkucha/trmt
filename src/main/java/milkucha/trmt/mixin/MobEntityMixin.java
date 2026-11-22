package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.TRMTEffects;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(MobEntity.class)
public class MobEntityMixin {

    /** Last block position this mob stood on; kept while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    /** Consecutive ticks this mob has been off the ground. */
    @Unique
    private int trmt$airTicks = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        MobEntity mob = (MobEntity)(Object) this;

        if (!(mob.getWorld() instanceof ServerWorld)) return;

        if (!mob.isOnGround()) {
            if (trmt$airTicks < Integer.MAX_VALUE) trmt$airTicks++;
            return;
        }
        boolean landedFromJump = trmt$airTicks >= ErosionMapManager.LANDING_MIN_AIR_TICKS;
        trmt$airTicks = 0;

        if (!mob.isLeashed()) return;

        if (mob.hasStatusEffect(TRMTEffects.LIGHTNESS)) return;

        BlockPos groundPos = mob.getBlockPos().down();

        World world = mob.getWorld();
        BlockState groundUpState = world.getBlockState(groundPos.up());
        if (groundUpState.isOf(TRMTBlocks.ERODED_SAND)) {
            groundPos = groundPos.up();
        }

        // New block column, or a landing after a real jump/fall (see ServerPlayerEntityMixin).
        boolean sameColumn = ErosionMapManager.sameColumn(groundPos, trmt$lastGroundPos);
        trmt$lastGroundPos = groundPos.toImmutable();
        if (sameColumn && !landedFromJump) return;

        float mult = TRMTConfig.get().erosionMultipliers.player
                * TRMTConfig.get().erosionMultipliers.leash;

        ErosionMapManager manager = ErosionMapManager.get(world);
        long gameTime = world.getTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        BlockPos abovePos = groundPos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(aboveState.getBlock())) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, abovePos, aboveState);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
        }
        if (erosion.snowEnabled && aboveState.isOf(Blocks.SNOW)) {
            manager.onStep(abovePos, aboveState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryReduceSnow(world, manager, abovePos, aboveState, mob);
            manager.broadcastEntryUpdate(abovePos, aboveState.getBlock());
            return; // snow layer insulates the ground block from direct erosion
        }

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        boolean tracked = (erosion.grassEnabled && (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (state.isOf(Blocks.DIRT) || state.isOf(TRMTBlocks.ERODED_DIRT) || state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)))
                || (erosion.sandEnabled && (state.isOf(Blocks.SAND) || state.isOf(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block))
                || (erosion.snowEnabled && state.isOf(Blocks.SNOW_BLOCK));

        if (!tracked) return;

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);
    }

    @Unique
    private static void trmt$tryBreakVegetation(World world, ErosionMapManager manager,
                                                 BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.getBlock() instanceof TallPlantBlock
                && state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.up();
            if (world.getBlockState(upper).isOf(state.getBlock())) {
                world.removeBlock(upper, false);
            }
            // Tall grass degrades to short grass rather than breaking entirely.
            if (state.isOf(Blocks.TALL_GRASS)) {
                world.setBlockState(pos, Blocks.GRASS.getDefaultState(), Block.NOTIFY_ALL);
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
            // The vanilla snow loot table only drops when an entity breaks the block.
            world.breakBlock(pos, drops, breaker);
        }
        manager.removeEntry(pos);
    }

    @Unique
    private static void trmt$tryTransform(World world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

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
                world.setBlockState(pos, state.with(ErodedDirtBlock.STAGE, currentStage + 1), Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_DIRT, world.getTime());
                return;
            }
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
