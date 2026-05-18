package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TRMTServerEvents {

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		ErosionMapManager.getInstance().loadState(event.getServer());
		ErosionMapManager.getInstance().migrateGrassEntries(event.getServer());
		TRMT.LOGGER.info("[TRMT] Initialized.");
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		ErosionMapManager.reset();
	}

	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer sp)) return;
		ErosionMapManager.getInstance().sendFullSyncToPlayer(sp);
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		var d = event.getDispatcher();
		d.register(Commands.literal("trmt")
			.then(Commands.literal("reloadconfig")
				.requires(s -> s.hasPermission(2))
				.executes(ctx -> {
					TRMTConfig.load();
					ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
					return 1;
				}))
			.then(Commands.literal("convert-to-vanilla")
				.requires(s -> s.hasPermission(2))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] WARNING: This will convert all existing eroded blocks in all currently loaded chunks to their vanilla counterparts. This cannot be undone. ")
						.append(Component.literal("[Click to confirm]")
							.withStyle(Style.EMPTY
								.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trmt convert-to-vanilla confirm"))
								.withColor(0xFFFF55)
								.withUnderlined(true))), false);
					return 1;
				})
				.then(Commands.literal("confirm")
					.requires(s -> s.hasPermission(2))
					.executes(ctx -> {
						ErosionMapManager.getInstance().convertAllErodedToVanilla(ctx.getSource().getServer());
						ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] All eroded blocks in loaded chunks converted to their vanilla counterparts."), true);
						return 1;
					})))
			.then(Commands.literal("eroded-chunks")
				.requires(s -> s.hasPermission(2))
				.executes(ctx -> {
					ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
					if (overworld == null) {
						ctx.getSource().sendFailure(Component.literal("[TRMT] No overworld."));
						return 0;
					}
					Set<ChunkPos> allChunks = ErosionMapManager.getInstance().getErodedChunkPositions();
					List<ChunkPos> unloaded = new ArrayList<>();
					int loadedCount = 0;
					for (ChunkPos cp : allChunks) {
						if (overworld.getChunkSource().getChunkNow(cp.x, cp.z) != null) {
							loadedCount++;
						} else {
							unloaded.add(cp);
						}
					}
					int total = allChunks.size();
					int unloadedCount = unloaded.size();
					int loadedFinal = loadedCount;
					ctx.getSource().sendSuccess(() -> Component.literal(
						"[TRMT] Eroded chunks: " + total + " chunk(s) total — " + loadedFinal + " loaded, " + unloadedCount + " unloaded."), false);
					if (unloaded.isEmpty()) {
						ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] All eroded chunks are currently loaded."), false);
					} else {
						TRMT.LOGGER.info("[TRMT] Unloaded chunks with erosion data ({}):", unloadedCount);
						for (ChunkPos cp : unloaded) {
							TRMT.LOGGER.info("[TRMT]   {} {}", cp.getMinBlockX(), cp.getMinBlockZ());
						}
						if (unloadedCount <= 20) {
							ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Unloaded chunk coordinates:"), false);
							for (ChunkPos cp : unloaded) {
								int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
								ctx.getSource().sendSuccess(() -> Component.literal("  " + bx + " " + bz), false);
							}
						} else {
							ctx.getSource().sendSuccess(() -> Component.literal(
								"[TRMT] " + unloadedCount + " unloaded chunks — full list printed to server console."), false);
						}
					}
					return 1;
				})));
	}

	/** Prevent placing blocks above sunken eroded sand (stages 1–4). */
	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().isClientSide()) return;
		if (event.getItemStack().getItem() instanceof BlockItem) {
			BlockPos placePos = event.getPos().relative(event.getFace());
			var below = event.getLevel().getBlockState(placePos.below());
			if (below.getBlock() == TRMTBlocks.ERODED_SAND.get()
				&& below.getValue(ErodedSandBlock.STAGE) > 0) {
				event.setCancellationResult(InteractionResult.FAIL);
				event.setCanceled(true);
			}
		}
	}
}
