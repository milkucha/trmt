package milkucha.trmt.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.TRMTClientConfig;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderGuiEvent;


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
 * Each cell (32x32) renders the block's top-face texture with three lines of data:
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

    public static void render(RenderGuiEvent.Post event) {
        if (!TRMTClientConfig.get().debugHud) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        ClientLevel level = minecraft.level;
        BlockPos center = minecraft.player.blockPosition().below();

        Font font = minecraft.font;
        int lineHeight = font.lineHeight + 1;

        int totalHeight = 3 * CELL + 4 + lineHeight;
        int x0 = MARGIN;
        int y0 = guiGraphics.guiHeight() - MARGIN - totalHeight;

        renderCell(guiGraphics, level, center.north(), x0 + CELL,     y0,            font); // -z
        renderCell(guiGraphics, level, center.west(),  x0,            y0 + CELL,     font); // -x
        renderCell(guiGraphics, level, center,         x0 + CELL,     y0 + CELL,     font); // *
        renderCell(guiGraphics, level, center.east(),  x0 + 2 * CELL, y0 + CELL,     font); // +x
        renderCell(guiGraphics, level, center.south(), x0 + CELL,     y0 + 2 * CELL, font); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        guiGraphics.drawString(font, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(GuiGraphics guiGraphics, ClientLevel level,
                                   BlockPos pos, int x, int y, Font font) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = level.getBlockState(pos);

        ClientErosionCache.Entry cellEntry = getEntry(pos);
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        RandomSource rng = RandomSource.create(0);
        for (BakedQuad quad : model.getQuads(state, null, rng)) {
            drawQuad(guiGraphics, minecraft, state, level, pos, x, y, quad);
        }
        for (BakedQuad quad : model.getQuads(state, Direction.UP, rng)) {
            drawQuad(guiGraphics, minecraft, state, level, pos, x, y, quad);
        }

        // Three text lines at 0.5x scale inside the 32x32 cell.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.pose().scale(0.5f, 0.5f, 1f);

        long currentTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        int lineH = font.lineHeight + 1; // line height in scaled coords

        // Line 1: walkedOnCount / threshold
        String countLabel = cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        drawCenteredScaled(guiGraphics, font, countLabel, x, y, 0, COUNT_COLOR);

        // Line 2: age -- ticks since last touch
        String ageLabel = cellEntry != null
                ? "age:" + (currentTime - cellEntry.lastTouchedGameTime)
                : "age:-";
        drawCenteredScaled(guiGraphics, font, ageLabel, x, y, lineH, AGE_COLOR);

        // Line 3: de-erosion timeout for this block/stage (halved + "I" if isolated)
        long timeout = resolveTimeout(state, cellEntry);
        String outLabel;
        if (timeout < 0) {
            outLabel = "out:-";
        } else {
            boolean isolated = isIsolatedClient(level, pos);
            if (isolated) timeout /= 2;
            outLabel = "out:" + timeout + (isolated ? " I" : "");
        }
        drawCenteredScaled(guiGraphics, font, outLabel, x, y, lineH * 2, OUT_COLOR);

        guiGraphics.pose().popPose();
    }

    /** Draws text centered horizontally within the CELL column starting at screen x, at a given line offset (scaled coords). */
    private static void drawCenteredScaled(GuiGraphics guiGraphics, Font font,
                                           String text, int cellX, int cellY,
                                           int lineOffset, int color) {
        int textWidth = font.width(text);
        int drawX = (cellX * 2) + (CELL * 2 - textWidth) / 2;
        int drawY = (cellY * 2) + 2 + lineOffset;
        guiGraphics.drawString(font, text, drawX, drawY, color, true);
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

    private static void drawQuad(GuiGraphics guiGraphics, Minecraft minecraft,
                                  BlockState state, ClientLevel level, BlockPos pos,
                                  int x, int y, BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        if (quad.isTinted()) {
            int color = minecraft.getBlockColors().getColor(state, level, pos, quad.getTintIndex());
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >>  8) & 0xFF) / 255.0f;
            float b = ( color        & 0xFF) / 255.0f;
            RenderSystem.setShaderColor(r, g, b, 1.0f);
        }
        guiGraphics.blit(x, y, 0, CELL, CELL, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static boolean isIsolatedClient(ClientLevel level, BlockPos pos) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.relative(dir).above(dy);
                Block neighborBlock = level.getBlockState(neighbor).getBlock();
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

    private static ClientErosionCache.Entry getEntry(BlockPos pos) {
        return ClientErosionCache.getInstance().getEntry(pos);
    }
}
