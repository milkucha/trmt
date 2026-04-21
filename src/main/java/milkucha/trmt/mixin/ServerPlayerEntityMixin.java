package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
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
public class ServerPlayerEntityMixin {


    /** Last block position this player was standing on. Null while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Determine whether the player is mounted and, if so, delegate ground detection to the vehicle.
        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle != null;
        boolean onGround = mounted ? vehicle.isOnGround() : player.isOnGround();

        if (!onGround) {
            // Airborne (or vehicle airborne) — clear last ground position so the next landing registers.
            trmt$lastGroundPos = null;
            return;
        }

        // getBlockPos() returns the block at the entity's Y coordinate (feet level).
        // The block they are *standing on* is one below.
        BlockPos groundPos = (mounted ? vehicle.getBlockPos() : player.getBlockPos()).down();

        // Sunken blocks (e.g. ERODED_SAND stages 1–4) have a collision height < 1, so the
        // player's feet land inside the block space and getBlockPos().down() resolves one block
        // too low. Correct by checking one block up when groundPos yields nothing tracked.
        World world = player.getWorld();
        BlockState groundUpState = world.getBlockState(groundPos.up());
        if (groundUpState.isOf(TRMTBlocks.ERODED_SAND) || groundUpState.isOf(Blocks.SAND)) {
            groundPos = groundPos.up();
        }

        // Only process when the player (or vehicle) moves onto a new block, not while standing still.
        if (groundPos.equals(trmt$lastGroundPos)) {
            return;
        }

        trmt$lastGroundPos = groundPos.toImmutable();

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        // Transformation chain:
        //   grass_block (6 visual stages) ──► eroded_dirt (s0→s1→s2→s3) ──► eroded_coarse_dirt ──┐
        //   dirt ───────────────────────────► eroded_dirt (s1→s2→s3)     ──► eroded_coarse_dirt ──┼──► eroded_rooted_dirt (final)
        //   coarse_dirt ──────────────────────────────────────────────────────────────────────────┘
        boolean rootedEnabled = TRMTConfig.get().erodedRootedDirtEnabled;
        boolean tracked = state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.DIRT)
                || state.isOf(TRMTBlocks.ERODED_DIRT)
                || (rootedEnabled && (state.isOf(Blocks.COARSE_DIRT) || state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)))
                || state.isOf(Blocks.SAND)
                || state.isOf(TRMTBlocks.ERODED_SAND)
                || BlockThresholds.isLeaves(block);

        if (!tracked) {
            return;
        }

        // Apply player erosion multiplier; mounted players get an additional configurable boost.
        float mult = TRMTConfig.get().playerErosionMultiplier
                * (mounted ? TRMTConfig.get().mountedErosionMultiplier : 1.0f);

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long gameTime = world.getTime();

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);

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

        // Check for vegetation at the player's feet level (one block above the ground).
        // Vegetation has no collision so the player passes through it — track and break it.
        BlockPos vegPos = groundPos.up();
        BlockState vegState = world.getBlockState(vegPos);
        if (BlockThresholds.isVegetation(vegState.getBlock())) {
            manager.onStep(vegPos, vegState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, vegPos, vegState);
        }
    }

    @Unique
    private static void trmt$stepAdjacent(World world, ErosionMapManager manager,
                                           BlockPos pos, float amount, long gameTime) {
        BlockState adjState = world.getBlockState(pos);
        boolean rootedEnabled = TRMTConfig.get().erodedRootedDirtEnabled;
        if (adjState.isOf(Blocks.GRASS_BLOCK) || adjState.isOf(Blocks.DIRT)
                || adjState.isOf(TRMTBlocks.ERODED_DIRT)
                || (rootedEnabled && (adjState.isOf(Blocks.COARSE_DIRT) || adjState.isOf(TRMTBlocks.ERODED_COARSE_DIRT)))
                || adjState.isOf(Blocks.SAND)
                || adjState.isOf(TRMTBlocks.ERODED_SAND)
                || BlockThresholds.isLeaves(adjState.getBlock())) {
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            trmt$tryTransform(world, manager, pos);
        }
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
        }

        float dropChance = TRMTConfig.get().vegetationDropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        world.breakBlock(pos, drops);
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

        boolean rootedEnabled = TRMTConfig.get().erodedRootedDirtEnabled;

        // Threshold reached — advance visual stage or transform the block.
        if (state.isOf(Blocks.SAND)) {
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
            int stage = state.get(ErodedSandBlock.STAGE);
            if (stage < 4) {
                world.setBlockState(pos, state.with(ErodedSandBlock.STAGE, stage + 1), Block.NOTIFY_ALL);
            }
            manager.removeEntry(pos);
            manager.writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getTime());
            return;
        }

        if (BlockThresholds.isLeaves(state.getBlock())) {
            float dropChance = TRMTConfig.get().leavesDropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            world.breakBlock(pos, drops);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(Blocks.GRASS_BLOCK)) {
            if (entry.getErosionStage() < 5) {
                // Advance to the next visual erosion stage (0-4); stay as grass_block.
                entry.advanceGrassStage(BlockThresholds.randomThreshold(state.getBlock()));
                manager.markForRerender(pos);
                return;
            }
            // Stage 5 reached — convert to eroded_dirt, preserving the same rotation used for the eroded grass overlay.
            Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, erodedFacing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_DIRT)) {
            Direction facing = state.get(ErodedDirtBlock.FACING);
            int currentStage = state.get(ErodedDirtBlock.STAGE);
            if (currentStage < 3) {
                // Advance to the next visual stage, preserving facing.
                world.setBlockState(pos,
                        state.with(ErodedDirtBlock.STAGE, currentStage + 1),
                        Block.NOTIFY_ALL);
                manager.removeEntry(pos);
                return;
            }
            // Stage 3 reached — carry rotation forward to eroded_coarse_dirt.
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_COARSE_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(TRMTBlocks.ERODED_COARSE_DIRT)) {
            if (!rootedEnabled) return;
            Direction facing = state.get(ErodedDirtBlock.FACING);
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_ROOTED_DIRT.getDefaultState().with(ErodedDirtBlock.FACING, facing),
                    Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (state.isOf(Blocks.COARSE_DIRT)) {
            if (!rootedEnabled) return;
            world.setBlockState(pos, TRMTBlocks.ERODED_ROOTED_DIRT.getDefaultState(), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        if (!state.isOf(Blocks.DIRT)) return;
        Direction erodedFacing = trmt$rotationToFacing(BlockThresholds.posRotation(pos));
        world.setBlockState(pos,
                TRMTBlocks.ERODED_DIRT.getDefaultState()
                        .with(ErodedDirtBlock.FACING, erodedFacing)
                        .with(ErodedDirtBlock.STAGE, 1),
                Block.NOTIFY_ALL);
        manager.removeEntry(pos);
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
