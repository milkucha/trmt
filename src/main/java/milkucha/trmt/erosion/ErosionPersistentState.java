package milkucha.trmt.erosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import milkucha.trmt.TRMT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

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
 * {@code SavedData} that re-wrote the whole map on every autosave — GH #68 / A2.
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
        return ChunkPos.pack(chunk.getRegionX(), chunk.getRegionZ());
    }

    // --- NBT (region files, and the 1.21.1-era legacy single file) ---
    // Layout: chunks:[{cx, cz, entries:[{x, y, z, block, count, threshold, lastTime, stage}]}]
    // Plain NBT, not a Codec, so the region format is identical on every branch.

    ListTag writeChunks(Iterable<ChunkPos> chunks) {
        ListTag chunkList = new ListTag();
        // Only a handful of distinct block types are tracked; resolve each id string once.
        Map<Block, String> blockIds = new IdentityHashMap<>();

        for (ChunkPos chunkPos : chunks) {
            ChunkErosionMap chunkMap = chunkMaps.get(chunkPos);
            if (chunkMap == null || chunkMap.isEmpty()) continue;

            ListTag entryList = new ListTag();
            for (Map.Entry<BlockPos, ErosionEntry> entry : chunkMap.getEntries().entrySet()) {
                BlockPos pos = entry.getKey();
                ErosionEntry erosion = entry.getValue();

                CompoundTag entryNbt = new CompoundTag();
                entryNbt.putInt("x", pos.getX());
                entryNbt.putInt("y", pos.getY());
                entryNbt.putInt("z", pos.getZ());
                entryNbt.putString("block", blockIds.computeIfAbsent(erosion.getTrackedBlock(),
                        b -> BuiltInRegistries.BLOCK.getKey(b).toString()));
                entryNbt.putFloat("count", erosion.getWalkedOnCount());
                entryNbt.putFloat("threshold", erosion.getThreshold());
                entryNbt.putLong("lastTime", erosion.getLastTouchedGameTime());
                entryNbt.putInt("stage", erosion.getErosionStage());
                entryList.add(entryNbt);
            }

            CompoundTag chunkNbt = new CompoundTag();
            chunkNbt.putInt("cx", chunkPos.x());
            chunkNbt.putInt("cz", chunkPos.z());
            chunkNbt.put("entries", entryList);
            chunkList.add(chunkNbt);
        }
        return chunkList;
    }

    /** Adds the chunks in {@code chunkList} to this map (does not mark anything dirty). */
    void readChunks(ListTag chunkList) {
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkNbt = chunkList.getCompoundOrEmpty(i);
            ChunkPos chunkPos = new ChunkPos(chunkNbt.getIntOr("cx", 0), chunkNbt.getIntOr("cz", 0));
            ListTag entryList = chunkNbt.getListOrEmpty("entries");
            if (entryList.isEmpty()) continue;

            ChunkErosionMap chunkMap = computeChunkMap(chunkPos);
            for (int j = 0; j < entryList.size(); j++) {
                CompoundTag entryNbt = entryList.getCompoundOrEmpty(j);
                BlockPos pos = new BlockPos(
                        entryNbt.getIntOr("x", 0),
                        entryNbt.getIntOr("y", 0),
                        entryNbt.getIntOr("z", 0)
                );
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(entryNbt.getStringOr("block", "")));
                float count     = entryNbt.getFloatOr("count", 0f);
                float threshold = entryNbt.getFloatOr("threshold", 0f);
                long  lastTime  = entryNbt.getLongOr("lastTime", 0L);
                int   stage     = entryNbt.getIntOr("stage", 0);

                chunkMap.putEntry(pos, new ErosionEntry(block, threshold, count, lastTime, stage));
            }
        }
    }

    // --- Legacy Codec layout (migration only) ---
    // Until A2 this branch stored erosion as a codec-based SavedData (id "trmt:erosion"; on 1.21.11 key
    // "trmt_erosion", same codec): data: {"cx,cz": {"x,y,z": {block, threshold, count, lastTime, stage}}}.
    // Kept only to read that file once during migration; nothing is written in this layout.

    private static final Codec<ErosionEntry> LEGACY_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(ErosionEntry::getTrackedBlock),
        Codec.FLOAT.fieldOf("threshold").forGetter(ErosionEntry::getThreshold),
        Codec.FLOAT.fieldOf("count").forGetter(ErosionEntry::getWalkedOnCount),
        Codec.LONG.fieldOf("lastTime").forGetter(ErosionEntry::getLastTouchedGameTime),
        Codec.INT.fieldOf("stage").forGetter(ErosionEntry::getErosionStage)
    ).apply(instance, (block, threshold, count, lastTime, stage) ->
            new ErosionEntry(block, threshold, count, lastTime, stage)));

    private static final Codec<BlockPos> LEGACY_STRING_BLOCK_POS = Codec.STRING.xmap(
        s -> { String[] p = s.split(","); return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); },
        pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()
    );

    private static final Codec<ChunkPos> LEGACY_STRING_CHUNK_POS = Codec.STRING.xmap(
        s -> { String[] p = s.split(","); return new ChunkPos(Integer.parseInt(p[0]), Integer.parseInt(p[1])); },
        pos -> pos.x() + "," + pos.z()
    );

    private static final Codec<Map<ChunkPos, Map<BlockPos, ErosionEntry>>> LEGACY_CODEC =
        Codec.unboundedMap(LEGACY_STRING_CHUNK_POS, Codec.unboundedMap(LEGACY_STRING_BLOCK_POS, LEGACY_ENTRY_CODEC));

    /**
     * Adds the chunks from a legacy codec-layout {@code data} tag to this map (does not mark anything dirty).
     * Like vanilla's SavedData loading, undecodable entries are logged and skipped.
     */
    void readLegacyCodecData(Tag data) {
        Map<ChunkPos, Map<BlockPos, ErosionEntry>> decoded = LEGACY_CODEC.parse(NbtOps.INSTANCE, data)
                .resultOrPartial(err -> TRMT.LOGGER.error("[TRMT] Error decoding legacy erosion data: {}", err))
                .orElse(Map.of());
        decoded.forEach((chunkPos, entries) -> {
            if (entries.isEmpty()) return;
            ChunkErosionMap chunkMap = computeChunkMap(chunkPos);
            entries.forEach(chunkMap::putEntry);
        });
    }
}
