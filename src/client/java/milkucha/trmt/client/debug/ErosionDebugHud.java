package milkucha.trmt.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.TRMTClientConfig;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.BlockThresholds;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;


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
 * Each cell (32×32) renders the block's top-face texture with three lines of data:
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
        HudRenderCallback.EVENT.register(ErosionDebugHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        if (!TRMTClientConfig.get().debugHud) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientWorld world = client.world;
        BlockPos center = client.player.getBlockPos().down();

        TextRenderer tr = client.textRenderer;
        int lineHeight = tr.fontHeight + 1;

        int totalHeight = 3 * CELL + 4 + lineHeight;
        int x0 = MARGIN;
        int y0 = context.getScaledWindowHeight() - MARGIN - totalHeight;

        renderCell(context, world, center.north(), x0 + CELL,     y0,            tr); // -z
        renderCell(context, world, center.west(),  x0,            y0 + CELL,     tr); // -x
        renderCell(context, world, center,         x0 + CELL,     y0 + CELL,     tr); // *
        renderCell(context, world, center.east(),  x0 + 2 * CELL, y0 + CELL,     tr); // +x
        renderCell(context, world, center.south(), x0 + CELL,     y0 + 2 * CELL, tr); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        context.drawText(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(DrawContext context, ClientWorld world,
                                   BlockPos pos, int x, int y, TextRenderer tr) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockState state = world.getBlockState(pos);

        ClientErosionCache.Entry cellEntry = getEntry(pos);
        BakedModel model = client.getBlockRenderManager().getModel(state);
        Random rng = Random.create(0);
        for (BakedQuad quad : model.getQuads(state, null, rng)) {
            drawQuad(context, client, state, world, pos, x, y, quad);
        }
        for (BakedQuad quad : model.getQuads(state, Direction.UP, rng)) {
            drawQuad(context, client, state, world, pos, x, y, quad);
        }

        // Three text lines at 0.5× scale inside the 32×32 cell.
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        context.getMatrices().scale(0.5f, 0.5f, 1f);

        long currentTime = client.world != null ? client.world.getTime() : 0L;
        int lineH = tr.fontHeight + 1; // line height in scaled coords

        // Line 1: walkedOnCount / threshold
        String countLabel = cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        drawCenteredScaled(context, tr, countLabel, x, y, 0, COUNT_COLOR);

        // Line 2: age — ticks since last touch
        String ageLabel = cellEntry != null
                ? "age:" + (currentTime - cellEntry.lastTouchedGameTime)
                : "age:-";
        drawCenteredScaled(context, tr, ageLabel, x, y, lineH, AGE_COLOR);

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
        drawCenteredScaled(context, tr, outLabel, x, y, lineH * 2, OUT_COLOR);

        context.getMatrices().pop();
    }

    /** Draws text centered horizontally within the CELL column starting at screen x, at a given line offset (scaled coords). */
    private static void drawCenteredScaled(DrawContext context, TextRenderer tr,
                                           String text, int cellX, int cellY,
                                           int lineOffset, int color) {
        int textWidth = tr.getWidth(text);
        int drawX = (cellX * 2) + (CELL * 2 - textWidth) / 2;
        int drawY = (cellY * 2) + 2 + lineOffset;
        context.drawText(tr, text, drawX, drawY, color, true);
    }

    private static long resolveTimeout(BlockState state, ClientErosionCache.Entry entry) {
        Block block = state.getBlock();
        if (block == TRMTBlocks.ERODED_GRASS_BLOCK) {
            return BlockThresholds.getGrassDeErosionTimeout(state.get(ErodedGrassBlock.STAGE) + 1);
        }
        if (block == TRMTBlocks.ERODED_DIRT
                || block == TRMTBlocks.ERODED_COARSE_DIRT
                || block == TRMTBlocks.ERODED_ROOTED_DIRT) {
            return BlockThresholds.getDirtDeErosionTimeout(block);
        }
        if (block == TRMTBlocks.ERODED_SAND) {
            return BlockThresholds.getSandDeErosionTimeout(state.get(ErodedSandBlock.STAGE));
        }
        return -1;
    }

    private static void drawQuad(DrawContext context, MinecraftClient client,
                                  BlockState state, ClientWorld world, BlockPos pos,
                                  int x, int y, BakedQuad quad) {
        Sprite sprite = quad.getSprite();
        if (quad.hasColor()) {
            int color = client.getBlockColors().getColor(state, world, pos, quad.getColorIndex());
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >>  8) & 0xFF) / 255.0f;
            float b = ( color        & 0xFF) / 255.0f;
            RenderSystem.setShaderColor(r, g, b, 1.0f);
        }
        context.drawSprite(x, y, 0, CELL, CELL, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static boolean isIsolatedClient(ClientWorld world, BlockPos pos) {
        for (Direction dir : HORIZONTALS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = pos.offset(dir).up(dy);
                Block neighborBlock = world.getBlockState(neighbor).getBlock();
                if (neighborBlock == TRMTBlocks.ERODED_GRASS_BLOCK
                        || neighborBlock == TRMTBlocks.ERODED_DIRT
                        || neighborBlock == TRMTBlocks.ERODED_COARSE_DIRT
                        || neighborBlock == TRMTBlocks.ERODED_ROOTED_DIRT
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
