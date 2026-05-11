package milkucha.trmt.erosion;

import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// ChunkPos must be encoded as a plain string so that Codec.unboundedMap can use it as an
// NBT compound key. ChunkPos.CODEC encodes to a long array which is NOT a string and causes
// "IllegalStateException: Not a string" on every world save.

/**
 * Persists the full erosion map to world/data/trmt_erosion.dat via Minecraft's PersistentState API.
 * Attached to the overworld's PersistentStateManager; automatically saved on world save.
 */
public class ErosionPersistentState extends PersistentState {

    private static final String DATA_KEY = "trmt_erosion";

    /** Encodes ChunkPos as the string "x,z" so it is a valid NBT compound key. */
    private static final Codec<ChunkPos> CHUNK_POS_STRING_CODEC = Codec.STRING.xmap(
            s -> { String[] p = s.split(","); return new ChunkPos(Integer.parseInt(p[0]), Integer.parseInt(p[1])); },
            cp -> cp.x + "," + cp.z
    );

    public static final Codec<ErosionPersistentState> CODEC = Codec.unboundedMap(CHUNK_POS_STRING_CODEC, ChunkErosionMap.CODEC)
            .xmap(ErosionPersistentState::new, state -> state.chunkMaps);

    public static final PersistentStateType<ErosionPersistentState> TYPE = new PersistentStateType<>(
            DATA_KEY,
            ErosionPersistentState::new,
            CODEC,
            null
    );

    private final Map<ChunkPos, ChunkErosionMap> chunkMaps;

    public ErosionPersistentState() {
        this.chunkMaps = new HashMap<>();
    }

    public ErosionPersistentState(Map<ChunkPos, ChunkErosionMap> chunkMaps) {
        this.chunkMaps = new HashMap<>(chunkMaps);
    }

    /** Retrieves or creates the persistent state attached to the overworld. */
    public static ErosionPersistentState getOrCreate(MinecraftServer server) {
        var world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return new ErosionPersistentState();
        }
        return world.getPersistentStateManager().getOrCreate(TYPE);
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
