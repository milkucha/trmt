package milkucha.trmt.client.debug;

import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Debug HUD overlay that always lists every tracked erosion entry as "x y z: count".
 * Rendered in the bottom-left corner to avoid overlapping vanilla F3 output.
 * Reads directly from ErosionMapManager — works in single-player / integrated server.
 */
public class ErosionDebugHud {

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MARGIN = 4;

    public static void register() {
        HudRenderCallback.EVENT.register(ErosionDebugHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Collect all tracked entries across all chunks.
        List<String> lines = new ArrayList<>();
        lines.add("[TRMT] Erosion data:");

        Map<ChunkPos, ChunkErosionMap> allMaps = ErosionMapManager.getInstance().getAllChunkMaps();

        if (allMaps.isEmpty()) {
            lines.add("  (no data — walk on grass blocks)");
        } else {
            List<Map.Entry<BlockPos, ErosionEntry>> allEntries = new ArrayList<>();
            for (ChunkErosionMap chunkMap : allMaps.values()) {
                allEntries.addAll(chunkMap.getEntries().entrySet());
            }

            // Sort by X then Z then Y for a stable, readable order.
            allEntries.sort(Comparator
                    .comparingInt((Map.Entry<BlockPos, ErosionEntry> e) -> e.getKey().getX())
                    .thenComparingInt(e -> e.getKey().getZ())
                    .thenComparingInt(e -> e.getKey().getY()));

            for (Map.Entry<BlockPos, ErosionEntry> entry : allEntries) {
                BlockPos pos = entry.getKey();
                int count = entry.getValue().getWalkedOnCount();
                lines.add(String.format("  %d %d %d: %d", pos.getX(), pos.getY(), pos.getZ(), count));
            }
        }

        TextRenderer textRenderer = client.textRenderer;
        int lineHeight = textRenderer.fontHeight + 1;
        int totalHeight = lines.size() * lineHeight;
        int screenHeight = context.getScaledWindowHeight();

        // Render in the bottom-left corner, growing upward, clear of vanilla HUD elements.
        int x = MARGIN;
        int startY = screenHeight - totalHeight - MARGIN;
        for (String line : lines) {
            context.drawText(textRenderer, line, x, startY, TEXT_COLOR, true);
            startY += lineHeight;
        }
    }
}
