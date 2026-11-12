package milkucha.trmt.client.debug;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.TRMTClientConfig;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
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

    /** Last {@code client-hud-state} text per cell (N, W, centre, E, S), so a line is logged only when it changes. */
    private static final String[] lastLogged = new String[5];

    private ErosionDebugHud() {}

    /**
     * Turns the HUD on/off at runtime: clears the cache when turning off and tells the server,
     * which only sends erosion data to HUD-enabled players. Called by the {@code /trmt-hud} client command.
     */
    public static void setEnabled(boolean enabled) {
        TRMTClientConfig.get().debugHud = enabled;
        TestLog.line(() -> "client-hud enabled=" + enabled);
        java.util.Arrays.fill(lastLogged, null);
        if (!enabled) ClientErosionCache.getInstance().clear();
        sendHudState(Minecraft.getInstance().getConnection(), enabled);
    }

    /**
     * Sends the HUD state to the server (on login when the HUD is enabled in config, and on /trmt-hud).
     * hasChannel: only if the server registered hud_state (a server without TRMT, or an older TRMT,
     * lacks it; NeoForge's send() throws UnsupportedOperationException for an unnegotiated channel).
     */
    public static void sendHudState(ClientPacketListener connection, boolean enabled) {
        if (connection != null && connection.hasChannel(TRMTNetwork.HudStatePayload.TYPE)) {
            connection.send(new TRMTNetwork.HudStatePayload(enabled));
        }
    }

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

        renderCell(guiGraphics, world, center.north(),     x0 + CELL,         y0,            tr, 0, "N"); // -z
        renderCell(guiGraphics, world, center.west(),      x0,                y0 + CELL,     tr, 1, "W"); // -x
        renderCell(guiGraphics, world, center,             x0 + CELL,         y0 + CELL,     tr, 2, "C"); // *
        renderCell(guiGraphics, world, center.east(),      x0 + 2 * CELL,     y0 + CELL,     tr, 3, "E"); // +x
        renderCell(guiGraphics, world, center.south(),     x0 + CELL,         y0 + 2 * CELL, tr, 4, "S"); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        guiGraphics.text(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
    }

    private static void renderCell(GuiGraphicsExtractor guiGraphics, ClientLevel world,
                                   BlockPos pos, int x, int y, Font tr, int cellIndex, String cellName) {
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

        // Text lines at 0.5x scale inside the 32x32 cell (drawCentered works in the scaled coordinates).
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(0.5f, 0.5f);

        long currentTime = client.level != null ? client.level.getGameTime() : 0L;
        int lineH = tr.lineHeight + 1;

        String countLabel = cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        drawCentered(guiGraphics, tr, countLabel, x, y, 0, COUNT_COLOR);
        logHudState(cellIndex, cellName, pos, cellEntry, countLabel);

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

        guiGraphics.pose().popMatrix();
    }

    /** Test log: the values this cell draws (stage, raw count/threshold, the line-1 label), only when they change. */
    private static void logHudState(int cellIndex, String cellName, BlockPos pos,
                                    ClientErosionCache.Entry entry, String countLabel) {
        if (!TestLog.enabled()) return;
        String text = "cell=" + cellName + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " stage=" + (entry != null ? entry.stage : 0)
                + " walked=" + (entry != null ? String.valueOf(entry.walkedOnCount) : "-")
                + " threshold=" + (entry != null ? String.valueOf(entry.threshold) : "-")
                + " label=" + countLabel;
        if (text.equals(lastLogged[cellIndex])) return;
        lastLogged[cellIndex] = text;
        TestLog.line(() -> "client-hud-state " + text);
    }

    private static void drawCentered(GuiGraphicsExtractor guiGraphics, Font tr,
                                     String text, int cellX, int cellY,
                                     int lineOffset, int color) {
        int textWidth = tr.width(text);
        int drawX = (cellX * 2) + (CELL * 2 - textWidth) / 2;
        int drawY = (cellY * 2) + 2 + lineOffset;
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
