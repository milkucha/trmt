package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class ErosionMapManager {
    private static ErosionMapManager INSTANCE;
    private ErosionPersistentState state;
    private MinecraftServer server;

    private ErosionMapManager() {}

    public static ErosionMapManager getInstance() {
        if (INSTANCE == null) INSTANCE = new ErosionMapManager();
        return INSTANCE;
    }

    public void loadState(MinecraftServer server) {
        this.server = server;
        this.state = ErosionPersistentState.getOrCreate(server);
    }

    public static void reset() { INSTANCE = null; }

    public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        map.recordStep(worldPos, block, amount, currentGameTime);
        state.setDirty();
    }

    public void broadcastEntryUpdate(BlockPos pos, Block block) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(pos));
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
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        map.removeEntry(worldPos);
        state.removeChunkMapIfEmpty(chunkPos);
        state.setDirty();
        broadcastStageUpdate(worldPos, 0, 0f, 0f, 0L);
    }

    public void markForRerender(BlockPos pos) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(pos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(pos);
        if (entry == null) return;
        broadcastStageUpdate(pos, entry.getErosionStage(), entry.getWalkedOnCount(), entry.getThreshold(), entry.getLastTouchedGameTime());
    }

    public ChunkErosionMap getChunkMap(ChunkPos chunkPos) {
        if (state == null) return null;
        return state.getChunkMap(chunkPos);
    }

    public void revertGrassStage(BlockPos worldPos, long currentGameTime) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(new ChunkPos(worldPos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(worldPos);
        if (entry == null) return;
        entry.revertGrassStage(BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK), currentGameTime);
        state.setDirty();
    }

    public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
        map.putEntry(worldPos.immutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.setDirty();
    }

    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkPos chunkPos = new ChunkPos(worldPos);
        ChunkErosionMap map = state.computeChunkMap(chunkPos);
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.immutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.setDirty();
    }

    public void migrateGrassEntries(MinecraftServer server) {
        if (state == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

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

        long currentTime = level.getGameTime();
        int migrated = 0;
        for (BlockPos pos : candidates) {
            ChunkErosionMap chunk = state.getChunkMap(new ChunkPos(pos));
            if (chunk == null) continue;
            ErosionEntry entry = chunk.getEntry(pos);
            if (entry == null) continue;

            if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                removeEntry(pos);
                continue;
            }

            int stage = entry.getErosionStage() - 1;
            Direction facing = facingFromPos(pos);
            level.setBlock(pos,
                TRMTBlocks.ERODED_GRASS_BLOCK.get().defaultBlockState()
                    .setValue(ErodedGrassBlock.FACING, facing)
                    .setValue(ErodedGrassBlock.STAGE, stage),
                Block.UPDATE_ALL);
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
            case 1  -> Direction.WEST;
            case 2  -> Direction.NORTH;
            case 3  -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }

    public void revertDisabledBlocks(ServerLevel level, ChunkPos chunkPos) {
        if (state == null) return;
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(x, y, z);
                    Block block = level.getBlockState(mutable).getBlock();

                    if (!t.grassEnabled && block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) {
                        level.setBlock(mutable.immutable(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(mutable.immutable());
                    } else if (!t.dirtEnabled) {
                        if (block == TRMTBlocks.ERODED_DIRT.get()) {
                            level.setBlock(mutable.immutable(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                            removeEntry(mutable.immutable());
                        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
                            level.setBlock(mutable.immutable(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                            removeEntry(mutable.immutable());
                        }
                    } else if (!t.sandEnabled && block == TRMTBlocks.ERODED_SAND.get()) {
                        level.setBlock(mutable.immutable(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(mutable.immutable());
                    }
                }
            }
        }
    }

    public void revertDisabledBlocksAllLoaded(MinecraftServer server) {
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int viewDistance = server.getPlayerList().getViewDistance();
        for (ServerLevel level : server.getAllLevels()) {
            Set<ChunkPos> scanned = new HashSet<>();
            for (ServerPlayer player : level.getPlayers(p -> true)) {
                ChunkPos playerChunk = player.chunkPosition();
                for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                    for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                        ChunkPos cp = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                        if (scanned.add(cp) && level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null) {
                            revertDisabledBlocks(level, cp);
                        }
                    }
                }
            }
        }
    }

    public Map<ChunkPos, ChunkErosionMap> getAllChunkMaps() {
        if (state == null) return Collections.emptyMap();
        return state.getAllChunkMaps();
    }

    public void sendFullSyncToPlayer(ServerPlayer player) {
        if (state == null) return;
        for (Map.Entry<ChunkPos, ChunkErosionMap> chunkEntry : state.getAllChunkMaps().entrySet()) {
            ChunkPos chunkPos = chunkEntry.getKey();
            Map<BlockPos, ErosionEntry> entries = chunkEntry.getValue().getEntries();
            if (entries.isEmpty()) continue;

            List<SyncChunkPayload.Entry> payloadEntries = new ArrayList<>(entries.size());
            for (Map.Entry<BlockPos, ErosionEntry> e : entries.entrySet()) {
                payloadEntries.add(new SyncChunkPayload.Entry(
                    e.getKey(),
                    e.getValue().getErosionStage(),
                    e.getValue().getWalkedOnCount(),
                    e.getValue().getThreshold(),
                    e.getValue().getLastTouchedGameTime()
                ));
            }
            PacketDistributor.sendToPlayer(player, new SyncChunkPayload(chunkPos.x, chunkPos.z, payloadEntries));
        }
    }

    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (server == null) return;
        UpdateStagePayload payload = new UpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
