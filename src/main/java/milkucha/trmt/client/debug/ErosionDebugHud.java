package milkucha.trmt.client.debug;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TestLog;
import milkucha.trmt.block.ErodedGrassBlock;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.TRMTClientConfig;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.network.TRMTNetwork;
import milkucha.trmt.path.PathSpeedBuff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/**
 * Debug HUD showing a compass-cross of erosion counts centred on the block under the player.
 *
 * Layout (bottom-left corner):
 *
 *       [-z]
 *  [-x] [*] [+x]
 *       [+z]
 *  &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *
 * Each cell (32×32) renders the block's top-face texture with three lines of data:
 *   walkedOnCount/threshold
 *   age: &lt;ticks since last touch&gt;
 *   out: &lt;de-erosion timeout&gt;
 */
public class ErosionDebugHud {

    private static final int TEXT_COLOR  = 0xFFFFFFFF;
    private static final int COUNT_COLOR = 0xFFFFFF55;
    private static final int AGE_COLOR   = 0xFF55FFFF;
    private static final int OUT_COLOR   = 0xFFFF5555;
    private static final int MARGIN      = 4;
    private static final int CELL        = 32;

    /** Last {@code client-hud-state} text per cell (N, W, centre, E, S), so a line is logged only when it changes. */
    private static final String[] lastLogged = new String[5];

    /** Last {@code client-hud-buff} value logged (percent), so a line is logged only when it changes. */
    private static int lastLoggedBuff = Integer.MIN_VALUE;

    private ErosionDebugHud() {}

    /**
     * Turns the HUD on/off at runtime: clears the cache when turning off and tells the server,
     * which only sends erosion data to HUD-enabled players. Called by the {@code /trmt-hud} client command.
     */
    public static void setEnabled(boolean enabled) {
        TRMTClientConfig.get().debugHud = enabled;
        TestLog.line(() -> "client-hud enabled=" + enabled);
        java.util.Arrays.fill(lastLogged, null);
        lastLoggedBuff = Integer.MIN_VALUE;
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

    public static void render(GuiGraphics guiGraphics) {
        if (!TRMTClientConfig.get().debugHud) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        ClientLevel world = client.level;
        BlockPos center = client.player.blockPosition().below();

        Font tr = client.font;
        int lineHeight = tr.lineHeight + 1;

        int totalHeight = 3 * CELL + 4 + 2 * lineHeight;
        int x0 = MARGIN;
        int y0 = client.getWindow().getGuiScaledHeight() - MARGIN - totalHeight;

        renderCell(guiGraphics, world, center.north(),         x0 + CELL,         y0,            tr, 0, "N"); // -z
        renderCell(guiGraphics, world, center.west(),          x0,                y0 + CELL,     tr, 1, "W"); // -x
        renderCell(guiGraphics, world, center,                 x0 + CELL,         y0 + CELL,     tr, 2, "C"); // *
        renderCell(guiGraphics, world, center.east(),          x0 + 2 * CELL,     y0 + CELL,     tr, 3, "E"); // +x
        renderCell(guiGraphics, world, center.south(),         x0 + CELL,         y0 + 2 * CELL, tr, 4, "S"); // +z

        String coords = center.getX() + " " + center.getY() + " " + center.getZ();
        guiGraphics.drawString(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);

        // Path speed buff: the movement-speed modifier is synced with the attribute (also for a ridden mount).
        boolean riding = client.player.getVehicle() instanceof LivingEntity;
        LivingEntity carrier = riding ? (LivingEntity) client.player.getVehicle() : client.player;
        int buffPercent = 0;
        AttributeInstance speed = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            AttributeModifier mod = speed.getModifier(PathSpeedBuff.MODIFIER_ID);
            if (mod != null) buffPercent = (int) Math.round(mod.amount() * 100);
        }
        guiGraphics.drawString(tr, "speed_buff = " + buffPercent + "%" + (riding ? " (mount)" : ""),
                x0, y0 + 3 * CELL + 4 + lineHeight, TEXT_COLOR, true);
        if (TestLog.enabled() && buffPercent != lastLoggedBuff) {
            lastLoggedBuff = buffPercent;
            final int logged = buffPercent;
            TestLog.line(() -> "client-hud-buff value=" + logged);
        }
    }

    private static void renderCell(GuiGraphics guiGraphics, ClientLevel world,
                                   BlockPos pos, int x, int y, Font tr, int cellIndex, String cellName) {
        Minecraft client = Minecraft.getInstance();
        BlockState state = world.getBlockState(pos);

        ClientErosionCache.Entry cellEntry = ClientErosionCache.getInstance().getEntry(pos);
        BlockStateModel model = client.getBlockRenderer().getBlockModel(state);
        RandomSource rng = RandomSource.create(0);
        List<BlockModelPart> parts = model.collectParts(rng);
        for (BlockModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                drawQuad(guiGraphics, client, state, world, pos, x, y, quad);
            }
            for (BakedQuad quad : part.getQuads(Direction.UP)) {
                drawQuad(guiGraphics, client, state, world, pos, x, y, quad);
            }
        }

        // Text lines at 0.5x scale inside the 32x32 cell (drawCenteredScaled works in the scaled coordinates).
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(0.5f, 0.5f);

        long currentTime = world.getGameTime();
        int lineH = tr.lineHeight + 1;

        // A permanent stage does not erode further, so it shows no progress towards the next stage.
        String countLabel = BlockThresholds.isPermanentStage(state)
                ? "perm"
                : cellEntry != null
                ? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
                : "0.0/-";
        drawCenteredScaled(guiGraphics, tr, countLabel, x, y, 0, COUNT_COLOR);
        logHudState(cellIndex, cellName, pos, cellEntry, countLabel);

        String ageLabel = cellEntry != null
                ? "age:" + (currentTime - cellEntry.lastTouchedGameTime)
                : "age:-";
        drawCenteredScaled(guiGraphics, tr, ageLabel, x, y, lineH, AGE_COLOR);

        long timeout = resolveTimeout(state, cellEntry);
        String outLabel;
        if (timeout < 0) {
            outLabel = "out:-";
        } else if (BlockThresholds.isPermanentStage(state)) {
            outLabel = "out:perm";
        } else {
            boolean isolated = isIsolatedClient(world, pos);
            if (isolated) timeout /= 2;
            outLabel = "out:" + timeout + (isolated ? " I" : "");
        }
        drawCenteredScaled(guiGraphics, tr, outLabel, x, y, lineH * 2, OUT_COLOR);

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

    private static void drawCenteredScaled(GuiGraphics guiGraphics, Font tr,
                                           String text, int cellX, int cellY,
                                           int lineOffset, int color) {
        int textWidth = tr.width(text);
        int drawX = (cellX * 2) + (CELL * 2 - textWidth) / 2;
        int drawY = (cellY * 2) + 2 + lineOffset;
        guiGraphics.drawString(tr, text, drawX, drawY, color, true);
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

    private static void drawQuad(GuiGraphics guiGraphics, Minecraft client,
                                  BlockState state, ClientLevel world, BlockPos pos,
                                  int x, int y, BakedQuad quad) {
        TextureAtlasSprite sprite = quad.sprite();
        int color;
        if (quad.isTinted()) {
            int biomeColor = client.getBlockColors().getColor(state, world, pos, quad.tintIndex());
            color = 0xFF000000 | (biomeColor & 0x00FFFFFF);
        } else {
            color = 0xFFFFFFFF;
        }
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, CELL, CELL, color);
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
