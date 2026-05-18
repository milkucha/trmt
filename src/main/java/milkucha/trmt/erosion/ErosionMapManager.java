package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTFlags;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side erosion maps + payload sync to clients.
 */
public class ErosionMapManager {

	private static ErosionMapManager INSTANCE;

	private ErosionPersistentState state;
	private MinecraftServer server;

	private ErosionMapManager() {}

	public static ErosionMapManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ErosionMapManager();
		}
		return INSTANCE;
	}

	public void loadState(MinecraftServer server) {
		this.server = server;
		this.state = ErosionPersistentState.getOrCreate(server);
	}

	public static void reset() {
		INSTANCE = null;
	}

	public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
		if (state == null) return;
		net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(worldPos);
		ChunkErosionMap map = state.computeChunkMap(chunkPos);
		map.recordStep(worldPos, block, amount, currentGameTime);
		setDirty();
	}

	public void broadcastEntryUpdate(BlockPos pos, Block block) {
		if (state == null) return;
		ChunkErosionMap map = state.getChunkMap(new net.minecraft.world.level.ChunkPos(pos));
		if (map == null) return;
		ErosionEntry entry = map.getEntry(pos);
		if (entry == null) return;
		int stage = entry.getErosionStage();
		if (stage == 0 && block == Blocks.GRASS_BLOCK) return;
		if (stage == 0) stage = 1;
		broadcastStageUpdate(pos, stage, entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
	}

	public void removeEntry(BlockPos worldPos) {
		if (state == null) return;
		net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(worldPos);
		ChunkErosionMap map = state.getChunkMap(chunkPos);
		if (map == null) return;
		map.removeEntry(worldPos);
		state.removeChunkMapIfEmpty(chunkPos);
		setDirty();
		broadcastStageUpdate(worldPos, 0, 0f, 0f, 0L);
	}

	public void markForRerender(BlockPos pos) {
		if (state == null) return;
		ChunkErosionMap map = state.getChunkMap(new net.minecraft.world.level.ChunkPos(pos));
		if (map == null) return;
		ErosionEntry entry = map.getEntry(pos);
		if (entry == null) return;
		broadcastStageUpdate(pos, entry.getErosionStage(), entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
	}

	public void revertGrassStage(BlockPos worldPos, long currentGameTime) {
		if (state == null) return;
		ChunkErosionMap map = state.getChunkMap(new net.minecraft.world.level.ChunkPos(worldPos));
		if (map == null) return;
		ErosionEntry entry = map.getEntry(worldPos);
		if (entry == null) return;
		entry.revertGrassStage(BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK), currentGameTime);
		setDirty();
	}

	public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
		if (state == null) return;
		net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(worldPos);
		ChunkErosionMap map = state.computeChunkMap(chunkPos);
		float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
		map.putEntry(worldPos.immutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
		setDirty();
	}

	public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
		if (state == null) return;
		net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(worldPos);
		ChunkErosionMap map = state.computeChunkMap(chunkPos);
		float threshold = BlockThresholds.randomThreshold(block);
		map.putEntry(worldPos.immutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
		setDirty();
	}

	public void migrateGrassEntries(MinecraftServer server) {
		if (state == null) return;
		ServerLevel world = server.getLevel(Level.OVERWORLD);
		if (world == null) return;

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

		long currentTime = world.getGameTime();
		int migrated = 0;
		for (BlockPos pos : candidates) {
			ChunkErosionMap chunk = state.getChunkMap(new net.minecraft.world.level.ChunkPos(pos));
			if (chunk == null) continue;
			ErosionEntry entry = chunk.getEntry(pos);
			if (entry == null) continue;

			if (!world.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
				removeEntry(pos);
				continue;
			}

			int stage = entry.getErosionStage() - 1;
			Direction facing = facingFromPos(pos);
			world.setBlock(pos,
				TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
					.setValue(ErodedGrassBlock.FACING, facing)
					.setValue(ErodedGrassBlock.STAGE, stage),
				TRMTFlags.BLOCK_UPDATE);
			removeEntry(pos);
			writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK.get(), currentTime);
			migrated++;
		}

		if (migrated > 0) {
			TRMT.LOGGER.info("[TRMT] Migrated {} eroded grass entries to eroded_grass_block.", migrated);
		}
	}

	private static Direction facingFromPos(BlockPos pos) {
		return switch (BlockThresholds.posRotation(pos)) {
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			case 3 -> Direction.EAST;
			default -> Direction.SOUTH;
		};
	}

	public void convertAllErodedToVanilla(MinecraftServer server) {
		if (state == null) return;
		int viewDistance = server.getPlayerList().getViewDistance();
		for (ServerLevel world : server.getAllLevels()) {
			Set<net.minecraft.world.level.ChunkPos> scanned = new HashSet<>();
			for (ServerPlayer player : world.players()) {
				net.minecraft.world.level.ChunkPos playerChunk = player.chunkPosition();
				for (int dx = -viewDistance; dx <= viewDistance; dx++) {
					for (int dz = -viewDistance; dz <= viewDistance; dz++) {
						net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
						if (scanned.add(cp) && world.getChunkSource().getChunkNow(cp.x, cp.z) != null) {
							convertChunkToVanilla(world, cp);
						}
					}
				}
			}
		}
	}

	private void convertChunkToVanilla(ServerLevel world, net.minecraft.world.level.ChunkPos chunkPos) {
		int startX = chunkPos.getMinBlockX();
		int startZ = chunkPos.getMinBlockZ();
		int minY = world.getMinBuildHeight();
		int maxY = world.getMaxBuildHeight();

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		for (int x = startX; x < startX + 16; x++) {
			for (int z = startZ; z < startZ + 16; z++) {
				for (int y = minY; y < maxY; y++) {
					mutable.set(x, y, z);
					Block block = world.getBlockState(mutable).getBlock();
					BlockPos immutable = mutable.immutable();
					if (block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) {
						world.setBlock(immutable, Blocks.GRASS_BLOCK.defaultBlockState(), TRMTFlags.BLOCK_UPDATE);
						removeEntry(immutable);
					} else if (block == TRMTBlocks.ERODED_DIRT.get()) {
						world.setBlock(immutable, Blocks.DIRT.defaultBlockState(), TRMTFlags.BLOCK_UPDATE);
						removeEntry(immutable);
					} else if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
						world.setBlock(immutable, Blocks.COARSE_DIRT.defaultBlockState(), TRMTFlags.BLOCK_UPDATE);
						removeEntry(immutable);
					} else if (block == TRMTBlocks.ERODED_SAND.get()) {
						world.setBlock(immutable, Blocks.SAND.defaultBlockState(), TRMTFlags.BLOCK_UPDATE);
						removeEntry(immutable);
					}
				}
			}
		}
	}

	public Set<net.minecraft.world.level.ChunkPos> getErodedChunkPositions() {
		if (state == null) return Collections.emptySet();
		return state.getAllChunkMaps().keySet();
	}

	public Map<net.minecraft.world.level.ChunkPos, ChunkErosionMap> getAllChunkMaps() {
		if (state == null) return Collections.emptyMap();
		return state.getAllChunkMaps();
	}

	public ChunkErosionMap getChunkMap(net.minecraft.world.level.ChunkPos chunkPos) {
		if (state == null) return null;
		return state.getChunkMap(chunkPos);
	}

	private void setDirty() {
		if (state != null) {
			state.setDirty();
		}
	}

	public void sendFullSyncToPlayer(ServerPlayer player) {
		if (state == null) return;
		for (Map.Entry<net.minecraft.world.level.ChunkPos, ChunkErosionMap> chunkEntry : state.getAllChunkMaps().entrySet()) {
			net.minecraft.world.level.ChunkPos chunkPos = chunkEntry.getKey();
			Map<BlockPos, ErosionEntry> entries = chunkEntry.getValue().getEntries();
			if (entries.isEmpty()) continue;
			java.util.HashMap<BlockPos, SyncChunkPayload.Entry> map = new java.util.HashMap<>(entries.size());
			for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
				ErosionEntry ent = e.getValue();
				map.put(e.getKey(), new SyncChunkPayload.Entry(
					ent.getErosionStage(),
					ent.getWalkedOnCount(),
					ent.getThreshold(),
					ent.getLastTouchedGameTime()
				));
			}
			PacketDistributor.sendToPlayer(player, new SyncChunkPayload(chunkPos, map));
		}
	}

	private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
		if (server == null) return;
		var payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PacketDistributor.sendToPlayer(player, payload);
		}
	}
}
