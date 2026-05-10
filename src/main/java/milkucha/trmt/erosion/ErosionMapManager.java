package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
        this.state  = ErosionPersistentState.getOrCreate(server);
    }

    public static void reset() { INSTANCE = null; }

    private static ChunkPos chunkOf(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public void onStep(BlockPos worldPos, Block block, float amount, long currentGameTime) {
        if (state == null) return;
        ChunkErosionMap map = state.computeChunkMap(chunkOf(worldPos));
        map.recordStep(worldPos, block, amount, currentGameTime);
        state.setDirty();
    }

    public void applyWear(ServerLevel world, BlockPos pos, float amount, long currentGameTime) {
        if (state == null || amount <= 0.0f || !world.isLoaded(pos)) return;

        BlockState stateAtPos = world.getBlockState(pos);
        Block block = stateAtPos.getBlock();
        TRMTConfig.ErosionToggles erosion = TRMTConfig.get().erosion;
        boolean tracked = (erosion.grassEnabled && (stateAtPos.is(Blocks.GRASS_BLOCK) || stateAtPos.is(TRMTBlocks.ERODED_GRASS_BLOCK)))
                || (erosion.dirtEnabled && (stateAtPos.is(Blocks.DIRT) || stateAtPos.is(TRMTBlocks.ERODED_DIRT)))
                || (erosion.sandEnabled && (stateAtPos.is(Blocks.SAND) || stateAtPos.is(TRMTBlocks.ERODED_SAND)))
                || (erosion.leavesEnabled && BlockThresholds.isLeaves(block));

        if (!tracked) return;

        onStep(pos, block, amount, currentGameTime);
        tryTransform(world, pos);
        broadcastEntryUpdate(pos, block);
    }

    public void broadcastEntryUpdate(BlockPos pos, Block block) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(chunkOf(pos));
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
        ChunkPos chunkPos = chunkOf(worldPos);
        ChunkErosionMap map = state.getChunkMap(chunkPos);
        if (map == null) return;
        map.removeEntry(worldPos);
        state.removeChunkMapIfEmpty(chunkPos);
        state.setDirty();
        broadcastStageUpdate(worldPos, 0, 0f, 0f, 0L);
    }

    public void markForRerender(BlockPos pos) {
        if (state == null) return;
        ChunkErosionMap map = state.getChunkMap(chunkOf(pos));
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
        ChunkErosionMap map = state.getChunkMap(chunkOf(worldPos));
        if (map == null) return;
        ErosionEntry entry = map.getEntry(worldPos);
        if (entry == null) return;
        entry.revertGrassStage(BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK), currentGameTime);
        state.setDirty();
    }

    public void writeErodedGrassCooldownEntry(BlockPos worldPos, int stage, long currentGameTime) {
        if (state == null) return;
        ChunkErosionMap map = state.computeChunkMap(chunkOf(worldPos));
        float threshold = BlockThresholds.randomThreshold(Blocks.GRASS_BLOCK);
        map.putEntry(worldPos.immutable(), new ErosionEntry(Blocks.GRASS_BLOCK, threshold, 0f, currentGameTime, stage));
        state.setDirty();
    }

    public void writeCooldownEntry(BlockPos worldPos, Block block, long currentGameTime) {
        if (state == null) return;
        ChunkErosionMap map = state.computeChunkMap(chunkOf(worldPos));
        float threshold = BlockThresholds.randomThreshold(block);
        map.putEntry(worldPos.immutable(), new ErosionEntry(block, threshold, 0f, currentGameTime));
        state.setDirty();
    }

    public void migrateGrassEntries(MinecraftServer server) {
        if (state == null) return;
        ServerLevel world = server.overworld();

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
            ChunkErosionMap chunk = state.getChunkMap(chunkOf(pos));
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
                    TRMTBlocks.ERODED_GRASS_BLOCK.defaultBlockState()
                            .setValue(ErodedGrassBlock.FACING, facing)
                            .setValue(ErodedGrassBlock.STAGE, stage),
                    Block.UPDATE_ALL);
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, currentTime);
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

    private void tryTransform(ServerLevel world, BlockPos pos) {
        ChunkErosionMap map = getChunkMap(chunkOf(pos));
        if (map == null) return;

        ErosionEntry entry = map.getEntry(pos);
        if (entry == null || entry.getWalkedOnCount() < entry.getThreshold()) return;

        BlockState blockState = world.getBlockState(pos);

        if (blockState.is(Blocks.SAND)) {
            Direction facing = facingFromPos(pos);
            world.setBlock(pos,
                    TRMTBlocks.ERODED_SAND.defaultBlockState()
                            .setValue(ErodedSandBlock.FACING, facing)
                            .setValue(ErodedSandBlock.STAGE, 0),
                    Block.UPDATE_ALL);
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getGameTime());
            return;
        }

        if (blockState.is(TRMTBlocks.ERODED_SAND)) {
            int stage = blockState.getValue(ErodedSandBlock.STAGE);
            if (stage < 4) {
                world.setBlock(pos, blockState.setValue(ErodedSandBlock.STAGE, stage + 1), Block.UPDATE_ALL);
            }
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_SAND, world.getGameTime());
            return;
        }

        if (BlockThresholds.isLeaves(blockState.getBlock())) {
            float dropChance = TRMTConfig.get().erosionThresholds.leaves.dropChance;
            boolean drops = dropChance >= 1.0f || (dropChance > 0.0f && ThreadLocalRandom.current().nextFloat() < dropChance);
            world.destroyBlock(pos, drops);
            removeEntry(pos);
            return;
        }

        if (blockState.is(Blocks.GRASS_BLOCK)) {
            Direction facing = facingFromPos(pos);
            world.setBlock(pos,
                    TRMTBlocks.ERODED_GRASS_BLOCK.defaultBlockState()
                            .setValue(ErodedGrassBlock.FACING, facing)
                            .setValue(ErodedGrassBlock.STAGE, 0),
                    Block.UPDATE_ALL);
            removeEntry(pos);
            writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getGameTime());
            return;
        }

        if (blockState.is(TRMTBlocks.ERODED_GRASS_BLOCK)) {
            Direction facing = blockState.getValue(ErodedGrassBlock.FACING);
            int stage = blockState.getValue(ErodedGrassBlock.STAGE);
            if (stage < 4) {
                world.setBlock(pos, blockState.setValue(ErodedGrassBlock.STAGE, stage + 1), Block.UPDATE_ALL);
                removeEntry(pos);
                writeCooldownEntry(pos, TRMTBlocks.ERODED_GRASS_BLOCK, world.getGameTime());
                return;
            }
            world.setBlock(pos, TRMTBlocks.ERODED_DIRT.defaultBlockState().setValue(ErodedDirtBlock.FACING, facing), Block.UPDATE_ALL);
            removeEntry(pos);
            return;
        }

        if (blockState.is(TRMTBlocks.ERODED_DIRT)) {
            Direction facing = blockState.getValue(ErodedDirtBlock.FACING);
            int stage = blockState.getValue(ErodedDirtBlock.STAGE);
            if (stage < 3) {
                world.setBlock(pos, blockState.setValue(ErodedDirtBlock.STAGE, stage + 1), Block.UPDATE_ALL);
                removeEntry(pos);
                return;
            }
            world.setBlock(pos, TRMTBlocks.ERODED_COARSE_DIRT.defaultBlockState().setValue(ErodedDirtBlock.FACING, facing), Block.UPDATE_ALL);
            removeEntry(pos);
            return;
        }

        if (blockState.is(Blocks.DIRT)) {
            Direction facing = facingFromPos(pos);
            world.setBlock(pos,
                    TRMTBlocks.ERODED_DIRT.defaultBlockState()
                            .setValue(ErodedDirtBlock.FACING, facing)
                            .setValue(ErodedDirtBlock.STAGE, 1),
                    Block.UPDATE_ALL);
            removeEntry(pos);
        }
    }

    public void revertDisabledBlocks(ServerLevel world, ChunkPos chunkPos) {
        if (state == null) return;
        TRMTConfig.ErosionToggles t = TRMTConfig.get().erosion;
        if (t.grassEnabled && t.dirtEnabled && t.sandEnabled) return;

        int startX = chunkPos.x() * 16;
        int startZ = chunkPos.z() * 16;
        int minY   = world.getMinY();
        int maxY   = world.getMaxY();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(x, y, z);
                    Block block = world.getBlockState(mutable).getBlock();

                    if (!t.grassEnabled && block == TRMTBlocks.ERODED_GRASS_BLOCK) {
                        world.setBlock(mutable.immutable(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        removeEntry(mutable.immutable());
                    } else if (!t.dirtEnabled) {
                        if (block == TRMTBlocks.ERODED_DIRT) {
                            world.setBlock(mutable.immutable(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                            removeEntry(mutable.immutable());
                        } else if (block == TRMTBlocks.ERODED_COARSE_DIRT) {
                            world.setBlock(mutable.immutable(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                            removeEntry(mutable.immutable());
                        }
                    } else if (!t.sandEnabled && block == TRMTBlocks.ERODED_SAND) {
                        world.setBlock(mutable.immutable(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
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
        for (ServerLevel world : server.getAllLevels()) {
            Set<ChunkPos> scanned = new HashSet<>();
            for (ServerPlayer player : world.players()) {
                ChunkPos playerChunk = player.chunkPosition();
                for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                    for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                        ChunkPos cp = new ChunkPos(playerChunk.x() + dx, playerChunk.z() + dz);
                        if (scanned.add(cp) && world.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false) != null) {
                            revertDisabledBlocks(world, cp);
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

            TRMTSyncChunkPayload payload = new TRMTSyncChunkPayload(chunkPos, entries);
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void broadcastStageUpdate(BlockPos pos, int stage, float walkedOnCount, float threshold, long lastTouchedGameTime) {
        if (server == null) return;
        TRMTUpdateStagePayload payload = new TRMTUpdateStagePayload(pos, stage, walkedOnCount, threshold, lastTouchedGameTime);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
