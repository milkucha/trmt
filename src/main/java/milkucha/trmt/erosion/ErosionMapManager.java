package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side singleton that holds all per-chunk erosion maps for the current world session.
 * Delegates storage to {@link ErosionPersistentState} so data survives across sessions.
 * Broadcasts stage changes to all connected clients via Fabric networking.
 */
public class ErosionMapManager {

    private static ErosionMapManager INSTANCE;

    /** Loaded on SERVER_STARTED; null until then. */
    private ErosionPersistentState state;
    private MinecraftServer server;

    private ErosionMapManager() {}

    public static ErosionMapManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ErosionMapManager();
        }
        return INSTANCE;
    }

    /** Called on SERVER_STARTED to load (or create) the persistent erosion state. */
    public void loadState(MinecraftServer server) {
        this.server = server;
        this.state  = ErosionPersistentState.getOrCreate(server);
    }

    /** Called on server stop to release all in-memory state. */
    public static void reset() {
        INSTANCE = null;
    }

    // --- Core Erosion Logic ---

    /**
     * Records one step at worldPos for the given block type.
     * Creates a ChunkErosionMap for the chunk if needed.
     */
    public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = ChunkPos.containing(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        map.recordStep(worldPos, block, amount, currentGameTime);
        state.setDirty();
    }

    /**
     * Broadcasts the current erosion progress for {@code pos} to all clients (HUD sync).
     * Call this once per step, after {@link #onStep} and any transformation check.
     * <p>
     * For grass blocks at erosionStage=0 (pristine, never advanced) no packet is sent so
     * the client never shows an eroded model prematurely. For all other tracked blocks,
     * stage 1 is used as a sentinel so the client stores the entry and can display the HUD.
     */
    public void broadcastEntryUpdate(BlockPos pos, Block block) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(ChunkPos.containing(pos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;
        int stage = entry.getErosionStage();
        // Sentinel check: If it's grass and stage is 0, assume pristine and do nothing.
        if (stage == 0 && block == Blocks.GRASS_BLOCK) return;
        // For all other tracked blocks, use a minimum sentinel stage of 1 if the entry exists but stage is 0.
        int displayStage = (stage == 0) ? 1 : stage;
        broadcastStageUpdate(pos, displayStage, entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
    }

    /**
     * Removes the erosion entry for worldPos (called after a block transformation).
     * Drops the ChunkErosionMap entirely if it becomes empty.
     * Broadcasts stage = 0 so clients clear the entry.
     */
    public void removeEntry(BlockPos worldPos) {
        if (state == null) return;
        ChunkPos chunkPos = ChunkPos.containing(worldPos);
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        map.removeEntry(worldPos);
        state.removeChunkMapIfEmpty(chunkPos);
        state.setDirty();
        // Broadcast stage 0 to signal clients to clear the entry for this position.
        broadcastStageUpdate(worldPos, 0, 0f, 0f, 0L);
    }

    /**
     * Called when a grass erosion stage advances. Broadcasts the new stage to all clients
     * so they can update their local cache and trigger a chunk re-render.
     */
    public void markForRerender(BlockPos pos) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(ChunkPos.containing(pos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;
        broadcastStageUpdate(pos, entry.getErosionStage(), entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
    }

    /**
     * Returns the ChunkErosionMap for the given chunk, or null if no block there has been walked on.
     */
    public ChunkErosionMap getChunkMap(ChunkPos chunkPos) {
        if (state == null) return null;
        return state.getChunkMap(chunkPos);
    }

    /**
     * Reverts grass erosion at worldPos by one stage and resets {@code lastTouchedGameTime}
     * to {@code currentGameTime} so the entry acts as its own cooldown.
     * Call {@link #markForRerender} afterwards to broadcast the new stage to clients.
     */
    public void revertGrassStage(BlockPos worldPos, long currentGameTime) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(ChunkPos.containing(worldPos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(worldPos);
        if (entry == null) return;
        // The actual reversion logic is handled by the block class, this just updates the state tracking.
        entry.revertGrassStage(BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK), currentGameTime);
        state.setDirty();
    }

    /**
     * Writes a grass erosion entry at worldPos with the given stage and a cooldown timestamp.
     * Used when eroded_dirt reverts to grass_block: the block becomes grass again visually
     * but should still display as eroded stage {@code stage} until further de-erosion occurs.
     * Call {@link #markForRerender} afterwards to broadcast the stage to clients.
     */
    public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = ChunkPos.containing(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
        map.putEntry(worldPos.immutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.setDirty();
    }

    /**
     * Writes a fresh cooldown entry at worldPos for {@code block}.
     * Sets walkedOnCount = 0 and lastTouchedGameTime = currentGameTime so the block cannot
     * immediately de-erode again after a revert.
     */
    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = ChunkPos.containing(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.immutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.setDirty();
    }

    /**
     * One-time migration: converts legacy ErosionEntry records that used to drive the
     * client-side proxy model (trackedBlock=GRASS_BLOCK, erosionStage 1-5) into real
     * ERODED_GRASS_BLOCK placements with the equivalent STAGE and FACING.
     * Safe to call on every server start - entries that have already been migrated won't
     * match the criteria and are silently skipped.
     */
    public void migrateGrassEntries(MinecraftServer server) {
        if (state == null) return;
        ServerLevel world = server.overworld();
        if (world == null) return;

        // Collect candidate positions first (getEntries returns an unmodifiable view).
        List<BlockPos> candidates = new ArrayList<>();
        for (ChunkErosionMap chunk : state.getAllChunkMaps().values()) {
            for (Map.Entry<BlockPos, ErosionEntry> e : chunk.getEntries().entrySet()) {
                ErosionEntry entry = e.getValue();
                // Check for the specific pattern of old grass proxy entries that need migration.
                if (entry.getTrackedBlock() == Blocks.GRASS_BLOCK && entry.getErosionStage() > 0) {
                    candidates.add(e.getKey());
                }
            }
        }

        if (candidates.isEmpty()) return;

        long currentTime = world.getGameTime();
        int migrated = 0;
        for (BlockPos pos : candidates) {
            ChunkErosionMap chunk = state.getChunkMap(ChunkPos.containing(pos));
            if (chunk == null) continue;
            ErosionEntry entry = chunk.getEntry(pos);
            if (entry == null) continue;

            // Only process if the block at this position is still grass, indicating it hasn't been processed yet.
            if (!world.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                removeEntry(pos);
                continue;
            }

            int stage = entry.getErosionStage() - 1; // old stages 1-5 -> new STAGE 0-4
            Direction facing = facingFromPos(pos);
            // Place the modern, directional block state.
            world.setBlock(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.defaultBlockState()
                            .setValue(ErodedGrassBlock.FACING, facing)
                            .setValue(ErodedGrassBlock.STAGE, stage),
                    Block.UPDATE_ALL);
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
            migrated++;
        }

        if (migrated > 0) {
            TRMT.LOGGER.info("[TRMT] Migrated {} eroded grass entries to eroded_grass_block.", migrated);
        }
    }

    private static Direction facingFromPos(BlockPos pos) {
        // Determines the direction based on the block's position relative to cardinal directions.
        return switch (BlockThresholds.posRotation(pos)) {
            case 1  -> Direction.WEST; // West face
            case 2  -> Direction.NORTH; // North face
            case 3  -> Direction.EAST;  // East face
            default -> Direction.SOUTH; // Default/South face
        };
    }

    /**
     * Converts all eroded blocks in all currently loaded chunks to their vanilla counterparts,
     * unconditionally. Intended for use before uninstalling the mod.
     */
    public void convertAllErodedToVanilla(MinecraftServer server) {
        if (state == null) return;
        ServerLevel world = server.overworld();
        if (world == null) return;

        // Iterate over all chunks currently tracked for erosion data.
        Set<ChunkPos> chunkPositionsToProcess = new HashSet<>(state.getAllChunkMaps().keySet());

        for (ChunkPos chunkPos : chunkPositionsToProcess) {
            convertChunkToVanilla(world, chunkPos);
        }
    }

    private void convertChunkToVanilla(ServerLevel world, ChunkPos chunkPos) {
        // Iterate over the 16x16 area of the chunk.
        for (int x = chunkPos.getMinBlockX(); x < chunkPos.getMaxBlockX() + 1; x++) {
            for (int z = chunkPos.getMinBlockZ(); z < chunkPos.getMaxBlockZ() + 1; z++) {
                // Iterate over a reasonable height range, assuming the world is loaded enough to check blocks.
                for (int y = world.getMinY(); y <= world.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();

                    if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
                        world.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(pos);
                    } else if (block == TRMTBlocks.ERODED_DIRT) {
                        world.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(pos);
                    } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
                        world.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(pos);
                    } else if (block == TRMTBlocks.ERODED_SAND) {
                        world.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(pos);
                    }
                }
            }
        }
    }

    /** Returns all chunk positions that currently have erosion entries. */
    public Set<ChunkPos> getErodedChunkPositions() {
        if (state == null) return Collections.emptySet();
        return state.getAllChunkMaps().keySet();
    }

    /** Returns an unmodifiable view of all chunk maps. Used by the debug HUD and join sync. */
    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        if (state == null) return Collections.emptyMap();
        return state.getAllChunkMaps();
    }

    // --- Networking ---

    /**
     * Sends the full erosion data for every known chunk to a newly-joined player.
     * One SYNC_CHUNK packet per non-empty chunk.
     */
    public void sendFullSyncToPlayer(ServerPlayer player) {
        if (state == null) return;
        for (Map.Entry<ChunkPos, ChunkErosionMap> chunkEntry : state.getAllChunkMaps().entrySet()) {
            ChunkPos chunkPos = chunkEntry.getKey();
            Map<BlockPos, ErosionEntry> entries = chunkEntry.getValue().getEntries();
            if (entries.isEmpty()) continue;

            List<SyncChunkPayload.Entry> payloadEntries = new ArrayList<>(entries.size());
            for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
                payloadEntries.add(new SyncChunkPayload.Entry(
                        e.getKey(), e.getValue().getErosionStage(), e.getValue().getWalkedOnCount(),
                        e.getValue().getThreshold(), e.getValue().getLastTouchedGameTime()));
            }
            ServerPlayNetworking.send(player, new SyncChunkPayload(chunkPos.x(), chunkPos.z(), payloadEntries));
        }
    }

    /** Broadcasts a single-block stage update to every connected player. */
    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (server == null) return;
        UpdateStagePayload payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
