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
import java.util.concurrent.ThreadLocalRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {


    /** Last block position this player was standing on. Null while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // Determine whether the player is mounted and, if so, delegate ground detection to the vehicle.
        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle != null;
        boolean onGround = mounted ? vehicle.onGround() : player.onGround();

        if (!onGround) {
            // Airborne (or vehicle airborne) — clear last ground position so the next landing registers.
            trmt$lastGroundPos = null;
            return;
        }

        // getBlockPos() returns the block at the entity's Y coordinate (feet level).
        // The block they are *standing on* is one below.
        BlockPos groundPos = (mounted ? vehicle.blockPosition() : player.blockPosition()).below();

        // Sunken blocks (e.g. ERODED_SAND stages 1–4) have a collision height < 1, so the
        // player's feet land inside the block space and getBlockPos().down() resolves one block
        // too low. Correct by checking one block up when groundPos yields nothing tracked.
        Level world = player.level();
        BlockState groundUpState = world.getBlockState(groundPos.above());
        if (groundUpState.is(TRMTBlocks.ERODED_SAND) || groundUpState.is(Blocks.SAND)) {
            groundPos = groundPos.above();
        }

        // Only process when the player (or vehicle) moves onto a new block, not while standing still.
        if (groundPos.equals(trmt$lastGroundPos)) {
            return;
        }

        trmt$lastGroundPos = groundPos.immutable();

        // Potion of Lightness suppresses erosion for the affected player or their mount.
        if (!mounted && player.hasEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;
        if (vehicle instanceof LivingEntity livingVehicle
                && livingVehicle.hasEffect(TRMTEffects.LIGHTNESS_ENTRY)) return;

        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        // Transformation chain:
        //   grass_block ──► eroded_grass_block (s0→s4) ──► eroded_dirt (s0→s3) ──► eroded_coarse_dirt (final)
        //   dirt ────────► eroded_dirt (s1→s3) ──► eroded_coarse_dirt (final)
        // Apply player erosion multiplier; mounted players get an additional configurable boost.
        float mult = TRMTConfig.get().erosionMultipliers.player
                * (mounted ? TRMTConfig.get().erosionMultipliers.mounted : 1.0f);

        ErosionMapManager manager = ErosionMapManager.getInstance();
        long gameTime = world.getGameTime();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;

        // Check for vegetation at the player's feet level (one block above the ground).
        // Vegetation has no collision so the player passes through it — track and break it.
        // This fires regardless of what the ground block is so that vegetation on any surface
        // can be trampled, even when the ground block's own erosion category is disabled.
        BlockPos vegPos = groundPos.above();
        BlockState vegState = world.getBlockState(vegPos);
        if (erosion.vegetationEnabled && BlockThresholds.isVegetation(vegState.getBlock())) {
            manager.onStep(vegPos, vegState.getBlock(), 1.0f * mult, gameTime);
            trmt$tryBreakVegetation(world, manager, vegPos, vegState);
            manager.broadcastEntryUpdate(vegPos, vegState.getBlock());
        }

        boolean tracked = (erosion.grassEnabled && (state.is(Blocks.GRASS_BLOCK) || state.is(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (state.is(Blocks.DIRT) || state.is(TRMTBlocks.ERODED_DIRT)))
                || (erosion.sandEnabled && (state.is(Blocks.SAND) || state.is(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block));

        if (!tracked) {
            return;
        }

        manager.onStep(groundPos, block, 1.0f * mult, gameTime);
        trmt$tryTransform(world, manager, groundPos);
        manager.broadcastEntryUpdate(groundPos, block);

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
        if ((erosion.grassEnabled && (adjState.is(Blocks.GRASS_BLOCK) || adjState.is(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (adjState.is(Blocks.DIRT) || adjState.is(TRMTBlocks.ERODED_DIRT)))
                || (erosion.sandEnabled && (adjState.is(Blocks.SAND) || adjState.is(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(adjState.getBlock()))) {
            manager.onStep(pos, adjState.getBlock(), amount, gameTime);
            trmt$tryTransform(world, manager, pos);
        }
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
        }

        float dropChance = TRMTConfig.get().erosionThresholds.vegetation.dropChance;
        boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
        world.destroyBlock(pos, drops);
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
        if (state.is(Blocks.SAND)) {
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
            // Threshold reached — place the real eroded grass block at stage 0.
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
            // Stage 4 reached — convert to eroded_dirt, carrying FACING forward.
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
                // Advance to the next visual stage, preserving facing.
                world.setBlock(pos,
                        state.setValue(ErodedDirtBlock.STAGE, currentStage + 1),
                        Block.UPDATE_ALL);
                manager.removeEntry(pos);
                return;
            }
            // Stage 3 reached — carry rotation forward to eroded_coarse_dirt.
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
