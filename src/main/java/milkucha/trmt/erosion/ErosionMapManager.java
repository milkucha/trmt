package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
 * (B1 / GH #69 — the same x/z in two dimensions are separate blocks). Get it with {@link #get(Level)}
 * for the world the step / tick / interaction happened in.
 * Holds the map in {@link ErosionPersistentState}; {@link ErosionRegionStorage} persists it per region.
 * Sends erosion data (debug HUD only) to HUD-enabled players watching each chunk via Fabric networking.
 */
public class ErosionMapManager {

    /**
     * A landing in the same block column counts as a new step only after at least this many ticks
     * off the ground (a jump is ~12). Shorter airtime is sinking (a snow layer removed: ~2 ticks,
     * a snow block turned into 7 layers: ~3) or an onGround flicker while standing still.
     */
    public static final int LANDING_MIN_AIR_TICKS = 5;

    private static final long TICKS_PER_DAY = 24000L;

    /** True if both positions are in the same x/z column ({@code b} may be null). */
    public static boolean sameColumn(BlockPos a, BlockPos b) {
        return b != null && a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    /** One manager per loaded server world, created when that world loads. */
    private static final Map<ResourceKey<Level>, ErosionMapManager> INSTANCES = new HashMap<>();
    /** Returned for client worlds and for worlds whose data is not loaded; every method is a no-op on it. */
    private static final ErosionMapManager INACTIVE = new ErosionMapManager(null);
    /** Shared by all dimensions: one {@code data/trmt/erosion/} folder, index and legacy file per save. */
    private static ErosionRegionStorage storage;

    /** Players whose client has the erosion debug HUD on. Only they receive erosion sync packets. */
    private static final Set<UUID> hudPlayers = new HashSet<>();

    /** The world this manager's data belongs to; null only for {@link #INACTIVE}. */
    private final ServerLevel world;
    /** Null only for {@link #INACTIVE}. */
    private final ErosionPersistentState state;

    private ErosionMapManager(ServerLevel world) {
        this.world = world;
        this.state = world != null ? new ErosionPersistentState() : null;
    }

    /** Returns the erosion data of {@code world}'s dimension (an inert manager on the client side). */
    public static ErosionMapManager get(Level world) {
        if (world == null || world.isClientSide()) return INACTIVE;
        return INSTANCES.getOrDefault(world.dimension(), INACTIVE);
    }

    /**
     * Called when a server world is loaded (inside initServer, before the tick clock starts) to load
     * that dimension's erosion region files. The overworld load also migrates the legacy file.
     */
    public static void loadState(ServerLevel world) {
        if (storage == null) storage = ErosionRegionStorage.forServer(world.getServer());
        ErosionMapManager manager = new ErosionMapManager(world);
        storage.load(manager.state, world.dimension());
        INSTANCES.put(world.dimension(), manager);
        manager.pruneExpired(new ArrayList<>(manager.state.getAllChunkMaps().keySet()));
    }

    /** Called when vanilla loads a chunk into the world: drops that chunk's expired entries. */
    public static void onChunkLoad(ServerLevel world, ChunkPos chunkPos) {
        get(world).pruneExpired(List.of(chunkPos));
    }

    /**
     * Drops expired entries in the given chunks: progress on a vanilla block (stage 0, not a
     * {@code trmt:eroded_*} block) untouched for {@code entryExpiryDays}. Entries of eroded blocks
     * are kept: they are the de-erosion timers. Runs at world load, chunk load and save — never polled.
     */
    private void pruneExpired(Iterable<ChunkPos> chunks) {
        if (state == null) return;
        float days = milkucha.trmt.TRMTConfig.get().entryExpiryDays;
        if (days <= 0) return;
        long cutoff = world.getGameTime() - (long) (days * TICKS_PER_DAY);
        int removed = 0;
        for (ChunkPos chunkPos : chunks) {
            ChunkErosionMap map = state.getChunkMap(chunkPos);
            if (map == null) continue;
            int n = map.pruneExpired(cutoff);
            if (n > 0) {
                removed += n;
                state.removeChunkMapIfEmpty(chunkPos);
                state.markDirty(chunkPos);
            }
        }
        if (removed > 0) {
            TRMT.LOGGER.debug("[TRMT] {}: dropped {} expired erosion entries", world.dimension().identifier(), removed);
        }
    }

    /**
     * Called whenever vanilla saves a world's persistent data (autosave, /save-all, server stop;
     * skipped while saving is disabled with /save-off). Writes only that dimension's regions
     * changed since its last save.
     */
    public static void onWorldSave(ServerLevel world) {
        ErosionMapManager manager = INSTANCES.get(world.dimension());
        if (manager == null || storage == null) return;
        // Regions about to be written: drop their expired entries first.
        List<ChunkPos> dirtyChunks = new ArrayList<>();
        for (long region : manager.state.getDirtyRegions()) {
            dirtyChunks.addAll(manager.state.getRegionChunks(region));
        }
        manager.pruneExpired(dirtyChunks);
        storage.save(manager.state, world.dimension());
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
            broadcastStageUpdate(worldPos, 0, 0f, 0f, 0L);
        }
    }

    public ChunkErosionMap getChunkMap(ChunkPos chunkPos) {
        if (state == null) return null;
        return state.getChunkMap(chunkPos);
    }

    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.immutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
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
                            .setValue(ErodedGrassBlock.STAGE, stage)
                            .setValue(ErodedGrassBlock.SNOWY, ErodedGrassBlock.snowyAt(world, pos)),
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
     * No neighbour or shape updates ({@link Block#UPDATE_CLIENTS} | {@link Block#UPDATE_KNOWN_SHAPE}):
     * they read the neighbouring blocks, and a neighbour in an unloaded chunk would be loaded
     * synchronously (or generated) by that read. The vanilla block takes the place of its eroded
     * form, so the neighbours stay valid without them.
     */
    public int convertChunkToVanilla(LevelChunk chunk) {
        if (world == null) return 0;
        LevelChunkSection[] sections = chunk.getSections();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        int converted = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> VANILLA_EQUIVALENTS.containsKey(s.getBlock()))) continue;
            int startY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState eroded = section.getBlockState(x, y, z);
                        Block vanilla = VANILLA_EQUIVALENTS.get(eroded.getBlock());
                        if (vanilla == null) continue;
                        mutable.set(startX + x, startY + y, startZ + z);
                        // Carry eroded grass's snowy flag to grass_block (no neighbour read, see above).
                        BlockState vanillaState = vanilla.defaultBlockState();
                        if (eroded.hasProperty(ErodedGrassBlock.SNOWY) && vanillaState.hasProperty(ErodedGrassBlock.SNOWY)) {
                            vanillaState = vanillaState.setValue(ErodedGrassBlock.SNOWY, eroded.getValue(ErodedGrassBlock.SNOWY));
                        }
                        world.setBlock(mutable, vanillaState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        removeEntry(mutable.immutable());
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
    public static void setHudEnabled(ServerPlayer player, boolean enabled) {
        TestLog.line(() -> "hud player=" + player.getName().getString() + " enabled=" + enabled);
        if (!enabled) {
            hudPlayers.remove(player.getUUID());
        } else if (hudPlayers.add(player.getUUID())) {
            get(player.level()).sendWatchedChunksToPlayer(player);
        }
    }

    /**
     * Sends SYNC_CHUNK for every chunk with erosion data that {@code player} is currently watching
     * (in this manager's world, which must be the player's world).
     * Iterates the player's {@code ChunkTrackingView} (bounded by view distance);
     * {@link PlayerLookup#tracking} excludes chunks still pending in the player's PlayerChunkSender,
     * which get their data from ChunkDataSenderMixin when they are actually sent.
     */
    private void sendWatchedChunksToPlayer(ServerPlayer player) {
        if (state == null) return;
        player.getChunkTrackingView().forEach(chunkPos -> {
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
     * Sends a single-block stage update to every HUD-enabled player watching the block's chunk
     * in this manager's world (players in other dimensions never receive it).
     */
    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (world == null || hudPlayers.isEmpty()) return;
        UpdateStagePayload payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
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
