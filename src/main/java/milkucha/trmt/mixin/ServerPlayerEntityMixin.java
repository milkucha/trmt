package milkucha.trmt.mixin;

import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    private static final int STEP_THRESHOLD = 2;

    /** Last block position this player was standing on. Null while airborne. */
    @Unique
    private BlockPos trmt$lastGroundPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (!player.isOnGround()) {
            // Player is airborne — clear last ground position so the next landing registers.
            trmt$lastGroundPos = null;
            return;
        }

        // getBlockPos() returns the block at the player's Y coordinate, which is the air block
        // the player occupies. The block they are *standing on* is one below.
        BlockPos groundPos = player.getBlockPos().down();

        // Only process when the player moves onto a new block, not while standing still.
        if (groundPos.equals(trmt$lastGroundPos)) {
            return;
        }

        trmt$lastGroundPos = groundPos.toImmutable();

        World world = player.getWorld();
        BlockState state = world.getBlockState(groundPos);
        Block block = state.getBlock();

        // Transformation chain (each stage erodes at STEP_THRESHOLD steps):
        //   grass_block  ─┐
        //                 ├─► coarse_dirt ──► rooted_dirt ──► dirt_path
        //   dirt         ─┘
        boolean tracked = state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(Blocks.ROOTED_DIRT);

        if (!tracked) {
            return;
        }

        ErosionMapManager manager = ErosionMapManager.getInstance();
        manager.onStep(groundPos, block, world.getTime());

        ErosionEntry entry = manager.getChunkMap(new ChunkPos(groundPos)).getEntry(groundPos);
        if (entry == null || entry.getWalkedOnCount() < STEP_THRESHOLD) {
            return;
        }

        // Threshold reached — determine next block in the chain.
        BlockState nextState;
        if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT)) {
            nextState = Blocks.COARSE_DIRT.getDefaultState();
        } else if (state.isOf(Blocks.COARSE_DIRT)) {
            nextState = Blocks.ROOTED_DIRT.getDefaultState();
        } else {
            // ROOTED_DIRT
            nextState = Blocks.DIRT_PATH.getDefaultState();
        }

        world.setBlockState(groundPos, nextState, Block.NOTIFY_ALL);
        manager.removeEntry(groundPos);
    }
}
