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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;


/**
 * Debug HUD showing a compass-cross of erosion counts centred on the block under the player.
 *
 * Layout (bottom-left corner):
 *
 *       [-z]
 *  [-x] [*] [+x]
 *       [+z]
 *  <x> <y> <z>
 *
 * Each cell (32x32) renders three lines of text data (block icon rendering was dropped in the
 * 26.x port, since the block-model quad-extraction APIs changed substantially; only the
 * numeric diagnostics, which are the actually useful part of this debug tool, are kept):
 *   walkedOnCount/threshold
 *   age: <ticks since last touch>
 *   out: <de-erosion timeout>
 */
public class ErosionDebugHud {

    private static final int TEXT_COLOR  = 0xFFFFFF;
    private static final int COUNT_COLOR = 0xFFFF55;
    private static final int AGE_COLOR   = 0x55FFFF;
    private static final int OUT_COLOR   = 0xFF5555;
    private static final int MARGIN      = 4;
    private static final int CELL        = 32;

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(milkucha.trmt.TRMT.MOD_ID, "erosion_debug_hud"),
                ErosionDebugHud::render);
    }

    private static void render(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
        if (!TRMTClientConfig.get().debugHud) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        ClientLevel world = client.level;
        BlockPos center = client.player.blockPosition().below();

        Font tr = client.font;
        int lineHeight = tr.lineHeight + 1;

        int totalHeight = 3 * CELL + 4 + lineHeight;
        int x0 = MARGIN;
        int y0 = context.guiHeight() - MARGIN - totalHeight;

        renderCell(context, world, center.north(), x0 + CELL,     y0,            tr); // -z
        renderCell(context, world, center.west(),  x0,            y0 + CELL,     tr); // -x
        renderCell(context, world, center,         x0 + CELL,     y0 + CELL,     tr); // *
        renderCell(context, world, center.east(),  x0 + 2 * CELL, y0 + CELL,     tr); // +x
        renderCell(context, world, center.south(), x0 + CELL,     y0 + 2 * CELL, tr); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        context.text(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(GuiGraphicsExtractor context, ClientLevel world,
                                   BlockPos pos, int x, int y, Font tr) {
        Minecraft client = Minecraft.getInstance();
        BlockState state = world.getBlockState(pos);

        ClientErosionCache.Entry cellEntry = getEntry(pos);
        long currentTime = client.level != null ? client.level.getGameTime() : 0L;
        int lineH = tr.lineHeight + 1;

        // Line 1: walkedOnCount / threshold
        String countLabel = cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        drawCentered(context, tr, countLabel, x, y, 0, COUNT_COLOR);

        // Line 2: age -- ticks since last touch
        String ageLabel = cellEntry != null
                ? "age:" + (currentTime - cellEntry.lastTouchedGameTime)
                : "age:-";
        drawCentered(context, tr, ageLabel, x, y, lineH, AGE_COLOR);

        // Line 3: de-erosion timeout for this block/stage (halved + "I" if isolated)
        long timeout = resolveTimeout(state, cellEntry);
        String outLabel;
        if (timeout < 0) {
            outLabel = "out:-";
        } else {
            boolean isolated = isIsolatedClient(world, pos);
            if (isolated) timeout /= 2;
            outLabel = "out:" + timeout + (isolated ? " I" : "");
        }
        drawCentered(context, tr, outLabel, x, y, lineH * 2, OUT_COLOR);
    }

    /** Draws text centered horizontally within the CELL column starting at screen x, at a given line offset. */
    private static void drawCentered(GuiGraphicsExtractor context, Font tr,
                                     String text, int cellX, int cellY,
                                     int lineOffset, int color) {
        int textWidth = tr.width(text);
        int drawX = cellX + (CELL - textWidth) / 2;
        int drawY = cellY + 2 + lineOffset;
        context.text(tr, text, drawX, drawY, color, true);
    }

    private static long resolveTimeout(BlockState state, ClientErosionCache.Entry entry) {
        Block block = state.getBlock();
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            return BlockThresholds.getGrassDeErosionTimeout(state.getValue(ErodedGrassBlock.STAGE) + 1);
        }
        if (block == TRMTBlocks.ERODED_DIRT
                || block == TRMTBlocks.ERODED_COARSE_DIRT) {
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

    private static boolean isIsolatedClient(ClientLevel world, BlockPos pos) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.relative(dir).above(dy);
                Block neighborBlock = world.getBlockState(neighbor).getBlock();
                if (neighborBlock == TRMTBlocks.ERODED_GRASS_BLOCK
                        || neighborBlock == TRMTBlocks.ERODED_DIRT
                        || neighborBlock == TRMTBlocks.ERODED_COARSE_DIRT
                        || neighborBlock == TRMTBlocks.ERODED_SAND) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ClientErosionCache.Entry getEntry(BlockPos pos) {
        return ClientErosionCache.getInstance().getEntry(pos);
    }
}
