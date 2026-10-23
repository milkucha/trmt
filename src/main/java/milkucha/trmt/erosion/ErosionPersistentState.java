package milkucha.trmt.erosion;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-memory erosion map: sparse {@code Map<ChunkPos, ChunkErosionMap>} plus the set of
 * regions (32x32 chunks) changed since the last save.
 * <p>
 * Persistence is done by {@link ErosionRegionStorage}, one file per region, so a save only
 * writes the regions in {@link #getDirtyRegions()}. (This class used to be a vanilla
 * {@code PersistentState} that re-wrote the whole map on every autosave — GH #68 / A2.
 * The name is kept to keep ports readable.)
 */
public class ErosionPersistentState {

    private final Map<ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();
    /** Region key -> chunks of that region present in {@link #chunkMaps}. */
    private final Long2ObjectMap<Set<ChunkPos>> regionChunks = new Long2ObjectOpenHashMap<>();
    private final LongSet dirtyRegions = new LongOpenHashSet();

    // --- Map access ---

    public ChunkErosionMap getChunkMap(ChunkPos pos) {
        return chunkMaps.get(pos);
    }

    public ChunkErosionMap computeChunkMap(ChunkPos pos) {
        ChunkErosionMap map = chunkMaps.get(pos);
        if (map == null) {
            map = new ChunkErosionMap();
            chunkMaps.put(pos, map);
            regionChunks.computeIfAbsent(regionKey(pos), k -> new HashSet<>()).add(pos);
        }
        return map;
    }

    public void removeChunkMapIfEmpty(ChunkPos pos) {
        ChunkErosionMap map = chunkMaps.get(pos);
        if (map != null && map.isEmpty()) {
            chunkMaps.remove(pos);
            long region = regionKey(pos);
            Set<ChunkPos> chunks = regionChunks.get(region);
            if (chunks != null) {
                chunks.remove(pos);
                if (chunks.isEmpty()) regionChunks.remove(region);
            }
        }
    }

    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        return Collections.unmodifiableMap(chunkMaps);
    }

    // --- Dirty tracking (per region) ---

    /** Marks the region containing {@code chunk} as changed; it is written on the next save. */
    public void markDirty(ChunkPos chunk) {
        dirtyRegions.add(regionKey(chunk));
    }

    /** Marks every region that has data as changed (used by the legacy migration). */
    void markAllDirty() {
        dirtyRegions.addAll(regionChunks.keySet());
    }

    LongSet getDirtyRegions() {
        return dirtyRegions;
    }

    void clearDirty(long region) {
        dirtyRegions.remove(region);
    }

    /** Chunks with data in the given region; empty if none. */
    Set<ChunkPos> getRegionChunks(long region) {
        Set<ChunkPos> chunks = regionChunks.get(region);
        return chunks != null ? chunks : Collections.emptySet();
    }

    static long regionKey(ChunkPos chunk) {
        return ChunkPos.toLong(chunk.getRegionX(), chunk.getRegionZ());
    }

    // --- NBT (shared by region files and the legacy single file) ---
    // Layout: chunks:[{cx, cz, entries:[{x, y, z, block, count, threshold, lastTime, stage}]}]

    NbtList writeChunks(Iterable<ChunkPos> chunks) {
        NbtList chunkList = new NbtList();
        // Only a handful of distinct block types are tracked; resolve each id string once.
        Map<Block, String> blockIds = new IdentityHashMap<>();

        for (ChunkPos chunkPos : chunks) {
            ChunkErosionMap chunkMap = chunkMaps.get(chunkPos);
            if (chunkMap == null || chunkMap.isEmpty()) continue;

            NbtList entryList = new NbtList();
            for (Map.Entry<BlockPos, ErosionEntry> entry : chunkMap.getEntries().entrySet()) {
                BlockPos pos = entry.getKey();
                ErosionEntry erosion = entry.getValue();

                NbtCompound entryNbt = new NbtCompound();
                entryNbt.putInt("x", pos.getX());
                entryNbt.putInt("y", pos.getY());
                entryNbt.putInt("z", pos.getZ());
                entryNbt.putString("block", blockIds.computeIfAbsent(erosion.getTrackedBlock(),
                        b -> Registries.BLOCK.getId(b).toString()));
                entryNbt.putFloat("count", erosion.getWalkedOnCount());
                entryNbt.putFloat("threshold", erosion.getThreshold());
                entryNbt.putLong("lastTime", erosion.getLastTouchedGameTime());
                entryNbt.putInt("stage", erosion.getErosionStage());
                entryList.add(entryNbt);
            }

            NbtCompound chunkNbt = new NbtCompound();
            chunkNbt.putInt("cx", chunkPos.x);
            chunkNbt.putInt("cz", chunkPos.z);
            chunkNbt.put("entries", entryList);
            chunkList.add(chunkNbt);
        }
        return chunkList;
    }

    /** Adds the chunks in {@code chunkList} to this map (does not mark anything dirty). */
    void readChunks(NbtList chunkList) {
        for (int i = 0; i < chunkList.size(); i++) {
            NbtCompound chunkNbt = chunkList.getCompound(i);
            ChunkPos chunkPos = new ChunkPos(chunkNbt.getInt("cx"), chunkNbt.getInt("cz"));
            NbtList entryList = chunkNbt.getList("entries", NbtElement.COMPOUND_TYPE);
            if (entryList.isEmpty()) continue;

            ChunkErosionMap chunkMap = computeChunkMap(chunkPos);
            for (int j = 0; j < entryList.size(); j++) {
                NbtCompound entryNbt = entryList.getCompound(j);
                BlockPos pos = new BlockPos(
                        entryNbt.getInt("x"),
                        entryNbt.getInt("y"),
                        entryNbt.getInt("z")
                );
                Block block = Registries.BLOCK.get(Identifier.of(entryNbt.getString("block")));
                float count     = entryNbt.getFloat("count");
                float threshold = entryNbt.getFloat("threshold");
                long  lastTime  = entryNbt.getLong("lastTime");
                int   stage     = entryNbt.getInt("stage");

                chunkMap.putEntry(pos, new ErosionEntry(block, threshold, count, lastTime, stage));
            }
        }
    }
}
