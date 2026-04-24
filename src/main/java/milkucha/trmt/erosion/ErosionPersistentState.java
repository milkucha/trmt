package milkucha.trmt.erosion;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
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
                .getOrCreate(new PersistentState.Type<>(
                        ErosionPersistentState::new,
                        ErosionPersistentState::fromNbt,
                        null), DATA_KEY);
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

    // --- NBT serialization ---

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList chunkList = new NbtList();

        for (Map.Entry<ChunkPos, ChunkErosionMap> chunkEntry : chunkMaps.entrySet()) {
            ChunkPos chunkPos = chunkEntry.getKey();
            ChunkErosionMap chunkMap = chunkEntry.getValue();

            NbtList entryList = new NbtList();
            for (Map.Entry<BlockPos, ErosionEntry> entry : chunkMap.getEntries().entrySet()) {
                BlockPos pos = entry.getKey();
                ErosionEntry erosion = entry.getValue();

                NbtCompound entryNbt = new NbtCompound();
                entryNbt.putInt("x", pos.getX());
                entryNbt.putInt("y", pos.getY());
                entryNbt.putInt("z", pos.getZ());
                entryNbt.putString("block", Registries.BLOCK.getId(erosion.getTrackedBlock()).toString());
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

        nbt.put("chunks", chunkList);
        return nbt;
    }

    private static ErosionPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        Map<ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();

        NbtList chunkList = nbt.getList("chunks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < chunkList.size(); i++) {
            NbtCompound chunkNbt = chunkList.getCompound(i);
            ChunkPos chunkPos = new ChunkPos(chunkNbt.getInt("cx"), chunkNbt.getInt("cz"));
            ChunkErosionMap chunkMap = new ChunkErosionMap();

            NbtList entryList = chunkNbt.getList("entries", NbtElement.COMPOUND_TYPE);
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

            chunkMaps.put(chunkPos, chunkMap);
        }

        return new ErosionPersistentState(chunkMaps);
    }
}
