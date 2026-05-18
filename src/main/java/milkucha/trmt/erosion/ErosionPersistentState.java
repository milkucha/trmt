package milkucha.trmt.erosion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists erosion data to world/data/trmt_erosion.dat (NBT layout unchanged from Fabric/Yarn era).
 */
public class ErosionPersistentState extends SavedData {

	private static final String DATA_KEY = "trmt_erosion";

	private final Map<net.minecraft.world.level.ChunkPos, ChunkErosionMap> chunkMaps;

	private static final Factory<ErosionPersistentState> FACTORY = new Factory<>(
		ErosionPersistentState::new,
		ErosionPersistentState::load,
		DataFixTypes.LEVEL
	);

	public ErosionPersistentState() {
		this.chunkMaps = new HashMap<>();
	}

	private ErosionPersistentState(Map<net.minecraft.world.level.ChunkPos, ChunkErosionMap> chunkMaps) {
		this.chunkMaps = chunkMaps;
	}

	public static ErosionPersistentState getOrCreate(MinecraftServer server) {
		net.minecraft.server.level.ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		if (overworld == null) {
			throw new IllegalStateException("TRMT: overworld not loaded");
		}
		return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_KEY);
	}

	public ChunkErosionMap getChunkMap(net.minecraft.world.level.ChunkPos pos) {
		return chunkMaps.get(pos);
	}

	public ChunkErosionMap computeChunkMap(net.minecraft.world.level.ChunkPos pos) {
		return chunkMaps.computeIfAbsent(pos, k -> new ChunkErosionMap());
	}

	public void removeChunkMapIfEmpty(net.minecraft.world.level.ChunkPos pos) {
		ChunkErosionMap map = chunkMaps.get(pos);
		if (map != null && map.isEmpty()) {
			chunkMaps.remove(pos);
		}
	}

	public Map<net.minecraft.world.level.ChunkPos, ChunkErosionMap> getAllChunkMaps() {
		return Collections.unmodifiableMap(chunkMaps);
	}

	private static ErosionPersistentState load(CompoundTag nbt, HolderLookup.Provider provider) {
		return fromNbt(nbt);
	}

	@Override
	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
		ListTag chunkList = new ListTag();

		for (Map.Entry<net.minecraft.world.level.ChunkPos, ChunkErosionMap> chunkEntry : chunkMaps.entrySet()) {
			net.minecraft.world.level.ChunkPos chunkPos = chunkEntry.getKey();
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
			chunkNbt.putInt("cx", chunkPos.x);
			chunkNbt.putInt("cz", chunkPos.z);
			chunkNbt.put("entries", entryList);
			chunkList.add(chunkNbt);
		}

		nbt.put("chunks", chunkList);
		return nbt;
	}

	private static ErosionPersistentState fromNbt(CompoundTag nbt) {
		Map<net.minecraft.world.level.ChunkPos, ChunkErosionMap> chunkMaps = new HashMap<>();

		ListTag chunkList = nbt.getList("chunks", Tag.TAG_COMPOUND);
		for (int i = 0; i < chunkList.size(); i++) {
			CompoundTag chunkNbt = chunkList.getCompound(i);
			net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(
				chunkNbt.getInt("cx"), chunkNbt.getInt("cz"));
			ChunkErosionMap chunkMap = new ChunkErosionMap();

			ListTag entryList = chunkNbt.getList("entries", Tag.TAG_COMPOUND);
			for (int j = 0; j < entryList.size(); j++) {
				CompoundTag entryNbt = entryList.getCompound(j);
				BlockPos pos = new BlockPos(
					entryNbt.getInt("x"),
					entryNbt.getInt("y"),
					entryNbt.getInt("z")
				);
				ResourceLocation rl = ResourceLocation.tryParse(entryNbt.getString("block"));
				Block block = rl == null
					? net.minecraft.world.level.block.Blocks.AIR
					: BuiltInRegistries.BLOCK.get(rl);
				if (block == null) {
					block = net.minecraft.world.level.block.Blocks.AIR;
				}
				float count = entryNbt.getFloat("count");
				float threshold = entryNbt.getFloat("threshold");
				long lastTime = entryNbt.getLong("lastTime");
				int stage = entryNbt.getInt("stage");

				chunkMap.putEntry(pos, new ErosionEntry(block, threshold, count, lastTime, stage));
			}

			chunkMaps.put(chunkPos, chunkMap);
		}

		return new ErosionPersistentState(chunkMaps);
	}
}
