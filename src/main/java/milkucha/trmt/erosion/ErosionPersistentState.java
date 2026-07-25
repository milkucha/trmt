package milkucha.trmt.erosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ErosionPersistentState extends SavedData {

    private static final Identifier DATA_KEY = Identifier.fromNamespaceAndPath("trmt", "erosion");

    private final Map<ChunkPos, ChunkErosionMap> chunkMaps;

    public ErosionPersistentState() {
        this.chunkMaps = new HashMap<>();
    }

    private ErosionPersistentState(Map<ChunkPos, ChunkErosionMap> chunkMaps) {
        this.chunkMaps = new HashMap<>(chunkMaps);
    }

    // --- Codec-based serialization (1.21.11+) ---

    private static final Codec<ErosionEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(ErosionEntry::getTrackedBlock),
        Codec.FLOAT.fieldOf("threshold").forGetter(ErosionEntry::getThreshold),
        Codec.FLOAT.fieldOf("count").forGetter(ErosionEntry::getWalkedOnCount),
        Codec.LONG.fieldOf("lastTime").forGetter(ErosionEntry::getLastTouchedGameTime),
        Codec.INT.fieldOf("stage").forGetter(ErosionEntry::getErosionStage)
    ).apply(instance, (block, threshold, count, lastTime, stage) ->
            new ErosionEntry(block, threshold, count, lastTime, stage)));

    // BlockPos and ChunkPos encoded as "x,y,z" / "x,z" strings for use as map keys.
    private static final Codec<BlockPos> STRING_BLOCK_POS = Codec.STRING.xmap(
        s -> { String[] p = s.split(","); return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); },
        pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()
    );

    private static final Codec<ChunkPos> STRING_CHUNK_POS = Codec.STRING.xmap(
        s -> { String[] p = s.split(","); return new ChunkPos(Integer.parseInt(p[0]), Integer.parseInt(p[1])); },
        pos -> pos.x() + "," + pos.z()
    );

    private static final Codec<ChunkErosionMap> CHUNK_MAP_CODEC =
        Codec.unboundedMap(STRING_BLOCK_POS, ENTRY_CODEC).xmap(
            entries -> {
                ChunkErosionMap m = new ChunkErosionMap();
                entries.forEach((pos, entry) -> m.putEntry(pos, entry));
                return m;
            },
            ChunkErosionMap::getEntries
        );

    static final Codec<ErosionPersistentState> CODEC =
        Codec.unboundedMap(STRING_CHUNK_POS, CHUNK_MAP_CODEC).xmap(
            ErosionPersistentState::new,
            state -> state.chunkMaps
        );

    private static final SavedDataType<ErosionPersistentState> TYPE =
        new SavedDataType<>(DATA_KEY, ErosionPersistentState::new, CODEC, DataFixTypes.SAVED_DATA_SCOREBOARD);

    public static ErosionPersistentState getOrCreate(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD)
                .getDataStorage()
                .computeIfAbsent(TYPE);
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
