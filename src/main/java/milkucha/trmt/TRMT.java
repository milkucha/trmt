package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.erosion.VanillaConversion;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TRMT implements ModInitializer {
	public static final String MOD_ID = "trmt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TRMTConfig.load();
		TRMTEffects.register();
		TRMTPotions.register();
		TRMTBlocks.register();
		// Load each dimension's erosion region files (the overworld also migrates the legacy file) while
		// that world is created, i.e. inside setupServer(), before runServer() starts its tick clock — doing
		// it on SERVER_STARTED counted the whole load as one long first tick ("Can't keep up!").
		ServerWorldEvents.LOAD.register((server, world) -> ErosionMapManager.loadState(world));
		// Untouched step progress expires (entryExpiryDays): checked when a chunk loads, at world load and on save.
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> ErosionMapManager.onChunkLoad(world, chunk.getPos()));
		// The legacy grass-entry migration places blocks, so it waits until the worlds are fully ready.
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				ErosionMapManager.migrateGrassEntries(server));
		// Per-chunk erosion data is sent when a player starts watching a chunk (ThreadedAnvilChunkStorageMixin),
		// but only to players whose client reports the debug HUD as on.
		ServerPlayNetworking.registerGlobalReceiver(TRMTPackets.HUD_STATE, (server, player, handler, buf, responseSender) -> {
			boolean enabled = buf.readBoolean();
			server.execute(() -> ErosionMapManager.setHudEnabled(player, enabled));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ErosionMapManager.setHudEnabled(handler.player, false));
		// Reset the erosion manager when the server stops so state does not bleed between sessions.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ErosionMapManager.reset());
		// /trmt convert-to-vanilla runs a few ms per tick; a server stop ends the run and releases its tickets.
		ServerTickEvents.END_SERVER_TICK.register(VanillaConversion::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> VanillaConversion.onServerStopping());
// Clear the erosion entry when any block is broken so a freshly placed block always starts from zero.
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
				ErosionMapManager.get(world).removeEntry(pos));

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
						// The root has no permission requirement but every subcommand needs level 2, so without
						// it /trmt is all a player sees (GH #43: singleplayer with cheats off). Say why.
						.executes(ctx -> {
							if (ctx.getSource().hasPermissionLevel(2)) {
								ctx.getSource().sendFeedback(() -> Text.literal(
										"[TRMT] Commands: /trmt reload-config, /trmt convert-to-vanilla [quick], /trmt eroded-chunks"), false);
							} else {
								ctx.getSource().sendFeedback(() -> Text.literal(
										"[TRMT] The /trmt commands need operator permission (cheats). In singleplayer, enable cheats "
										+ "when creating the world, or use Open to LAN with Allow Cheats: ON, then run the command again."), false);
							}
							return 1;
						})
						.then(CommandManager.literal("reload-config")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									if (TRMTConfig.load(true) == TRMTConfig.LoadResult.INVALID) {
										ctx.getSource().sendError(Text.literal("[TRMT] trmt.json is not valid JSON, so nothing was reloaded: the previous settings are still in use. "
												+ "Fix the file (the server log names the line and the problem) and run /trmt reload-config again."));
										return 0;
									}
									ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] Config reloaded."), true);
									return 1;
								}))
						// Converts every TRMT block in the world in the background (VanillaConversion, B3).
						// Default: scan every generated chunk. "quick": only chunks with stored erosion data.
						// "full" is kept as an alias of the default so older instructions still work.
						.then(CommandManager.literal("convert-to-vanilla")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> { VanillaConversion.preview(ctx.getSource(), true); return 1; })
								.then(CommandManager.literal("confirm")
										.executes(ctx -> { VanillaConversion.start(ctx.getSource(), true); return 1; }))
								.then(CommandManager.literal("quick")
										.executes(ctx -> { VanillaConversion.preview(ctx.getSource(), false); return 1; })
										.then(CommandManager.literal("confirm")
												.executes(ctx -> { VanillaConversion.start(ctx.getSource(), false); return 1; })))
								.then(CommandManager.literal("full")
										.executes(ctx -> { VanillaConversion.preview(ctx.getSource(), true); return 1; })
										.then(CommandManager.literal("confirm")
												.executes(ctx -> { VanillaConversion.start(ctx.getSource(), true); return 1; })))
								.then(CommandManager.literal("cancel")
										.executes(ctx -> { VanillaConversion.cancel(ctx.getSource()); return 1; })))
						.then(CommandManager.literal("eroded-chunks")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									// Each dimension has its own erosion data; report each one with its own loaded-chunk check.
									boolean any = false;
									for (ServerWorld world : ctx.getSource().getServer().getWorlds()) {
										Set<ChunkPos> allChunks = ErosionMapManager.get(world).getErodedChunkPositions();
										if (allChunks.isEmpty()) continue;
										any = true;
										String dim = world.getRegistryKey().getValue().toString();

										List<ChunkPos> unloaded = new ArrayList<>();
										int loadedCount = 0;
										for (ChunkPos cp : allChunks) {
											if (world.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null) {
												loadedCount++;
											} else {
												unloaded.add(cp);
											}
										}

										int total = allChunks.size();
										int unloadedCount = unloaded.size();
										int loadedFinal = loadedCount;
										ctx.getSource().sendFeedback(() -> Text.literal(
												"[TRMT] Eroded chunks in " + dim + ": " + total + " chunk(s) total — " + loadedFinal + " loaded, " + unloadedCount + " unloaded."), false);

										if (unloaded.isEmpty()) {
											ctx.getSource().sendFeedback(() -> Text.literal(
													"[TRMT] All eroded chunks in " + dim + " are currently loaded."), false);
										} else {
											LOGGER.info("[TRMT] Unloaded chunks with erosion data in {} ({}):", dim, unloadedCount);
											for (ChunkPos cp : unloaded) {
												LOGGER.info("[TRMT]   {} {}", cp.getStartX(), cp.getStartZ());
											}
											if (unloadedCount <= 20) {
												ctx.getSource().sendFeedback(() -> Text.literal(
														"[TRMT] Unloaded chunk coordinates in " + dim + ":"), false);
												for (ChunkPos cp : unloaded) {
													int bx = cp.getStartX(), bz = cp.getStartZ();
													ctx.getSource().sendFeedback(() -> Text.literal("  " + bx + " " + bz), false);
												}
											} else {
												ctx.getSource().sendFeedback(() -> Text.literal(
														"[TRMT] " + unloadedCount + " unloaded chunks in " + dim + " — full list printed to server console."), false);
											}
										}
									}
									if (!any) {
										ctx.getSource().sendFeedback(() -> Text.literal(
												"[TRMT] Eroded chunks: 0 chunk(s) total."), false);
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
