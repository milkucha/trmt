package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-dimension erosion data for the current world session: one manager per loaded server world
 * (B1 / GH #69 — the same x/z in two dimensions are separate blocks). Get it with {@link #get(World)}
 * for the world the step / tick / interaction happened in.
 * Holds the map in {@link ErosionPersistentState}; {@link ErosionRegionStorage} persists it per region.
 * Sends erosion data (debug HUD only) to HUD-enabled players watching each chunk via Fabric networking.
 */
public class ErosionMapManager {

    /** One manager per loaded server world, created when that world loads. */
    private static final Map<RegistryKey<World>, ErosionMapManager> INSTANCES = new HashMap<>();
    /** Returned for client worlds and for worlds whose data is not loaded; every method is a no-op on it. */
    private static final ErosionMapManager INACTIVE = new ErosionMapManager(null);
    /** Shared by all dimensions: one {@code data/trmt/erosion/} folder, index and legacy file per save. */
    private static ErosionRegionStorage storage;

    /** Players whose client has the erosion debug HUD on. Only they receive erosion sync packets. */
    private static final Set<UUID> hudPlayers = new HashSet<>();

    /** The world this manager's data belongs to; null only for {@link #INACTIVE}. */
    private final ServerWorld world;
    /** Null only for {@link #INACTIVE}. */
    private final ErosionPersistentState state;

    private ErosionMapManager(ServerWorld world) {
        this.world = world;
        this.state = world != null ? new ErosionPersistentState() : null;
    }

    /** Returns the erosion data of {@code world}'s dimension (an inert manager on the client side). */
    public static ErosionMapManager get(World world) {
        if (world == null || world.isClient) return INACTIVE;
        return INSTANCES.getOrDefault(world.getRegistryKey(), INACTIVE);
    }

    /**
     * Called when a server world is loaded (inside setupServer, before the tick clock starts) to load
     * that dimension's erosion region files. The overworld load also migrates the legacy file.
     */
    public static void loadState(ServerWorld world) {
        if (storage == null) storage = ErosionRegionStorage.forServer(world.getServer());
        ErosionMapManager manager = new ErosionMapManager(world);
        storage.load(manager.state, world.getRegistryKey());
        INSTANCES.put(world.getRegistryKey(), manager);
    }

    /**
     * Called whenever vanilla saves a world's persistent data (autosave, /save-all, server stop;
     * skipped while saving is disabled with /save-off). Writes only that dimension's regions
     * changed since its last save.
     */
    public static void onWorldSave(ServerWorld world) {
        ErosionMapManager manager = INSTANCES.get(world.getRegistryKey());
        if (manager == null || storage == null) return;
        storage.save(manager.state, world.getRegistryKey());
    }

    /** Called on server stop to release all in-memory state. */
    public static void reset() {
        INSTANCES.clear();
        storage = null;
        hudPlayers.clear();
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
        map.putEntry(worldPos.toImmutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.markDirty(chunkPos);
    }

    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.toImmutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.markDirty(chunkPos);
    }

    /** Runs on every dimension's data. */
    public static void migrateGrassEntries(MinecraftServer server) {
        for (ErosionMapManager manager : INSTANCES.values()) {
            manager.migrateGrassEntries();
        }
    }

    private void migrateGrassEntries() {
        if (state == null) return;

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

    /**
     * Every TRMT block and the vanilla block {@code /trmt convert-to-vanilla} replaces it with.
     * A new TRMT block must be added here, or the command leaves it in the world.
     */
    public static final Map<Block, Block> VANILLA_EQUIVALENTS = Map.of(
            TRMTBlocks.ERODED_GRASS_BLOCK, Blocks.GRASS_BLOCK,
            TRMTBlocks.ERODED_DIRT,        Blocks.DIRT,
            TRMTBlocks.ERODED_COARSE_DIRT, Blocks.COARSE_DIRT,
            TRMTBlocks.ERODED_SAND,        Blocks.SAND);

    /**
     * Replaces every TRMT block in {@code chunk} (a loaded chunk of this manager's world) with its
     * vanilla equivalent and removes those blocks' erosion entries. Sections whose palette holds no
     * TRMT block are skipped; the others are scanned completely. Returns the number of blocks replaced.
     * <p>
     * No neighbour or shape updates ({@link Block#NOTIFY_LISTENERS} | {@link Block#FORCE_STATE}):
     * they read the neighbouring blocks, and a neighbour in an unloaded chunk would be loaded
     * synchronously (or generated) by that read. The vanilla block takes the place of its eroded
     * form, so the neighbours stay valid without them.
     */
    public int convertChunkToVanilla(WorldChunk chunk) {
        if (world == null) return 0;
        ChunkSection[] sections = chunk.getSectionArray();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int converted = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section.isEmpty() || !section.hasAny(s -> VANILLA_EQUIVALENTS.containsKey(s.getBlock()))) continue;
            int startY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block vanilla = VANILLA_EQUIVALENTS.get(section.getBlockState(x, y, z).getBlock());
                        if (vanilla == null) continue;
                        mutable.set(startX + x, startY + y, startZ + z);
                        world.setBlockState(mutable, vanilla.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                        removeEntry(mutable.toImmutable());
                        converted++;
                    }
                }
            }
        }
        return converted;
    }

    /** Removes all erosion data of one chunk (after {@code convert-to-vanilla} has visited it). */
    public void clearChunk(ChunkPos chunkPos) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        for (BlockPos pos : new ArrayList<>(map.getEntries().keySet())) {
            removeEntry(pos);
        }
    }

    public Set<ChunkPos> getErodedChunkPositions() {
        if (state == null) return Collections.emptySet();
        return state.getAllChunkMaps().keySet();
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
    public static void setHudEnabled(ServerPlayerEntity player, boolean enabled) {
        TestLog.line(() -> "hud player=" + player.getName().getString() + " enabled=" + enabled);
        if (!enabled) {
            hudPlayers.remove(player.getUuid());
        } else if (hudPlayers.add(player.getUuid())) {
            get(player.getServerWorld()).sendWatchedChunksToPlayer(player);
        }
    }

    /**
     * Sends SYNC_CHUNK for every chunk with erosion data that {@code player} is currently watching.
     * (in this manager's world, which must be the player's world).
     * Iterates the player's chunk tracking view ({@code ChunkFilter}, bounded by view distance);
     * {@link PlayerLookup#tracking} excludes chunks still queued in the player's ChunkDataSender,
     * which get their data from ChunkDataSenderMixin when they are actually sent.
     */
    private void sendWatchedChunksToPlayer(ServerPlayerEntity player) {
        if (state == null) return;
        player.getChunkFilter().forEach(chunkPos -> {
            if (state.getChunkMap(chunkPos) == null) return;
            if (PlayerLookup.tracking(world, chunkPos).contains(player)) {
                sendChunkSyncToPlayer(player, chunkPos);
            }
        });
    }

    /**
     * Sends the erosion data for one chunk to a HUD-enabled player that has just been sent that chunk.
     * Call it on the manager of the chunk's world.
     * Called right after vanilla sends the chunk packet (see ChunkDataSenderMixin),
     * so it is paced by vanilla's chunk batching. Chunks without data send nothing: the client
     * drops its cached entries for a chunk when that chunk loads or unloads on its side.
     */
    public void sendChunkSyncToPlayer(ServerPlayerEntity player, ChunkPos chunkPos) {
        if (state == null || !hudPlayers.contains(player.getUuid())) return;
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
        TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + player.getServerWorld().getRegistryKey().getValue()
                + " chunk=" + chunkPos.x + "," + chunkPos.z + " entries=" + entries.size());
        if (TestLog.enabled()) {
            TestLog.ChunkSummary summary = new TestLog.ChunkSummary();
            for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
                ErosionEntry v = e.getValue();
                summary.add(e.getKey(), v.getErosionStage(), v.getWalkedOnCount(), v.getThreshold(), v.getLastTouchedGameTime());
            }
            summary.log("server", "player=" + player.getName().getString() + " dim=" + player.getServerWorld().getRegistryKey().getValue()
                    + " chunk=" + chunkPos.x + "," + chunkPos.z);
        }
    }

    /**
     * Sends a single-block stage update to every HUD-enabled player watching the block's chunk
     * in this manager's world (players in other dimensions never receive it).
     */
    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (world == null || hudPlayers.isEmpty()) return;
        UpdateStagePayload payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            if (!hudPlayers.contains(player.getUuid())) continue;
            ServerPlayNetworking.send(player, payload);
            TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + world.getRegistryKey().getValue()
                    + " chunk=" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4) + " entries=1");
            TestLog.line(() -> "server-entry player=" + player.getName().getString() + " "
                    + TestLog.entry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime));
        }
    }
}
