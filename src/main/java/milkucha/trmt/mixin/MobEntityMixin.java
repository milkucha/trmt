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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(Mob.class)
public class MobEntityMixin {

    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        Mob mob = (Mob)(Object) this;

        if (!(mob.level() instanceof ServerLevel)) return;

        if (!mob.onGround()) {
            trmt$lastGroundPos = null;
            return;
        }

        if (!mob.isLeashed()) return;

        if (mob.hasEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;

        BlockPos groundPos = mob.blockPosition().below();

        Level world = mob.level();
        BlockState groundUpState = world.getBlockState(groundPos.above());
        if (groundUpState.is(TRMTBlocks.ERODED_SAND) || groundUpState.is(Blocks.SAND)) {
            groundPos = groundPos.above();
        }

        if (groundPos.equals(trmt$lastGroundPos)) return;
        trmt$lastGroundPos = groundPos.immutable();

        float mult = TRMTConfig.get().erosionMultipliers.player
                * TRMTConfig.get().erosionMultipliers.leash;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long gameTime = world.getGameTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        BlockPos vegPos = groundPos.above();
        BlockState vegState = world.getBlockState(vegPos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(vegState.getBlock())) {
            manager.onStep(vegPos, vegState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, vegPos, vegState);
            manager.broadcastEntryUpdate(vegPos, vegState.getBlock());
        }

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        boolean tracked = (erosion.grassEnabled && (state.is(Blocks.GRASS_BLOCK) || state.is(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (state.is(Blocks.DIRT) || state.is(TRMTBlocks.ERODED_DIRT)))
                || (erosion.sandEnabled && (state.is(Blocks.SAND) || state.is(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block));

        if (!tracked) return;

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);
    }

    @Unique
    private static void trmt$tryBreakVegetation(Level world, ErosionMapManager manager,
                                                 BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(ChunkPos.containing(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.above();
            if (world.getBlockState(upper).is(state.getBlock())) {
                world.removeBlock(upper, false);
            }
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
    private static void trmt$tryTransform(Level world, ErosionMapManager manager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ErosionEntry entry = manager.getChunkMap(ChunkPos.containing(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.is(Blocks.SAND)) {
            if (!world.getBlockState(pos.above()).isAir()) return;
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlock(pos,
                    TRMTBlocks.ERODED_SAND.defaultBlockState()
                            .setValue(ErodedSandBlock.FACING, erodedFacing)
                            .setValue(ErodedSandBlock.STAGE, 0),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_SAND)) {
            if (!world.getBlockState(pos.above()).isAir()) return;
            int stage = state.getValue(ErodedSandBlock.STAGE);
            if (stage < 4) {
                world.setBlock(pos, state.setValue(ErodedSandBlock.STAGE, stage + 1), Block.UPDATE_ALL);
            }
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getGameTime());
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
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlock(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.defaultBlockState()
                            .setValue(ErodedGrassBlock.FACING, erodedFacing)
                            .setValue(ErodedGrassBlock.STAGE, 0),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            Direction facing = state.getValue(ErodedGrassBlock.FACING);
            int currentStage = state.getValue(ErodedGrassBlock.STAGE);
            if (currentStage < 4) {
                world.setBlock(pos, state.setValue(ErodedGrassBlock.STAGE, currentStage + 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getGameTime());
                return;
            }
            world.setBlock(pos,
                    TRMTBlocks.ERODED_DIRT.defaultBlockState().setValue(ErodedDirtBlock.FACING, facing),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.is(TRMTBlocks.ERODED_DIRT)) {
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            int currentStage = state.getValue(ErodedDirtBlock.STAGE);
            if (currentStage < 3) {
                world.setBlock(pos, state.setValue(ErodedDirtBlock.STAGE, currentStage + 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                return;
            }
            world.setBlock(pos,
                    TRMTBlocks.ERODED_COARSE_DIRT.defaultBlockState().setValue(ErodedDirtBlock.FACING, facing),
                    Block.UPDATE_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (!state.is(Blocks.DIRT)) return;
        Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
        world.setBlock(pos,
                TRMTBlocks.ERODED_DIRT.defaultBlockState()
                        .setValue(ErodedDirtBlock.FACING, erodedFacing)
                        .setValue(ErodedDirtBlock.STAGE, 1),
                Block.UPDATE_ALL);
        manager.removeEntry(pos);
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
