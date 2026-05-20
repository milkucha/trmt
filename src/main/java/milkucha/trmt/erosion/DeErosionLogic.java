package milkucha.trmt.erosion;

import milkucha.trmt.TRMTConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public final class DeErosionLogic {

    private static List<DeErosionRule> rules = List.of();

    private DeErosionLogic() {
    }

    public static void replaceRules(List<DeErosionRule> newRules) {
        rules = List.copyOf(newRules);
    }

    public static boolean tryNaturallyDeErode(ServerWorld world, ErosionMapManager manager,
                                              BlockPos pos, BlockState state, ErosionEntry entry) {
        Optional<DeErosionRule> rule = findRule(state);
        if (rule.isEmpty() || !allows(rule.get().naturalConfig())) {
            return false;
        }

        long timeout = rule.get().timeoutTicks(state);
        if (BlockThresholds.isIsolated(world, pos, manager)) {
            timeout /= 2;
        }

        if (world.getTime() - entry.getLastTouchedGameTime() <= timeout) {
            return false;
        }

        return stepBack(world, manager, pos, state, entry.getHistory(), rule.get());
    }

    public static boolean tryBonemealDeErode(ServerWorld world, ErosionMapManager manager,
                                             BlockPos pos, BlockState state) {
        Optional<DeErosionRule> rule = findRule(state);
        if (rule.isEmpty() || !allows(rule.get().bonemealConfig())) {
            return false;
        }

        return stepBack(world, manager, pos, state, manager.getHistory(pos), rule.get());
    }

    public static OptionalLong getTimeoutTicks(BlockState state) {
        Optional<DeErosionRule> rule = findRule(state);
        return rule.isPresent()
                ? OptionalLong.of(rule.get().timeoutTicks(state))
                : OptionalLong.empty();
    }

    private static Optional<DeErosionRule> findRule(BlockState state) {
        return rules.stream()
                .filter(rule -> rule.matches(state))
                .findFirst();
    }

    private static boolean hasRule(BlockState state) {
        return findRule(state).isPresent();
    }

    private static boolean stepBack(ServerWorld world, ErosionMapManager manager, BlockPos pos,
                                    BlockState state, List<ErosionHistoryState> currentHistory,
                                    DeErosionRule rule) {
        List<ErosionHistoryState> history = new ArrayList<>(currentHistory);
        BlockState targetState;

        if (!history.isEmpty()) {
            targetState = history.remove(history.size() - 1).toBlockState();
        } else if (rule.fallback().isPresent()) {
            targetState = rule.fallback().get().toBlockState(state, pos);
        } else {
            return false;
        }

        world.setBlockState(pos, targetState, Block.NOTIFY_ALL);
        manager.removeEntry(pos);
        if (!history.isEmpty() || hasRule(targetState)) {
            manager.writeCooldownEntry(pos, targetState.getBlock(), world.getTime(), history);
        }
        return true;
    }

    private static boolean allows(Optional<String> configKey) {
        return configKey.isEmpty() || configValue(configKey.get());
    }

    private static boolean configValue(String key) {
        TRMTConfig config = TRMTConfig.get();
        return switch (key) {
            case "deErosion.grassEnabled" -> config.deErosion.grassEnabled;
            case "deErosion.dirtEnabled" -> config.deErosion.dirtEnabled;
            case "deErosion.sandEnabled" -> config.deErosion.sandEnabled;
            case "bonemealDeErosion.grassEnabled" -> config.bonemealDeErosion.grassEnabled;
            case "bonemealDeErosion.dirtEnabled" -> config.bonemealDeErosion.dirtEnabled;
            case "bonemealDeErosion.sandEnabled" -> config.bonemealDeErosion.sandEnabled;
            default -> throw new IllegalArgumentException("Unknown TRMT config key: " + key);
        };
    }
}
