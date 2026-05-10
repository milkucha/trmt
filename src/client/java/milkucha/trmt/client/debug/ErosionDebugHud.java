package milkucha.trmt.client.debug;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.TRMTClientConfig;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.BlockThresholds;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ErosionDebugHud {

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int COUNT_COLOR = 0xFFFF55;
    private static final int AGE_COLOR = 0x55FFFF;
    private static final int OUT_COLOR = 0xFF5555;
    private static final int MARGIN = 4;
    private static final int CELL = 46;

    private ErosionDebugHud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("trmt", "erosion_debug"), ErosionDebugHud::render);
    }

    private static void render(GuiGraphicsExtractor context, DeltaTracker tickDelta) {
        if (!TRMTClientConfig.get().debugHud) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        BlockPos center = client.player.blockPosition().below();
        Font font = client.font;
        int totalHeight = 3 * CELL + font.lineHeight + 6;
        int x0 = MARGIN;
        int y0 = context.guiHeight() - MARGIN - totalHeight;

        renderCell(context, center.north(), x0 + CELL, y0, font);
        renderCell(context, center.west(), x0, y0 + CELL, font);
        renderCell(context, center, x0 + CELL, y0 + CELL, font);
        renderCell(context, center.east(), x0 + 2 * CELL, y0 + CELL, font);
        renderCell(context, center.south(), x0 + CELL, y0 + 2 * CELL, font);

        context.text(font, center.getX() + " " + center.getY() + " " + center.getZ(), x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(GuiGraphicsExtractor context, BlockPos pos, int x, int y, Font font) {
        Minecraft client = Minecraft.getInstance();
        BlockState state = client.level.getBlockState(pos);
        ClientErosionCache.Entry entry = ClientErosionCache.getInstance().getEntry(pos);
        long currentTime = client.level.getGameTime();
        int lineHeight = font.lineHeight + 1;

        context.fill(net.minecraft.client.renderer.RenderPipelines.GUI, x, y, x + CELL - 2, y + CELL - 2, 0x88000000);
        context.text(font, shortBlockName(state.getBlock()), x + 2, y + 2, TEXT_COLOR, true);

        String countLabel = entry != null
                ? String.format("%.1f/%.1f", entry.walkedOnCount, entry.threshold)
                : "0.0/-";
        context.text(font, countLabel, x + 2, y + 2 + lineHeight, COUNT_COLOR, true);

        String ageLabel = entry != null ? "age:" + (currentTime - entry.lastTouchedGameTime) : "age:-";
        context.text(font, ageLabel, x + 2, y + 2 + lineHeight * 2, AGE_COLOR, true);

        long timeout = resolveTimeout(state);
        String outLabel = timeout < 0 ? "out:-" : "out:" + (isIsolatedClient(pos) ? timeout / 2 : timeout);
        context.text(font, outLabel, x + 2, y + 2 + lineHeight * 3, OUT_COLOR, true);
    }

    private static String shortBlockName(Block block) {
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) return "grass";
        if (block == TRMTBlocks.ERODED_DIRT) return "dirt";
        if (block == TRMTBlocks.ERODED_COARSE_DIRT) return "coarse";
        if (block == TRMTBlocks.ERODED_SAND) return "sand";
        return "-";
    }

    private static long resolveTimeout(BlockState state) {
        Block block = state.getBlock();
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            return BlockThresholds.getGrassDeErosionTimeout(state.getValue(ErodedGrassBlock.STAGE) + 1);
        }
        if (block == TRMTBlocks.ERODED_DIRT || block == TRMTBlocks.ERODED_COARSE_DIRT) {
            return BlockThresholds.getDirtDeErosionTimeout(block);
        }
        if (block == TRMTBlocks.ERODED_SAND) {
            return BlockThresholds.getSandDeErosionTimeout(state.getValue(ErodedSandBlock.STAGE));
        }
        return -1;
    }

    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static boolean isIsolatedClient(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return true;
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.relative(dir).above(dy);
                Block block = client.level.getBlockState(neighbor).getBlock();
                if (block == TRMTBlocks.ERODED_GRASS_BLOCK
                        || block == TRMTBlocks.ERODED_DIRT
                        || block == TRMTBlocks.ERODED_COARSE_DIRT
                        || block == TRMTBlocks.ERODED_SAND) {
                    return false;
                }
            }
        }
        return true;
    }
}
