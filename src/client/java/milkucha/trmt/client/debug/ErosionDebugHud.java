package milkucha.trmt.client.debug;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Debug HUD: erosion counts in a compass cross (text-only for 1.21 port stability). */
public final class ErosionDebugHud {

	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int COUNT_COLOR = 0xFFFF55;
	private static final int AGE_COLOR = 0x55FFFF;
	private static final int OUT_COLOR = 0xFF5555;
	private static final int MARGIN = 4;
	private static final int CELL = 32;

	private ErosionDebugHud() {}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(ErosionDebugHud::onRenderGui);
	}

	private static void onRenderGui(RenderGuiEvent.Post event) {
		render(event.getGuiGraphics());
	}

	private static void render(GuiGraphics graphics) {
		if (!TRMTClientConfig.get().debugHud) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;

		ClientLevel world = (ClientLevel) client.level;
		BlockPos center = client.player.blockPosition().below();

		Font tr = client.font;
		int lineHeight = tr.lineHeight + 1;

		int totalHeight = 3 * CELL + 4 + lineHeight;
		int x0 = MARGIN;
		int y0 = Minecraft.getInstance().getWindow().getGuiScaledHeight() - MARGIN - totalHeight;

		renderCell(graphics, world, center.north(), x0 + CELL, y0, tr);
		renderCell(graphics, world, center.west(), x0, y0 + CELL, tr);
		renderCell(graphics, world, center, x0 + CELL, y0 + CELL, tr);
		renderCell(graphics, world, center.east(), x0 + 2 * CELL, y0 + CELL, tr);
		renderCell(graphics, world, center.south(), x0 + CELL, y0 + 2 * CELL, tr);

		String coords = center.getX() + " " + center.getY() + " " + center.getZ();
		graphics.drawString(tr, coords, x0, y0 + 3 * CELL + 4, TEXT_COLOR, true);
	}

	private static void renderCell(GuiGraphics graphics, ClientLevel world, BlockPos pos, int x, int y, Font tr) {
		Minecraft client = Minecraft.getInstance();

		ClientErosionCache.Entry cellEntry = getEntry(pos);
		long currentTime = client.level != null ? client.level.getGameTime() : 0L;
		int lineH = tr.lineHeight + 1;

		BlockState state = world.getBlockState(pos);

		String countLabel = cellEntry != null
			? String.format("%.1f/%.1f", cellEntry.walkedOnCount, cellEntry.threshold)
			: "0.0/-";
		drawCenteredScaled(graphics, tr, countLabel, x, y, 0, COUNT_COLOR);

		String ageLabel = cellEntry != null
			? "age:" + (currentTime - cellEntry.lastTouchedGameTime)
			: "age:-";
		drawCenteredScaled(graphics, tr, ageLabel, x, y, lineH, AGE_COLOR);

		long timeout = resolveTimeout(state, cellEntry);
		String outLabel;
		if (timeout < 0) {
			outLabel = "out:-";
		} else {
			boolean isolated = isIsolatedClient(world, pos);
			if (isolated) timeout /= 2;
			outLabel = "out:" + timeout + (isolated ? " I" : "");
		}
		drawCenteredScaled(graphics, tr, outLabel, x, y, lineH * 2, OUT_COLOR);
	}

	private static void drawCenteredScaled(GuiGraphics graphics, Font tr, String text, int cellX, int cellY,
	                                       int lineOffset, int color) {
		int textWidth = tr.width(text);
		int drawX = (cellX * 2) + (CELL * 2 - textWidth) / 2;
		int drawY = (cellY * 2) + 2 + lineOffset;
		graphics.drawString(tr, text, drawX, drawY, color, true);
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

	private static ClientErosionCache.Entry getEntry(BlockPos pos) {
		return ClientErosionCache.getInstance().getEntry(pos);
	}
}
