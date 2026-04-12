package milkucha.trmt.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassModels;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
 * Each cell renders the block's top-face texture (biome-tinted where applicable)
 * with walkedOnCount/threshold overlaid.
 */
public class ErosionDebugHud {

    private static final int TEXT_COLOR  = 0xFFFFFF;
    private static final int COUNT_COLOR = 0xFFFF55;
    private static final int MARGIN      = 4;
    /** Cell size equals icon size — no gap between cells. */
    private static final int CELL        = 16;

    public static void register() {
        HudRenderCallback.EVENT.register(ErosionDebugHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientWorld world = client.world;
        BlockPos center = client.player.getBlockPos().down();

        TextRenderer tr = client.textRenderer;
        int lineHeight = tr.fontHeight + 1;

        int totalHeight = 3 * CELL + 4 + lineHeight;
        int x0 = MARGIN;
        int y0 = context.getScaledWindowHeight() - MARGIN - totalHeight;

        renderCell(context, world, center.north(), x0 + CELL,      y0,            tr); // -z
        renderCell(context, world, center.west(),  x0,             y0 + CELL,     tr); // -x
        renderCell(context, world, center,         x0 + CELL,      y0 + CELL,     tr); // *
        renderCell(context, world, center.east(),  x0 + 2 * CELL,  y0 + CELL,     tr); // +x
        renderCell(context, world, center.south(), x0 + CELL,      y0 + 2 * CELL, tr); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        context.drawText(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(DrawContext context, ClientWorld world,
                                   BlockPos pos, int x, int y, TextRenderer tr) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockState state = world.getBlockState(pos);

        // For eroded grass, use the eroded model; otherwise use the vanilla block model.
        ClientErosionCache.Entry cellEntry = getEntry(pos);
        BakedModel model;
        if (state.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)
                && cellEntry != null && cellEntry.stage > 0) {
            model = ErodedGrassModels.getModel(cellEntry.stage);
            if (model == null) model = client.getBlockRenderManager().getModel(state);
        } else {
            model = client.getBlockRenderManager().getModel(state);
        }
        Random rng = Random.create(0);
        // Draw non-culled quads first (e.g. dirt base — no cullface in JSON).
        for (BakedQuad quad : model.getQuads(state, null, rng)) {
            drawQuad(context, client, state, world, pos, x, y, quad);
        }
        // Draw UP-culled quads on top (e.g. eroded overlay — cullface: up).
        for (BakedQuad quad : model.getQuads(state, Direction.UP, rng)) {
            drawQuad(context, client, state, world, pos, x, y, quad);
        }

        // Overlay walkedOnCount/threshold at half scale so it fits within the cell.
        String label = cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        context.getMatrices().scale(0.5f, 0.5f, 1f);
        int textWidth = tr.getWidth(label);
        int drawX = (x * 2) + (16 - textWidth / 2) / 2;
        int drawY = (y * 2) + 4;
        context.drawText(tr, label, drawX, drawY, COUNT_COLOR, true);
        context.getMatrices().pop();
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
        context.drawSprite(x, y, 0, 16, 16, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static ClientErosionCache.Entry getEntry(BlockPos pos) {
        return ClientErosionCache.getInstance().getEntry(pos);
    }
}
