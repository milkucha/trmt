package milkucha.trmt.erosion;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedDirtBlock;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

public record ErosionHistoryState(Block block, int stage, Direction facing) {

    public static ErosionHistoryState from(BlockState state) {
        Block block = state.getBlock();
        int stage = -1;
        Direction facing = null;

        if (state.contains(Properties.HORIZONTAL_FACING)) {
            facing = state.get(Properties.HORIZONTAL_FACING);
        }

        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            stage = state.get(ErodedGrassBlock.STAGE);
        } else if (block == TRMTBlocks.ERODED_DIRT || block == TRMTBlocks.ERODED_COARSE_DIRT) {
            stage = state.get(ErodedDirtBlock.STAGE);
        } else if (block == TRMTBlocks.ERODED_SAND) {
            stage = state.get(ErodedSandBlock.STAGE);
        }

        return new ErosionHistoryState(block, stage, facing);
    }

    public BlockState toBlockState() {
        BlockState state = block.getDefaultState();

        if (facing != null && state.contains(Properties.HORIZONTAL_FACING)) {
            state = state.with(Properties.HORIZONTAL_FACING, facing);
        }

        if (stage >= 0) {
            if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
                state = state.with(ErodedGrassBlock.STAGE, stage);
            } else if (block == TRMTBlocks.ERODED_DIRT || block == TRMTBlocks.ERODED_COARSE_DIRT) {
                state = state.with(ErodedDirtBlock.STAGE, stage);
            } else if (block == TRMTBlocks.ERODED_SAND) {
                state = state.with(ErodedSandBlock.STAGE, stage);
            }
        }

        return state;
    }

    public String blockId() {
        return Registries.BLOCK.getId(block).toString();
    }
}
