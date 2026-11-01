package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side singleton that holds all per-chunk erosion maps for the current world session.
 * Holds the map in {@link ErosionPersistentState}; {@link ErosionRegionStorage} persists it per region.
 * Sends erosion data (debug HUD only) to HUD-enabled players watching each chunk via Fabric networking.
 */
public class ErosionMapManager {

    private static ErosionMapManager INSTANCE;

    /** Loaded when the overworld loads; null until then. */
    private ErosionPersistentState state;
    private ErosionRegionStorage storage;
    private MinecraftServer server;

    /** Players whose client has the erosion debug HUD on. Only they receive erosion sync packets. */
    private final Set<UUID> hudPlayers = new HashSet<>();

    private ErosionMapManager() {}

    public static ErosionMapManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ErosionMapManager();
        }
        return INSTANCE;
    }

    /**
     * Called when the overworld is loaded (inside initServer, before the tick clock starts) to load
     * the erosion region files, migrating the legacy file if needed.
     */
    public void loadState(MinecraftServer server) {
        this.server  = server;
        this.state   = new ErosionPersistentState();
        this.storage = ErosionRegionStorage.forServer(server);
        storage.load(state);
    }

    /**
     * Called whenever vanilla saves a world's persistent data (autosave, /save-all, server stop;
     * skipped while saving is disabled with /save-off). Writes only the regions changed since the last save.
     */
    public void onWorldSave(ServerLevel world) {
        if (state == null || storage == null) return;
        // Erosion is not dimension-scoped yet (B1): everything is stored under the overworld.
        if (world.dimension() != Level.OVERWORLD) return;
        storage.save(state, Level.OVERWORLD);
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
        state.markDirty(chunkPos);
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
        // Only dirty the save when something was actually removed: callers such as block
        // updates and the vanilla-conversion command often remove positions that have no entry.
        if (map.removeEntry(worldPos)) {
            state.removeChunkMapIfEmpty(chunkPos);
            state.markDirty(chunkPos);
        }
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
        state.markDirty(new ChunkPos(worldPos));
    }

    public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
        map.putEntry(worldPos.immutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.markDirty(chunkPos);
    }

    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.immutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.markDirty(chunkPos);
    }

    public void migrateGrassEntries(MinecraftServer server) {
        if (state == null) return;
        ServerLevel world = server.getLevel(Level.OVERWORLD);
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

        long currentTime = world.getGameTime();
        int migrated = 0;
        for (BlockPos pos : candidates) {
            ChunkErosionMap chunk = state.getChunkMap(new ChunkPos(pos));
            if (chunk == null) continue;
            ErosionEntry entry = chunk.getEntry(pos);
            if (entry == null) continue;

            if (!world.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                removeEntry(pos);
                continue;
            }

            int stage = entry.getErosionStage() - 1;
            Direction facing = facingFromPos(pos);
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
        return switch (BlockThresholds.posRotation(pos)) {
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }

    public void convertAllErodedToVanilla(MinecraftServer server) {
        if (state == null) return;
        int viewDistance = server.getPlayerList().getViewDistance();
        for (ServerLevel world : server.getAllLevels()) {
            Set<ChunkPos> scanned = new HashSet<>();
            for (ServerPlayer player : world.players()) {
                ChunkPos playerChunk = player.chunkPosition();
                for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                    for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                        ChunkPos cp = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                        if (scanned.add(cp) && world.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null) {
                            convertChunkToVanilla(world, cp);
                        }
                    }
                }
            }
        }
    }

    private void convertChunkToVanilla(ServerLevel world, ChunkPos chunkPos) {
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int minY   = world.getMinY();
        int maxY   = world.getMinY() + world.getHeight();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(x, y, z);
                    Block block = world.getBlockState(mutable).getBlock();
                    BlockPos immutable = mutable.immutable();
                    if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
                        world.setBlock(immutable, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(immutable);
                    } else if (block == TRMTBlocks.ERODED_DIRT) {
                        world.setBlock(immutable, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(immutable);
                    } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
                        world.setBlock(immutable, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(immutable);
                    } else if (block == TRMTBlocks.ERODED_SAND) {
                        world.setBlock(immutable, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(immutable);
                    }
                }
            }
        }
    }

    public Set<ChunkPos> getErodedChunkPositions() {
        if (state == null) return Collections.emptySet();
        return state.getAllChunkMaps().keySet();
    }

    public void revertDisabledBlocks(ServerLevel world, ChunkPos chunkPos) {
        if (state == null) return;
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int minY   = world.getMinY();
        int maxY   = world.getMinY() + world.getHeight();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(x, y, z);
                    Block block = world.getBlockState(mutable).getBlock();

                    if (!t.grassEnabled && block == TRMTBlocks.ERODED_GRASS_BLOCK) {
                        world.setBlock(mutable.immutable(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(mutable.immutable());
                    } else if (!t.dirtEnabled) {
                        if (block == TRMTBlocks.ERODED_DIRT) {
                            world.setBlock(mutable.immutable(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                            removeEntry(mutable.immutable());
                        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
                            world.setBlock(mutable.immutable(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                            removeEntry(mutable.immutable());
                        }
                    } else if (!t.sandEnabled && block == TRMTBlocks.ERODED_SAND) {
                        world.setBlock(mutable.immutable(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(mutable.immutable());
                    }
                }
            }
        }
    }

    public void revertDisabledBlocksAllLoaded(MinecraftServer server) {
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int viewDistance = server.getPlayerList().getViewDistance();
        for (ServerLevel world : server.getAllLevels()) {
            Set<ChunkPos> scanned = new HashSet<>();
            for (ServerPlayer player : world.players()) {
                ChunkPos playerChunk = player.chunkPosition();
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

    /**
     * Records whether {@code player}'s debug HUD is on (client sends HUD_STATE; disconnect passes false).
     * On enable, sends the erosion data for the chunks the player is already watching.
     */
    public void setHudEnabled(ServerPlayer player, boolean enabled) {
        TestLog.line(() -> "hud player=" + player.getName().getString() + " enabled=" + enabled);
        if (!enabled) {
            hudPlayers.remove(player.getUUID());
        } else if (hudPlayers.add(player.getUUID())) {
            sendWatchedChunksToPlayer(player);
        }
    }

    /**
     * Sends SYNC_CHUNK for every chunk with erosion data that {@code player} is currently watching.
     * Iterates the player's {@code ChunkTrackingView} (bounded by view distance);
     * {@link PlayerLookup#tracking} excludes chunks still pending in the player's PlayerChunkSender,
     * which get their data from ChunkDataSenderMixin when they are actually sent.
     */
    private void sendWatchedChunksToPlayer(ServerPlayer player) {
        if (state == null || server == null) return;
        ServerLevel world = player.level();
        player.getChunkTrackingView().forEach(chunkPos -> {
            if (state.getChunkMap(chunkPos) == null) return;
            if (PlayerLookup.tracking(world, chunkPos).contains(player)) {
                sendChunkSyncToPlayer(player, chunkPos);
            }
        });
    }

    /**
     * Sends the erosion data for one chunk to a HUD-enabled player that has just been sent that chunk.
     * Called right after vanilla sends the chunk packet (see ChunkDataSenderMixin),
     * so it is paced by vanilla's chunk batching. Chunks without data send nothing: the client
     * drops its cached entries for a chunk when that chunk loads or unloads on its side.
     */
    public void sendChunkSyncToPlayer(ServerPlayer player, ChunkPos chunkPos) {
        if (state == null || !hudPlayers.contains(player.getUUID())) return;
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        Map<BlockPos, ErosionEntry> entries = map.getEntries();
        if (entries.isEmpty()) return;

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
        TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + player.level().dimension().identifier()
                + " chunk=" + chunkPos.x + "," + chunkPos.z + " entries=" + entries.size());
        if (TestLog.enabled()) {
            TestLog.ChunkSummary summary = new TestLog.ChunkSummary();
            for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
                ErosionEntry v = e.getValue();
                summary.add(e.getKey(), v.getErosionStage(), v.getWalkedOnCount(), v.getThreshold(), v.getLastTouchedGameTime());
            }
            summary.log("server", "player=" + player.getName().getString() + " dim=" + player.level().dimension().identifier()
                    + " chunk=" + chunkPos.x + "," + chunkPos.z);
        }
    }

    /**
     * Sends a single-block stage update to every HUD-enabled player watching the block's chunk.
     * The erosion map is not dimension-scoped yet, so watchers of that chunk position in any
     * world receive it (same data they would get from {@link #sendChunkSyncToPlayer}).
     */
    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (server == null || hudPlayers.isEmpty()) return;
        UpdateStagePayload payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : PlayerLookup.tracking(world, pos)) {
                if (!hudPlayers.contains(player.getUUID())) continue;
                ServerPlayNetworking.send(player, payload);
                TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + world.dimension().identifier()
                        + " chunk=" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4) + " entries=1");
                TestLog.line(() -> "server-entry player=" + player.getName().getString() + " "
                        + TestLog.entry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime));
            }
        }
    }
}
