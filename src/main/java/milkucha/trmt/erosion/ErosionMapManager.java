package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.TestLog;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;
import java.util.List;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension erosion data for the current world session: one manager per loaded server world
 * (B1 / GH #69 — the same x/z in two dimensions are separate blocks). Get it with {@link #get(World)}
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
        manager.pruneExpired(new ArrayList<>(manager.state.getAllChunkMaps().keySet()));
    }

    /** Called when vanilla loads a chunk into the world: drops that chunk's expired entries. */
    public static void onChunkLoad(ServerWorld world, ChunkPos chunkPos) {
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
        long cutoff = world.getTime() - (long) (days * TICKS_PER_DAY);
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
            TRMT.LOGGER.debug("[TRMT] {}: dropped {} expired erosion entries", world.getRegistryKey().getValue(), removed);
        }
    }

    /**
     * Called whenever vanilla saves a world's persistent data (autosave, /save-all, server stop;
     * skipped while saving is disabled with /save-off). Writes only that dimension's regions
     * changed since its last save.
     */
    public static void onWorldSave(ServerWorld world) {
        ErosionMapManager manager = INSTANCES.get(world.getRegistryKey());
        if (manager == null || storage == null) return;
        // Regions about to be written: drop their expired entries first.
        List<ChunkPos> dirtyChunks = new ArrayList<>();
        for (long region : manager.state.getDirtyRegions()) {
            dirtyChunks.addAll(manager.state.getRegionChunks(region));
        }
        manager.pruneExpired(dirtyChunks);
        storage.save(manager.state, world.getRegistryKey());
    }

    /** Called on server stop to release all in-memory state. */
    public static void reset() {
        INSTANCES.clear();
        storage = null;
        hudPlayers.clear();
    }

    // --- Erosion logic ---

    /**
     * Records one step at worldPos for the given block type.
     * Creates a ChunkErosionMap for the chunk if needed.
     */
    public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        map.recordStep(worldPos, block, amount, currentGameTime);
        state.markDirty(chunkPos);
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
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(pos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;
        int stage = entry.getErosionStage();
        if (stage == 0 && block == Blocks.GRASS_BLOCK) return; // pristine grass — no HUD entry yet
        if (stage == 0) stage = 1; // sentinel for non-grass tracked blocks
        broadcastStageUpdate(pos, stage, entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
    }

    /**
     * Removes the erosion entry for worldPos (called after a block transformation).
     * Drops the ChunkErosionMap entirely if it becomes empty.
     * Broadcasts stage = 0 so clients clear the entry.
     */
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

    /**
     * Called when a grass erosion stage advances. Broadcasts the new stage to all clients
     * so they can update their local cache and trigger a chunk re-render.
     */
    public void markForRerender(BlockPos pos) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(pos));
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
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(worldPos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(worldPos);
        if (entry == null) return;
        entry.revertGrassStage(BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK), currentGameTime);
        state.markDirty(new ChunkPos(worldPos));
    }

    /**
     * Writes a grass erosion entry at worldPos with the given stage and a cooldown timestamp.
     * Used when eroded_dirt reverts to grass_block: the block becomes grass again visually
     * but should still display as eroded stage {@code stage} until further de-erosion occurs.
     * Call {@link #markForRerender} afterwards to broadcast the stage to clients.
     */
    public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
        map.putEntry(worldPos.toImmutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.markDirty(chunkPos);
    }

    /**
     * Writes a fresh cooldown entry at worldPos for {@code block}.
     * Sets walkedOnCount = 0 and lastTouchedGameTime = currentGameTime so the block cannot
     * immediately de-erode again after a revert.
     */
    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.toImmutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.markDirty(chunkPos);
    }

    /**
     * One-time migration: converts legacy ErosionEntry records that used to drive the
     * client-side proxy model (trackedBlock=GRASS_BLOCK, erosionStage 1–5) into real
     * ERODED_GRASS_BLOCK placements with the equivalent STAGE and FACING.
     * Safe to call on every server start — entries that have already been migrated won't
     * match the criteria and are silently skipped. Runs on every dimension's data.
     */
    public static void migrateGrassEntries(MinecraftServer server) {
        for (ErosionMapManager manager : INSTANCES.values()) {
            manager.migrateGrassEntries();
        }
    }

    private void migrateGrassEntries() {
        if (state == null) return;

        // Collect candidate positions first (getEntries returns an unmodifiable view).
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

            int stage = entry.getErosionStage() - 1; // old stages 1–5 → new STAGE 0–4
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

    /** Returns all chunk positions that currently have erosion entries. */
    public Set<ChunkPos> getErodedChunkPositions() {
        if (state == null) return Collections.emptySet();
        return state.getAllChunkMaps().keySet();
    }

    /** Returns an unmodifiable view of all chunk maps. Used by the debug HUD. */
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
            get(player.getWorld()).sendWatchedChunksToPlayer(player);
        }
    }

    /**
     * Sends SYNC_CHUNK for every chunk with erosion data that {@code player} is currently watching
     * (in this manager's world, which must be the player's world).
     * Bounded by the server view distance; chunks without data send nothing.
     */
    private void sendWatchedChunksToPlayer(ServerPlayerEntity player) {
        if (state == null) return;
        int radius = world.getServer().getPlayerManager().getViewDistance() + 1;
        int centerX = player.getWatchedSection().getSectionX();
        int centerZ = player.getWatchedSection().getSectionZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerX + dx, centerZ + dz);
                if (state.getChunkMap(chunkPos) == null) continue;
                if (PlayerLookup.tracking(world, chunkPos).contains(player)) {
                    sendChunkSyncToPlayer(player, chunkPos);
                }
            }
        }
    }

    /**
     * Sends the erosion data for one chunk to a HUD-enabled player that has just started watching it.
     * Call it on the manager of the chunk's world.
     * Called right after vanilla sends the chunk itself (see ThreadedAnvilChunkStorageMixin),
     * so it is paced by vanilla's chunk sending. Chunks without data send nothing: the client
     * drops its cached entries for a chunk when that chunk loads or unloads on its side.
     */
    public void sendChunkSyncToPlayer(ServerPlayerEntity player, ChunkPos chunkPos) {
        if (state == null || !hudPlayers.contains(player.getUuid())) return;
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        Map<BlockPos, ErosionEntry> entries = map.getEntries();
        if (entries.isEmpty()) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(chunkPos.x);
        buf.writeInt(chunkPos.z);
        buf.writeInt(entries.size());
        for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
            buf.writeBlockPos(e.getKey());
            buf.writeInt(e.getValue().getErosionStage());
            buf.writeFloat(e.getValue().getWalkedOnCount());
            buf.writeFloat(e.getValue().getThreshold());
            buf.writeLong(e.getValue().getLastTouchedGameTime());
        }
        ServerPlayNetworking.send(player, TRMTPackets.SYNC_CHUNK, buf);
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
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            if (!hudPlayers.contains(player.getUuid())) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeInt(stage);
            buf.writeFloat(walkedOnCount);
            buf.writeFloat(threshold);
            buf.writeLong(lastTouchedGameTime);
            ServerPlayNetworking.send(player, TRMTPackets.UPDATE_STAGE, buf);
            TestLog.line(() -> "server-sync player=" + player.getName().getString() + " dim=" + world.getRegistryKey().getValue()
                    + " chunk=" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4) + " entries=1");
            TestLog.line(() -> "server-entry player=" + player.getName().getString() + " "
                    + TestLog.entry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime));
        }
    }
}
