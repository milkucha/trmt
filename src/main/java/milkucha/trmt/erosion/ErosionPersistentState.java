package milkucha.trmt.erosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the full erosion map to world/data/trmt_erosion.dat via Minecraft's SavedData API.
 * Attached to the overworld's DimensionDataStorage; automatically saved on world save.
 */
public class ErosionPersistentState extends SavedData {

    private static final String DATA_KEY = "trmt_erosion";
    // 26.3: SavedDataType now keys its storage by Identifier rather than a raw String, and
    // Identifier.of(namespace, path) was replaced by fromNamespaceAndPath.
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(milkucha.trmt.TRMT.MOD_ID, DATA_KEY);

    private final Map<ChunkPos, ChunkErosionMap> chunkMaps;

    public ErosionPersistentState() {
        this.chunkMaps = new HashMap<>();
    }

    private ErosionPersistentState(Map<ChunkPos, ChunkErosionMap> chunkMaps) {
        this.chunkMaps = chunkMaps;
    }

    // --- Codec-based (de)serialization ---

    private record EntryData(BlockPos pos, Block block, float count, float threshold, long lastTime, int stage) {
        static final Codec<EntryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(EntryData::pos),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(EntryData::block),
                Codec.FLOAT.fieldOf("count").forGetter(EntryData::count),
                Codec.FLOAT.fieldOf("threshold").forGetter(EntryData::threshold),
                Codec.LONG.fieldOf("lastTime").forGetter(EntryData::lastTime),
                Codec.INT.fieldOf("stage").forGetter(EntryData::stage)
        ).apply(instance, EntryData::new));
    }

    private record ChunkData(ChunkPos chunkPos, List<EntryData> entries) {
        static final Codec<ChunkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ChunkPos.CODEC.fieldOf("chunkPos").forGetter(ChunkData::chunkPos),
                EntryData.CODEC.listOf().fieldOf("entries").forGetter(ChunkData::entries)
        ).apply(instance, ChunkData::new));
    }

    public static final Codec<ErosionPersistentState> CODEC = ChunkData.CODEC.listOf().xmap(
            ErosionPersistentState::fromChunkDataList,
            ErosionPersistentState::toChunkDataList
    );

    public static final SavedDataType<ErosionPersistentState> TYPE =
            new SavedDataType<>(DATA_ID, ErosionPersistentState::new, CODEC, DataFixTypes.LEVEL);

    /** Retrieves or creates the persistent state attached to the overworld. */
    public static ErosionPersistentState getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static ErosionPersistentState fromChunkDataList(List<ChunkData> chunkDataList) {
        Map<ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();
        for (ChunkData chunkData : chunkDataList) {
            ChunkErosionMap chunkMap = new ChunkErosionMap();
            for (EntryData entry : chunkData.entries()) {
                chunkMap.putEntry(entry.pos(), new ErosionEntry(
                        entry.block(), entry.threshold(), entry.count(), entry.lastTime(), entry.stage()));
            }
            chunkMaps.put(chunkData.chunkPos(), chunkMap);
        }
        return new ErosionPersistentState(chunkMaps);
    }

    private List<ChunkData> toChunkDataList() {
        List<ChunkData> result = new java.util.ArrayList<>();
        for (Map.Entry<ChunkPos, ChunkErosionMap> chunkEntry : chunkMaps.entrySet()) {
            List<EntryData> entries = new java.util.ArrayList<>();
            for (Map.Entry<BlockPos, ErosionEntry> e : chunkEntry.getValue().getEntries().entrySet()) {
                ErosionEntry erosion = e.getValue();
                entries.add(new EntryData(e.getKey(), erosion.getTrackedBlock(), erosion.getWalkedOnCount(),
                        erosion.getThreshold(), erosion.getLastTouchedGameTime(), erosion.getErosionStage()));
            }
            result.add(new ChunkData(chunkEntry.getKey(), entries));
        }
        return result;
    }

    // --- Map access ---

    public ChunkErosionMap getChunkMap(ChunkPos pos) {
        return chunkMaps.get(pos);
    }

    public ChunkErosionMap computeChunkMap(ChunkPos pos) {
        return chunkMaps.computeIfAbsent(pos, k -> new ChunkErosionMap());
    }

    public void removeChunkMapIfEmpty(ChunkPos pos) {
        ChunkErosionMap map = chunkMaps.get(pos);
        if (map != null && map.isEmpty()) {
            chunkMaps.remove(pos);
        }
    }

    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        return Collections.unmodifiableMap(chunkMaps);
    }
}
