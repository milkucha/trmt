package milkucha.trmt.client.debug;

import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

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
 * Each cell renders the block's item icon with the walkedOnCount overlaid.
 */
public class ErosionDebugHud {

    private static final int TEXT_COLOR  = 0xFFFFFF;
    private static final int COUNT_COLOR = 0xFFFF55; // yellow for visibility over block textures
    private static final int MARGIN      = 4;
    /** Distance between cell origins; icons are rendered at 16×16 within each cell. */
    private static final int CELL = 20;

    public static void register() {
        HudRenderCallback.EVENT.register(ErosionDebugHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientWorld world = client.world;
        // The block the player is physically standing on is one below their feet.
        BlockPos center = client.player.getBlockPos().down();

        TextRenderer tr = client.textRenderer;
        int lineHeight = tr.fontHeight + 1;

        // Total height: 3 rows of cells + gap + coordinate line
        int totalHeight = 3 * CELL + 4 + lineHeight;
        int x0 = MARGIN;
        int y0 = context.getScaledWindowHeight() - MARGIN - totalHeight;

        // Compass cross — column/row offsets from (x0, y0):
        //   col 0 = x0,          col 1 = x0+CELL,   col 2 = x0+2*CELL
        //   row 0 = y0 (north),  row 1 = y0+CELL,   row 2 = y0+2*CELL (south)
        renderCell(context, world, center.north(), x0 + CELL,     y0,          tr); // -z
        renderCell(context, world, center.west(),  x0,            y0 + CELL,   tr); // -x
        renderCell(context, world, center,         x0 + CELL,     y0 + CELL,   tr); // *
        renderCell(context, world, center.east(),  x0 + 2 * CELL, y0 + CELL,   tr); // +x
        renderCell(context, world, center.south(), x0 + CELL,     y0 + 2*CELL, tr); // +z

        // Coordinate line below the cross
        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        context.drawText(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(DrawContext context, ClientWorld world,
                                   BlockPos pos, int x, int y, TextRenderer tr) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // Render block as a 16×16 item icon
        ItemStack stack = new ItemStack(block.asItem());
        if (!stack.isEmpty()) {
            context.drawItemWithoutEntity(stack, x, y);
        }

        // Overlay walkedOnCount/threshold centred horizontally over the icon.
        // Must translate z forward so the text is not depth-culled by the item geometry.
        ErosionEntry entry = getEntry(pos);
        String label = entry != null
                ? String.format("%.1f/%.1f", entry.getWalkedOnCount(), entry.getThreshold())
                : "0.0/-";
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        context.getMatrices().scale(0.5f, 0.5f, 1f);
        // After 0.5× scale, screen position (x, y) maps to draw position (x*2, y*2).
        int textWidth = tr.getWidth(label);
        int drawX = (x * 2) + (16 - textWidth / 2) / 2;
        int drawY = (y * 2) + 4;
        context.drawText(tr, label, drawX, drawY, COUNT_COLOR, true);
        context.getMatrices().pop();
    }

    private static ErosionEntry getEntry(BlockPos pos) {
        ChunkErosionMap map = ErosionMapManager.getInstance().getChunkMap(new ChunkPos(pos));
        if (map == null) return null;
        return map.getEntry(pos);
    }
}
