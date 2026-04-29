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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {

    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle != null;
        boolean onGround = mounted ? vehicle.onGround() : player.onGround();

        if (!onGround) {
            trmt$lastGroundPos = null;
            return;
        }

        BlockPos groundPos = (mounted ? vehicle.blockPosition() : player.blockPosition()).below();

        Level level = player.level();
        BlockState groundUpState = level.getBlockState(groundPos.above());
        if (groundUpState.is(TRMTBlocks.ERODED_SAND.get()) || groundUpState.is(Blocks.SAND)) {
            groundPos = groundPos.above();
        }

        if (groundPos.equals(trmt$lastGroundPos)) {
            return;
        }

        trmt$lastGroundPos = groundPos.immutable();

        if (!mounted && player.hasEffect(TRMTEffects.LIGHTNESS)) return;
        if (vehicle instanceof LivingEntity livingVehicle
                && livingVehicle.hasEffect(TRMTEffects.LIGHTNESS)) return;

        BlockState state = level.getBlockState(groundPos);
        Block block = state.getBlock();

        float mult = TRMTConfig.get().erosionMultipliers.player
                * (mounted ? TRMTConfig.get().erosionMultipliers.mounted : 1.0f);

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long gameTime = level.getGameTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        BlockPos vegPos = groundPos.above();
        BlockState vegState = level.getBlockState(vegPos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(vegState.getBlock())) {
            manager.onStep(vegPos, vegState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(level, manager, vegPos, vegState);
            manager.broadcastEntryUpdate(vegPos, vegState.getBlock());
        }

        boolean tracked = (erosion.grassEnabled && (state.is(Blocks.GRASS_BLOCK) || state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())))
                || (erosion.dirtEnabled && (state.is(Blocks.DIRT) || state.is(TRMTBlocks.ERODED_DIRT.get())))
                || (erosion.sandEnabled && (state.is(Blocks.SAND) || state.is(TRMTBlocks.ERODED_SAND.get())))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block));

        if (!tracked) {
            return;
        }

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(level, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);

        Direction facing = player.getDirection();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        trmt$stepAdjacent(level, manager, groundPos.relative(facing), 0.2f * mult, gameTime);
        trmt$stepAdjacent(level, manager, groundPos.relative(left), 0.5f * mult, gameTime);
        trmt$stepAdjacent(level, manager, groundPos.relative(right), 0.5f * mult, gameTime);
    }

    @Unique
    private static void trmt$stepAdjacent(Level level, ErosionMapManager manager,
                                           BlockPos pos, float amount, long gameTime) {
        BlockState adjState = level.getBlockState(pos);
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        if ((erosion.grassEnabled && (adjState.is(Blocks.GRASS_BLOCK) || adjState.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())))
                || (erosion.dirtEnabled && (adjState.is(Blocks.DIRT) || adjState.is(TRMTBlocks.ERODED_DIRT.get())))
                || (erosion.sandEnabled && (adjState.is(Blocks.SAND) || adjState.is(TRMTBlocks.ERODED_SAND.get())))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(adjState.getBlock()))) {
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            trmt$tryTransform(level, manager, pos);
        }
    }

    @Unique
    private static void trmt$tryBreakVegetation(Level level, ErosionMapManager manager,
                                                 BlockPos pos, BlockState state) {
        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        if (state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.above();
            if (level.getBlockState(upper).is(state.getBlock())) {
                level.removeBlock(upper, false);
            }
        }

        float dropChance = TRMTConfig.get().erosionThresholds.vegetation.dropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        level.destroyBlock(pos, drops);
        manager.removeEntry(pos);
    }

    @Unique
    private static void trmt$tryTransform(Level level, ErosionMapManager manager, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        ErosionEntry entry = manager.getChunkMap(new ChunkPos(pos)).getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) {
            return;
        }

        if (state.is(Blocks.SAND)) {
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            level.setBlock(pos,
                TRMTBlocks.ERODED_SAND.get().defaultBlockState()
                    .setValue(ErodedSandBlock.FACING, erodedFacing)
                    .setValue(ErodedSandBlock.STAGE, 0),
                Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), level.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_SAND.get())) {
            int stage = state.getValue(ErodedSandBlock.STAGE);
            if (stage < 4) {
                level.setBlock(pos, state.setValue(ErodedSandBlock.STAGE, stage + 1), Block.UPDATE_ALL);
            }
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND.get(), level.getGameTime());
            return;
        }

        if (BlockThresholds.isLeaves(state.getBlock())) {
            float dropChance = TRMTConfig.get().erosionThresholds.leaves.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            level.destroyBlock(pos, drops);
            manager.removeEntry(pos);
            return;
        }

        if (state.is(Blocks.GRASS_BLOCK)) {
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            level.setBlock(pos,
                TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
                    .setValue(ErodedGrassBlock.FACING, erodedFacing)
                    .setValue(ErodedGrassBlock.STAGE, 0),
                Block.UPDATE_ALL);
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), level.getGameTime());
            return;
        }

        if (state.is(TRMTBlocks.ERODED_GRASS_BLOCK.get())) {
            Direction facing = state.getValue(ErodedGrassBlock.FACING);
            int currentStage = state.getValue(ErodedGrassBlock.STAGE);
            if (currentStage < 4) {
                level.setBlock(pos, state.setValue(ErodedGrassBlock.STAGE, currentStage + 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), level.getGameTime());
                return;
            }
            level.setBlock(pos,
                TRMTBlocks.ERODED_DIRT.get().defaultBlockState().setValue(ErodedDirtBlock.FACING, facing),
                Block.UPDATE_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.is(TRMTBlocks.ERODED_DIRT.get())) {
            Direction facing = state.getValue(ErodedDirtBlock.FACING);
            int currentStage = state.getValue(ErodedDirtBlock.STAGE);
            if (currentStage < 3) {
                level.setBlock(pos, state.setValue(ErodedDirtBlock.STAGE, currentStage + 1), Block.UPDATE_ALL);
                manager.removeEntry(pos);
                return;
            }
            level.setBlock(pos,
                TRMTBlocks.ERODED_COARSE_DIRT.get().defaultBlockState().setValue(ErodedDirtBlock.FACING, facing),
                Block.UPDATE_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (!state.is(Blocks.DIRT)) return;
        Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
        level.setBlock(pos,
            TRMTBlocks.ERODED_DIRT.get().defaultBlockState()
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
