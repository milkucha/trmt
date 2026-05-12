package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.BlockItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TRMT implements ModInitializer {
	public static final String MOD_ID = "trmt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TRMTConfig.load();
		TRMTEffects.register();
		TRMTPotions.register();
		TRMTBlocks.register();
		// Load (or create) persistent erosion state once the server and its worlds are ready.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ErosionMapManager manager = ErosionMapManager.getInstance();
			manager.loadState(server);
			manager.migrateGrassEntries(server);
		});
		// Send full erosion data to each player when they join (covers existing erosion they'd otherwise miss).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ErosionMapManager.getInstance().sendFullSyncToPlayer(handler.player));
		// Reset the erosion manager when the server stops so state does not bleed between sessions.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ErosionMapManager.reset());
// Clear the erosion entry when any block is broken so a freshly placed block always starts from zero.
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
				ErosionMapManager.getInstance().removeEntry(pos));

		// Prevent blocks from being placed above sunken eroded sand (stages 1-4) from any angle.
		// Stage 0 is full-height and has no visual glitch, so it is not restricted.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			var placePos = hitResult.getBlockPos().offset(hitResult.getSide());
			var below = world.getBlockState(placePos.down());
			if (below.isOf(TRMTBlocks.ERODED_SAND)
					&& below.get(ErodedSandBlock.STAGE) > 0
					&& player.getStackInHand(hand).getItem() instanceof BlockItem) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		// During login, send server version; disconnect client if its version is older.
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeString(getModVersion());
			sender.sendPacket(TRMTPackets.VERSION_CHECK, buf);
		});
		ServerLoginNetworking.registerGlobalReceiver(TRMTPackets.VERSION_CHECK,
			(server, handler, understood, buf, synchronizer, responseSender) -> {
				if (!understood) return;
				String clientVer = buf.readString(32767);
				String serverVer = getModVersion();
				if (isClientOutdated(clientVer, serverVer)) {
					server.execute(() -> handler.disconnect(
						Text.translatable("trmt.disconnect.outdated", clientVer, serverVer)
					));
				}
			});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("trmt")
						.then(CommandManager.literal("reloadconfig")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									TRMTConfig.load();
									ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] Config reloaded."), true);
									return 1;
								}))
						.then(CommandManager.literal("convert-to-vanilla")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] WARNING: This will convert all existing eroded blocks in all currently loaded chunks to their vanilla counterparts. This cannot be undone. ")
											.append(Text.literal("[Click to confirm]")
													.styled(s -> s
															.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trmt convert-to-vanilla confirm"))
															.withFormatting(Formatting.YELLOW)
															.withUnderline(true))), false);
									return 1;
								})
								.then(CommandManager.literal("confirm")
										.executes(ctx -> {
											ErosionMapManager.getInstance().convertAllErodedToVanilla(ctx.getSource().getServer());
											ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] All eroded blocks in loaded chunks converted to their vanilla counterparts."), true);
											return 1;
										})))
						.then(CommandManager.literal("eroded-chunks")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									ServerWorld overworld = ctx.getSource().getServer().getWorld(World.OVERWORLD);
									Set<ChunkPos> allChunks = ErosionMapManager.getInstance().getErodedChunkPositions();

									List<ChunkPos> unloaded = new ArrayList<>();
									int loadedCount = 0;
									for (ChunkPos cp : allChunks) {
										if (overworld.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null) {
											loadedCount++;
										} else {
											unloaded.add(cp);
										}
									}

									int total = allChunks.size();
									int unloadedCount = unloaded.size();
									int loadedFinal = loadedCount;
									ctx.getSource().sendFeedback(() -> Text.literal(
											"[TRMT] Eroded chunks: " + total + " chunk(s) total — " + loadedFinal + " loaded, " + unloadedCount + " unloaded."), false);

									if (unloaded.isEmpty()) {
										ctx.getSource().sendFeedback(() -> Text.literal(
												"[TRMT] All eroded chunks are currently loaded."), false);
									} else {
										LOGGER.info("[TRMT] Unloaded chunks with erosion data ({}):", unloadedCount);
										for (ChunkPos cp : unloaded) {
											LOGGER.info("[TRMT]   {} {}", cp.getStartX(), cp.getStartZ());
										}
										if (unloadedCount <= 20) {
											ctx.getSource().sendFeedback(() -> Text.literal(
													"[TRMT] Unloaded chunk coordinates:"), false);
											for (ChunkPos cp : unloaded) {
												int bx = cp.getStartX(), bz = cp.getStartZ();
												ctx.getSource().sendFeedback(() -> Text.literal("  " + bx + " " + bz), false);
											}
										} else {
											ctx.getSource().sendFeedback(() -> Text.literal(
													"[TRMT] " + unloadedCount + " unloaded chunks — full list printed to server console."), false);
										}
									}
									return 1;
								}))));

		LOGGER.info("[TRMT] Initialized.");
	}

	private static String getModVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("0.0.0");
	}

	private static boolean isClientOutdated(String clientVer, String serverVer) {
		int[] cv = parseVersionParts(clientVer);
		int[] sv = parseVersionParts(serverVer);
		for (int i = 0; i < Math.max(cv.length, sv.length); i++) {
			int c = i < cv.length ? cv[i] : 0;
			int s = i < sv.length ? sv[i] : 0;
			if (c < s) return true;
			if (c > s) return false;
		}
		return false;
	}

	private static int[] parseVersionParts(String ver) {
		String[] parts = ver.split("-")[0].split("\\.");
		int[] result = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
		}
		return result;
	}
}
