package milkucha.trmt.erosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErosionPersistentState extends PersistentState {

    private static final String DATA_KEY = "trmt_erosion";

    private final Map<ChunkPos, ChunkErosionMap> chunkMaps;

    public ErosionPersistentState() {
        this.chunkMaps = new HashMap<>();
    }

    private ErosionPersistentState(Map<ChunkPos, ChunkErosionMap> chunkMaps) {
        this.chunkMaps = chunkMaps;
    }

    public static ErosionPersistentState getOrCreate(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD)
                .getPersistentStateManager()
                .getOrCreate(TYPE);
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

    // --- Codec-based serialization ---

    private record StoredEntry(BlockPos pos, Identifier block, float threshold, float count, long lastTime, int stage) {
        static final Codec<StoredEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(StoredEntry::pos),
                Identifier.CODEC.fieldOf("block").forGetter(StoredEntry::block),
                Codec.FLOAT.fieldOf("threshold").forGetter(StoredEntry::threshold),
                Codec.FLOAT.fieldOf("count").forGetter(StoredEntry::count),
                Codec.LONG.fieldOf("lastTime").forGetter(StoredEntry::lastTime),
                Codec.INT.fieldOf("stage").forGetter(StoredEntry::stage)
        ).apply(i, StoredEntry::new));
    }

    private record StoredChunk(ChunkPos pos, List<StoredEntry> entries) {
        static final Codec<StoredChunk> CODEC = RecordCodecBuilder.create(i -> i.group(
                ChunkPos.CODEC.fieldOf("pos").forGetter(StoredChunk::pos),
                StoredEntry.CODEC.listOf().fieldOf("entries").forGetter(StoredChunk::entries)
        ).apply(i, StoredChunk::new));
    }

    public static final Codec<ErosionPersistentState> CODEC = StoredChunk.CODEC.listOf()
            .xmap(ErosionPersistentState::fromStored, ErosionPersistentState::toStored);

    public static final PersistentStateType<ErosionPersistentState> TYPE = new PersistentStateType<>(
            DATA_KEY,
            ErosionPersistentState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private static ErosionPersistentState fromStored(List<StoredChunk> stored) {
        Map<ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();
        for (StoredChunk chunk : stored) {
            ChunkErosionMap chunkMap = new ChunkErosionMap();
            for (StoredEntry entry : chunk.entries()) {
                Block block = Registries.BLOCK.get(entry.block());
                chunkMap.putEntry(entry.pos(),
                        new ErosionEntry(block, entry.threshold(), entry.count(), entry.lastTime(), entry.stage()));
            }
            chunkMaps.put(chunk.pos(), chunkMap);
        }
        return new ErosionPersistentState(chunkMaps);
    }

    private static List<StoredChunk> toStored(ErosionPersistentState state) {
        return state.chunkMaps.entrySet().stream()
                .map(chunkEntry -> {
                    List<StoredEntry> entries = chunkEntry.getValue().getEntries().entrySet().stream()
                            .map(e -> new StoredEntry(
                                    e.getKey(),
                                    Registries.BLOCK.getId(e.getValue().getTrackedBlock()),
                                    e.getValue().getThreshold(),
                                    e.getValue().getWalkedOnCount(),
                                    e.getValue().getLastTouchedGameTime(),
                                    e.getValue().getErosionStage()
                            ))
                            .toList();
                    return new StoredChunk(chunkEntry.getKey(), entries);
                })
                .toList();
    }
}
