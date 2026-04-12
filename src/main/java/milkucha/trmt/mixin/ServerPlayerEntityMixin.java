package milkucha.trmt.mixin;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
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

        // Only process when the player (or vehicle) moves onto a new block, not while standing still.
        if (groundPos.equals(trmt$lastGroundPos)) {
            return;
        }

        trmt$lastGroundPos = groundPos.toImmutable();

        World world = player.getWorld();
        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        // Transformation chain:
        //   grass_block (6 visual stages) ──► eroded_coarse_dirt ──┐
        //   dirt ────────────────────────────► eroded_coarse_dirt ──┼──► eroded_rooted_dirt  (final)
        //   coarse_dirt ─────────────────────────────────────────────┘
        boolean tracked = state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(TRMTBlocks.ERODED_COARSE_DIRT);

        if (!tracked) {
            return;
        }

        // Mounted players erode terrain faster — apply a configurable multiplier.
        float mult = mounted ? TRMTConfig.get().mountedErosionMultiplier : 1.0f;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long gameTime = world.getTime();

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(world, manager, groundPos);

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
        if (adjState.isOf(Blocks.GRASS_BLOCK) || adjState.isOf(Blocks.DIRT)
                || adjState.isOf(Blocks.COARSE_DIRT) || adjState.isOf(TRMTBlocks.ERODED_COARSE_DIRT)) {
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            trmt$tryTransform(world, manager, pos);
        }
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
        if (state.isOf(Blocks.GRASS_BLOCK)) {
            if (entry.getErosionStage() < 5) {
                // Advance to the next visual erosion stage (0-4); stay as grass_block.
                entry.advanceGrassStage(BlockThresholds.randomThreshold(state.getBlock()));
                manager.markForRerender(pos);
                return;
            }
            // Stage 5 reached — convert to eroded coarse dirt (visually shorter than normal coarse dirt).
            world.setBlockState(pos, TRMTBlocks.ERODED_COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);
            manager.removeEntry(pos);
            return;
        }

        BlockState nextState;
        if (state.isOf(Blocks.DIRT)) {
            nextState = TRMTBlocks.ERODED_COARSE_DIRT.getDefaultState();
        } else {
            // COARSE_DIRT or ERODED_COARSE_DIRT → terminal state
            nextState = TRMTBlocks.ERODED_ROOTED_DIRT.getDefaultState();
        }

        world.setBlockState(pos, nextState, Block.NOTIFY_ALL);
        manager.removeEntry(pos);
    }
}
