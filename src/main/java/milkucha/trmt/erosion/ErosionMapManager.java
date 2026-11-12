package milkucha.trmt.erosion;

import com.mojang.logging.LogUtils;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.TRMTNetwork;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-dimension erosion data for the current world session: one manager per loaded server level
 * (B1 / GH #69 - the same x/z in two dimensions are separate blocks). Get it with {@link #get(Level)}
 * for the level the step / tick / interaction happened in.
 * Holds the map in {@link ErosionPersistentState}; {@link ErosionRegionStorage} persists it per region.
 * Sends erosion data (debug HUD only) to HUD-enabled players watching each chunk via the Forge SimpleChannel.
 */
public class ErosionMapManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** One manager per loaded server level, created when that level loads. */
    private static final Map<ResourceKey<Level>, ErosionMapManager> INSTANCES = new HashMap<>();
    /** Returned for client levels and for levels whose data is not loaded; every method is a no-op on it. */
    private static final ErosionMapManager INACTIVE = new ErosionMapManager(null);
    /** Shared by all dimensions: one {@code data/trmt/erosion/} folder, index and legacy file per save. */
    private static ErosionRegionStorage storage;

    /** Players whose client has the erosion debug HUD on. Only they receive erosion sync packets. */
    private static final Set<UUID> hudPlayers = new HashSet<>();

    /** The level this manager's data belongs to; null only for {@link #INACTIVE}. */
    private final ServerLevel world;
    /** Null only for {@link #INACTIVE}. */
    private final ErosionPersistentState state;

    private ErosionMapManager(ServerLevel world) {
        this.world = world;
        this.state = world != null ? new ErosionPersistentState() : null;
    }

    /** Returns the erosion data of {@code world}'s dimension (an inert manager on the client side). */
    public static ErosionMapManager get(Level world) {
        if (world == null || world.isClientSide) return INACTIVE;
        return INSTANCES.getOrDefault(world.dimension(), INACTIVE);
    }

    /**
     * Called when a server level is loaded (LevelEvent.Load inside createLevels, before the tick clock starts)
     * to load that dimension's erosion region files. The overworld load also migrates the legacy file.
     */
    public static void loadState(ServerLevel world) {
        if (storage == null) storage = ErosionRegionStorage.forServer(world.getServer());
        ErosionMapManager manager = new ErosionMapManager(world);
        storage.load(manager.state, world.dimension());
        INSTANCES.put(world.dimension(), manager);
    }

    /**
     * Called whenever vanilla saves a level (LevelEvent.Save: autosave, /save-all, server stop;
     * not fired while saving is disabled with /save-off). Writes only that dimension's regions
     * changed since its last save.
     */
    public static void onWorldSave(ServerLevel world) {
        ErosionMapManager manager = INSTANCES.get(world.dimension());
        if (manager == null || storage == null) return;
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
                    TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
                            .setValue(ErodedGrassBlock.FACING, facing)
                            .setValue(ErodedGrassBlock.STAGE, stage),
                    Block.UPDATE_ALL);
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), currentTime);
            migrated++;
        }

        if (migrated > 0) {
            LOGGER.info("[TRMT] Migrated {} eroded grass entries to eroded_grass_block.", migrated);
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

    private static volatile Map<Block, Block> vanillaEquivalents;

    /**
     * Every TRMT block and the vanilla block {@code /trmt convert-to-vanilla} replaces it with.
     * A new TRMT block must be added here, or the command leaves it in the world.
     * Built on first use: the TRMT blocks are RegistryObjects, resolvable only after registration.
     */
    public static Map<Block, Block> vanillaEquivalents() {
        Map<Block, Block> map = vanillaEquivalents;
        if (map == null) {
            map = Map.of(
                    TRMTBlocks.ERODED_GRASS_BLOCK.get(), Blocks.GRASS_BLOCK,
                    TRMTBlocks.ERODED_DIRT.get(),        Blocks.DIRT,
                    TRMTBlocks.ERODED_COARSE_DIRT.get(), Blocks.COARSE_DIRT,
                    TRMTBlocks.ERODED_SAND.get(),        Blocks.SAND);
            vanillaEquivalents = map;
        }
        return map;
    }

    /**
     * Replaces every TRMT block in {@code chunk} (a loaded chunk of this manager's level) with its
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
        Map<Block, Block> equivalents = vanillaEquivalents();
        LevelChunkSection[] sections = chunk.getSections();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        int converted = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> equivalents.containsKey(s.getBlock()))) continue;
            int startY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block vanilla = equivalents.get(section.getBlockState(x, y, z).getBlock());
                        if (vanilla == null) continue;
                        mutable.set(startX + x, startY + y, startZ + z);
                        world.setBlock(mutable, vanilla.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
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
     * Records whether {@code player}'s debug HUD is on (client sends hud_state; logout passes false).
     * On enable, sends the erosion data for the chunks the player is already watching.
     */
    public static void setHudEnabled(ServerPlayer player, boolean enabled) {
        TestLog.line(() -> "hud player=" + player.getName().getString() + " enabled=" + enabled);
        if (!enabled) {
            hudPlayers.remove(player.getUUID());
        } else if (hudPlayers.add(player.getUUID())) {
            get(player.serverLevel()).sendWatchedChunksToPlayer(player);
        }
    }

    /**
     * Sends sync_chunk for every chunk with erosion data that {@code player} is currently watching
     * (in this manager's level, which must be the player's level).
     * Bounded by the server view distance; chunks without data send nothing.
     */
    private void sendWatchedChunksToPlayer(ServerPlayer player) {
        if (state == null) return;
        int radius = world.getServer().getPlayerList().getViewDistance() + 1;
        int centerX = player.getLastSectionPos().x();
        int centerZ = player.getLastSectionPos().z();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerX + dx, centerZ + dz);
                if (state.getChunkMap(chunkPos) == null) continue;
                if (world.getChunkSource().chunkMap.getPlayers(chunkPos, false).contains(player)) {
                    sendChunkSyncToPlayer(player, chunkPos);
                }
            }
        }
    }

    /**
     * Sends the erosion data for one chunk to a HUD-enabled player that has just been sent that chunk.
     * Called from ChunkWatchEvent.Watch, which Forge 47 posts at the end of ChunkMap.playerLoadedChunk,
     * after the ClientboundLevelChunkWithLightPacket (so it is paced by vanilla's chunk sending).
     * Chunks without data send nothing: the client drops its cached entries for a chunk when that
     * chunk loads or unloads on its side.
     */
    public void sendChunkSyncToPlayer(ServerPlayer player, ChunkPos chunkPos) {
        if (state == null || !hudPlayers.contains(player.getUUID())) return;
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        Map<BlockPos, ErosionEntry> entries = map.getEntries();
        if (entries.isEmpty()) return;

        List<TRMTNetwork.SyncChunkMessage.Entry> payloadEntries = new ArrayList<>(entries.size());
        for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
            payloadEntries.add(new TRMTNetwork.SyncChunkMessage.Entry(
                    e.getKey(),
                    e.getValue().getErosionStage(),
                    e.getValue().getWalkedOnCount(),
                    e.getValue().getThreshold(),
                    e.getValue().getLastTouchedGameTime()
            ));
        }
        TRMTNetwork.sendSyncChunk(player, chunkPos.x, chunkPos.z, payloadEntries);
        TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + player.serverLevel().dimension().location()
                + " chunk=" + chunkPos.x + "," + chunkPos.z + " entries=" + entries.size());
        if (TestLog.enabled()) {
            TestLog.ChunkSummary summary = new TestLog.ChunkSummary();
            for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
                ErosionEntry v = e.getValue();
                summary.add(e.getKey(), v.getErosionStage(), v.getWalkedOnCount(), v.getThreshold(), v.getLastTouchedGameTime());
            }
            summary.log("server", "player=" + player.getName().getString() + " dim=" + player.serverLevel().dimension().location()
                    + " chunk=" + chunkPos.x + "," + chunkPos.z);
        }
    }

    /**
     * Sends a single-block stage update to every HUD-enabled player watching the block's chunk
     * in this manager's level (players in other dimensions never receive it).
     */
    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (world == null || hudPlayers.isEmpty()) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        for (ServerPlayer player : world.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            if (!hudPlayers.contains(player.getUUID())) continue;
            TRMTNetwork.sendUpdateStage(player, pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
            TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + world.dimension().location()
                    + " chunk=" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4) + " entries=1");
            TestLog.line(() -> "server-entry player=" + player.getName().getString() + " "
                    + TestLog.entry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime));
        }
    }
}
