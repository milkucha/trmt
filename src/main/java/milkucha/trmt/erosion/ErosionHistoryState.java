package milkucha.trmt.erosion;

import milkucha.trmt.erosion.operation.ErosionTransformSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;

public record ErosionHistoryState(Block block, Map<String, String> properties) {

    public ErosionHistoryState {
        properties = Map.copyOf(properties);
    }

    public static ErosionHistoryState from(BlockState state) {
        Map<String, String> properties = new HashMap<>();
        for (var entry : state.getEntries().entrySet()) {
            properties.put(entry.getKey().getName(),
                    ErosionTransformSupport.propertyValue(entry.getKey(), entry.getValue()));
        }

        return new ErosionHistoryState(state.getBlock(), properties);
    }

    public BlockState toBlockState() {
        return ErosionTransformSupport.withProperties(block.getDefaultState(), properties);
    }

    public String blockId() {
        return Registries.BLOCK.getId(block).toString();
    }
}
