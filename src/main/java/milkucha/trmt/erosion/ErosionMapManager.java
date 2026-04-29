package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

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

    // --- Erosion logic ---

    public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        map.recordStep(worldPos, block, amount, currentGameTime);
        state.markDirty();
    }

    public void broadcastEntryUpdate(BlockPos pos, Block block) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(pos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;
        int stage = entry.getErosionStage();
        if (stage == 0 && block == Blocks.GRASS_BLOCK) return;
        if (stage == 0) stage = 1;
        broadcastStageUpdate(pos, stage, entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
    }

    public void removeEntry(BlockPos worldPos) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        map.removeEntry(worldPos);
        state.removeChunkMapIfEmpty(chunkPos);
        state.markDirty();
        broadcastStageUpdate(worldPos, 0, 0f, 0f, 0L);
    }

    public void markForRerender(BlockPos pos) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(pos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;
        broadcastStageUpdate(pos, entry.getErosionStage(), entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
    }

    public ChunkErosionMap getChunkMap(ChunkPos chunkPos) {
        if (state == null) return null;
        return state.getChunkMap(chunkPos);
    }

    public void revertGrassStage(BlockPos worldPos, long currentGameTime) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(worldPos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(worldPos);
        if (entry == null) return;
        entry.revertGrassStage(BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK), currentGameTime);
        state.markDirty();
    }

    public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
        map.putEntry(worldPos.toImmutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.markDirty();
    }

    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.toImmutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.markDirty();
    }

    public void migrateGrassEntries(MinecraftServer server) {
        if (state == null) return;
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) return;

        List<BlockPos> candidates = new ArrayList<>();
        for (ChunkErosionMap chunk : state.getAllChunkMaps().values()) {
            for (Map.Entry<BlockPos, ErosionEntry> e : chunk.getEntries().entrySet()) {
                ErosionEntry entry = e.getValue();
                if (entry.getTrackedBlock() == Blocks.GRASS_BLOCK && entry.getErosionStage() > 0) {
                    candidates.add(e.getKey());
                }
            }
        }

        if (candidates.isEmpty()) return;

        long currentTime = world.getTime();
        int migrated = 0;
        for (BlockPos pos : candidates) {
            ChunkErosionMap chunk = state.getChunkMap(new ChunkPos(pos));
            if (chunk == null) continue;
            ErosionEntry entry = chunk.getEntry(pos);
            if (entry == null) continue;

            if (!world.getBlockState(pos).isOf(Blocks.GRASS_BLOCK)) {
                removeEntry(pos);
                continue;
            }

            int stage = entry.getErosionStage() - 1;
            Direction facing = facingFromPos(pos);
            world.setBlockState(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.getDefaultState()
                            .with(ErodedGrassBlock.FACING, facing)
                            .with(ErodedGrassBlock.STAGE, stage),
                    Block.NOTIFY_ALL);
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
            migrated++;
        }

        if (migrated > 0) {
            TRMT.LOGGER.info("[TRMT] Migrated {} eroded grass entries to eroded_grass_block.", migrated);
        }
    }

    private static Direction facingFromPos(BlockPos pos) {
        return switch (BlockThresholds.posRotation(pos)) {
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }

    public void revertDisabledBlocks(ServerWorld world, ChunkPos chunkPos) {
        if (state == null) return;
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int minY   = world.getBottomY();
        int maxY   = world.getTopYInclusive() + 1;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(x, y, z);
                    Block block = world.getBlockState(mutable).getBlock();

                    if (!t.grassEnabled && block == TRMTBlocks.ERODED_GRASS_BLOCK) {
                        world.setBlockState(mutable.toImmutable(), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                        removeEntry(mutable.toImmutable());
                    } else if (!t.dirtEnabled) {
                        if (block == TRMTBlocks.ERODED_DIRT) {
                            world.setBlockState(mutable.toImmutable(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                            removeEntry(mutable.toImmutable());
                        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
                            world.setBlockState(mutable.toImmutable(), Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);
                            removeEntry(mutable.toImmutable());
                        }
                    } else if (!t.sandEnabled && block == TRMTBlocks.ERODED_SAND) {
                        world.setBlockState(mutable.toImmutable(), Blocks.SAND.getDefaultState(), Block.NOTIFY_ALL);
                        removeEntry(mutable.toImmutable());
                    }
                }
            }
        }
    }

    public void revertDisabledBlocksAllLoaded(MinecraftServer server) {
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int viewDistance = server.getPlayerManager().getViewDistance();
        for (ServerWorld world : server.getWorlds()) {
            Set<ChunkPos> scanned = new HashSet<>();
            for (ServerPlayerEntity player : world.getPlayers()) {
                ChunkPos playerChunk = player.getChunkPos();
                for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                    for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                        ChunkPos cp = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                        if (scanned.add(cp) && world.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null) {
                            revertDisabledBlocks(world, cp);
                        }
                    }
                }
            }
        }
    }

    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        if (state == null) return Collections.emptyMap();
        return state.getAllChunkMaps();
    }

    // --- Networking ---

    public void sendFullSyncToPlayer(ServerPlayerEntity player) {
        if (state == null) return;
        for (Map.Entry<ChunkPos, ChunkErosionMap> chunkEntry : state.getAllChunkMaps().entrySet()) {
            ChunkPos chunkPos = chunkEntry.getKey();
            Map<BlockPos, ErosionEntry> entries = chunkEntry.getValue().getEntries();
            if (entries.isEmpty()) continue;

            List<SyncChunkPayload.Entry> payloadEntries = new ArrayList<>(entries.size());
            for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
                payloadEntries.add(new SyncChunkPayload.Entry(
                    e.getKey(),
                    e.getValue().getErosionStage(),
                    e.getValue().getWalkedOnCount(),
                    e.getValue().getThreshold(),
                    e.getValue().getLastTouchedGameTime()
                ));
            }
            ServerPlayNetworking.send(player, new SyncChunkPayload(chunkPos.x, chunkPos.z, payloadEntries));
        }
    }

    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (server == null) return;
        UpdateStagePayload payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
