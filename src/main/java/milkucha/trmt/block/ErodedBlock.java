package milkucha.trmt.block;

import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionHistoryState;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.erosion.ErosionLogic;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.ToLongFunction;

public abstract class ErodedBlock extends Block {

    private final BooleanSupplier naturalDeErosionEnabled;
    private final ToLongFunction<BlockState> timeoutFactory;
    private final EmptyHistoryTransition emptyHistoryTransition;

    protected ErodedBlock(Settings settings,
                          BooleanSupplier naturalDeErosionEnabled,
                          ToLongFunction<BlockState> timeoutFactory,
                          EmptyHistoryTransition emptyHistoryTransition) {
        super(settings);
        this.naturalDeErosionEnabled = naturalDeErosionEnabled;
        this.timeoutFactory = timeoutFactory;
        this.emptyHistoryTransition = emptyHistoryTransition;
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        deErode(state, world, pos, false);
    }

    public boolean deErode(BlockState state, ServerWorld world, BlockPos pos, boolean bypassTimeout) {
        if (!bypassTimeout && !naturalDeErosionEnabled.getAsBoolean()) return false;

        ErosionMapManager manager = ErosionMapManager.getInstance();
        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(pos));
        ErosionEntry entry = chunkMap != null ? chunkMap.getEntry(pos) : null;

        long currentTime = world.getTime();
        long timeout = timeoutFactory.applyAsLong(state);
        if (BlockThresholds.isIsolated(world, pos, manager)) timeout /= 2;
        if (!bypassTimeout && entry != null && currentTime - entry.getLastTouchedGameTime() <= timeout) {
            return false;
        }

        List<ErosionHistoryState> history = entry != null
                ? new ArrayList<>(entry.getHistory())
                : new ArrayList<>();

        DeErosionResult result;
        if (!history.isEmpty()) {
            ErosionHistoryState previous = history.remove(history.size() - 1);
            result = new DeErosionResult(previous.toBlockState(), !history.isEmpty() || ErosionLogic.isErodedBlock(previous.block()));
        } else {
            result = emptyHistoryTransition.next(state);
        }

        world.setBlockState(pos, result.state(), Block.NOTIFY_ALL);
        manager.removeEntry(pos);
        if (result.writeCooldown()) {
            manager.writeCooldownEntry(pos, result.state().getBlock(), currentTime, history);
        }
        return true;
    }

    @FunctionalInterface
    public interface EmptyHistoryTransition {
        DeErosionResult next(BlockState state);
    }

    public record DeErosionResult(BlockState state, boolean writeCooldown) {
    }
}
