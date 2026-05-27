package milkucha.trmt.client.debug;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.TRMTClientConfig;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

public class ErosionDebugHud {

    private static final int TEXT_COLOR  = 0xFFFFFFFF;
    private static final int COUNT_COLOR = 0xFFFFFF55;
    private static final int AGE_COLOR   = 0xFF55FFFF;
    private static final int OUT_COLOR   = 0xFFFF5555;
    private static final int MARGIN      = 4;
    private static final int CELL        = 32;

    private ErosionDebugHud() {}

    public static void render(GuiGraphicsExtractor guiGraphics) {
        if (!TRMTClientConfig.get().debugHud) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        ClientLevel world = client.level;
        BlockPos center = client.player.blockPosition().below();

        Font tr = client.font;
        int lineHeight = tr.lineHeight + 1;

        int totalHeight = 3 * CELL + 4 + lineHeight;
        int x0 = MARGIN;
        int y0 = guiGraphics.guiHeight() - MARGIN - totalHeight;

        renderCell(guiGraphics, world, center.north(),     x0 + CELL,         y0,            tr); // -z
        renderCell(guiGraphics, world, center.west(),      x0,                y0 + CELL,     tr); // -x
        renderCell(guiGraphics, world, center,             x0 + CELL,         y0 + CELL,     tr); // *
        renderCell(guiGraphics, world, center.east(),      x0 + 2 * CELL,     y0 + CELL,     tr); // +x
        renderCell(guiGraphics, world, center.south(),     x0 + CELL,         y0 + 2 * CELL, tr); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        guiGraphics.text(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(GuiGraphicsExtractor guiGraphics, ClientLevel world,
                                   BlockPos pos, int x, int y, Font tr) {
        Minecraft client = Minecraft.getInstance();
        BlockState state = world.getBlockState(pos);

        ClientErosionCache.Entry cellEntry = ClientErosionCache.getInstance().getEntry(pos);
        BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(state);
        RandomSource rng = RandomSource.create(0);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(world, pos, state, rng, parts);
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                drawQuad(guiGraphics, client, state, world, pos, x, y, quad);
            }
            for (BakedQuad quad : part.getQuads(Direction.UP)) {
                drawQuad(guiGraphics, client, state, world, pos, x, y, quad);
            }
        }

        long currentTime = client.level != null ? client.level.getGameTime() : 0L;
        int lineH = tr.lineHeight + 1;

        String countLabel = cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        drawCentered(guiGraphics, tr, countLabel, x, y, 0, COUNT_COLOR);

        String ageLabel = cellEntry != null
                ? "age:" + (currentTime - cellEntry.lastTouchedGameTime)
                : "age:-";
        drawCentered(guiGraphics, tr, ageLabel, x, y, lineH, AGE_COLOR);

        long timeout = resolveTimeout(state, cellEntry);
        String outLabel;
        if (timeout < 0) {
            outLabel = "out:-";
        } else {
            boolean isolated = isIsolatedClient(world, pos);
            if (isolated) timeout /= 2;
            outLabel = "out:" + timeout + (isolated ? " I" : "");
        }
        drawCentered(guiGraphics, tr, outLabel, x, y, lineH * 2, OUT_COLOR);
    }

    private static void drawCentered(GuiGraphicsExtractor guiGraphics, Font tr,
                                     String text, int cellX, int cellY,
                                     int lineOffset, int color) {
        int textWidth = tr.width(text);
        int drawX = cellX + (CELL - textWidth) / 2;
        int drawY = cellY + 2 + lineOffset;
        guiGraphics.text(tr, text, drawX, drawY, color, true);
    }

    private static long resolveTimeout(BlockState state, ClientErosionCache.Entry entry) {
        Block block = state.getBlock();
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK.get()) {
            return BlockThresholds.getGrassDeErosionTimeout(state.getValue(ErodedGrassBlock.STAGE) + 1);
        }
        if (block == TRMTBlocks.ERODED_DIRT.get()
                || block == TRMTBlocks.ERODED_COARSE_DIRT.get()) {
            return BlockThresholds.getDirtDeErosionTimeout(block);
        }
        if (block == TRMTBlocks.ERODED_SAND.get()) {
            return BlockThresholds.getSandDeErosionTimeout(state.getValue(ErodedSandBlock.STAGE));
        }
        return -1;
    }

    private static void drawQuad(GuiGraphicsExtractor guiGraphics, Minecraft client,
                                  BlockState state, ClientLevel world, BlockPos pos,
                                  int x, int y, BakedQuad quad) {
        var materialInfo = quad.materialInfo();
        int color;
        if (materialInfo.isTinted()) {
            BlockTintSource tintSource = client.getBlockColors().getTintSource(state, materialInfo.tintIndex());
            color = tintSource != null
                    ? (0xFF000000 | tintSource.colorInWorld(state, world, pos))
                    : 0xFFFFFFFF;
        } else {
            color = 0xFFFFFFFF;
        }
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, materialInfo.sprite(), x, y, CELL, CELL, color);
    }

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static boolean isIsolatedClient(ClientLevel world, BlockPos pos) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.relative(dir).above(dy);
                Block neighborBlock = world.getBlockState(neighbor).getBlock();
                if (neighborBlock == TRMTBlocks.ERODED_GRASS_BLOCK.get()
                        || neighborBlock == TRMTBlocks.ERODED_DIRT.get()
                        || neighborBlock == TRMTBlocks.ERODED_COARSE_DIRT.get()
                        || neighborBlock == TRMTBlocks.ERODED_SAND.get()) {
                    return false;
                }
            }
        }
        return true;
    }
}
