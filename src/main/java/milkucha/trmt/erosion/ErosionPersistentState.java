package milkucha.trmt.erosion;

import net.minecraft.core.BlockPos;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ErosionPersistentState extends SavedData {

    private static final String DATA_KEY = "trmt_erosion";
    private static final Codec<ErosionPersistentState> CODEC =
            CompoundTag.CODEC.xmap(ErosionPersistentState::fromNbt, ErosionPersistentState::toNbt);
    private static final SavedDataType<ErosionPersistentState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("trmt", DATA_KEY),
            ErosionPersistentState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<ChunkPos, ChunkErosionMap> chunkMaps;

    public ErosionPersistentState() {
        this.chunkMaps = new HashMap<>();
    }

    private ErosionPersistentState(Map<ChunkPos, ChunkErosionMap> chunkMaps) {
        this.chunkMaps = chunkMaps;
    }

    public static ErosionPersistentState getOrCreate(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(TYPE);
    }

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

    private CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        ListTag chunkList = new ListTag();

        for (Map.Entry<ChunkPos, ChunkErosionMap> chunkEntry : chunkMaps.entrySet()) {
            ChunkPos chunkPos = chunkEntry.getKey();
            ChunkErosionMap chunkMap = chunkEntry.getValue();

            ListTag entryList = new ListTag();
            for (Map.Entry<BlockPos, ErosionEntry> entry : chunkMap.getEntries().entrySet()) {
                BlockPos pos = entry.getKey();
                ErosionEntry erosion = entry.getValue();

                CompoundTag entryNbt = new CompoundTag();
                entryNbt.putInt("x", pos.getX());
                entryNbt.putInt("y", pos.getY());
                entryNbt.putInt("z", pos.getZ());
                entryNbt.putString("block", BuiltInRegistries.BLOCK.getKey(erosion.getTrackedBlock()).toString());
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

        nbt.put("chunks", chunkList);
        return nbt;
    }

    private static ErosionPersistentState fromNbt(CompoundTag nbt) {
        Map<ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();

        ListTag chunkList = nbt.getListOrEmpty("chunks");
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkNbt = chunkList.getCompoundOrEmpty(i);
            ChunkPos chunkPos = new ChunkPos(chunkNbt.getIntOr("cx", 0), chunkNbt.getIntOr("cz", 0));
            ChunkErosionMap chunkMap = new ChunkErosionMap();

            ListTag entryList = chunkNbt.getListOrEmpty("entries");
            for (int j = 0; j < entryList.size(); j++) {
                CompoundTag entryNbt = entryList.getCompoundOrEmpty(j);
                BlockPos pos = new BlockPos(
                        entryNbt.getIntOr("x", 0),
                        entryNbt.getIntOr("y", 0),
                        entryNbt.getIntOr("z", 0)
                );
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(entryNbt.getStringOr("block", "minecraft:air")));
                float count = entryNbt.getFloatOr("count", 0.0f);
                float threshold = entryNbt.getFloatOr("threshold", 0.0f);
                long lastTime = entryNbt.getLongOr("lastTime", 0L);
                int stage = entryNbt.getIntOr("stage", 0);

                chunkMap.putEntry(pos, new ErosionEntry(block, threshold, count, lastTime, stage));
            }

            chunkMaps.put(chunkPos, chunkMap);
        }

        return new ErosionPersistentState(chunkMaps);
    }
}
