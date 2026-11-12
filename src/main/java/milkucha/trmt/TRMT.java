package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.erosion.VanillaConversion;
import milkucha.trmt.network.HudStatePayload;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import milkucha.trmt.network.VersionCheckPayload;
import milkucha.trmt.network.VersionResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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

		PayloadTypeRegistry.clientboundConfiguration().register(VersionCheckPayload.ID, VersionCheckPayload.CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(VersionResponsePayload.ID, VersionResponsePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncChunkPayload.ID, SyncChunkPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateStagePayload.ID, UpdateStagePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HudStatePayload.ID, HudStatePayload.CODEC);

		// During configuration, send our version; client responds with its own version.
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (ServerConfigurationNetworking.canSend(handler, VersionCheckPayload.ID)) {
				ServerConfigurationNetworking.send(handler, new VersionCheckPayload(getModVersion()));
			}
		});
		ServerConfigurationNetworking.registerGlobalReceiver(VersionResponsePayload.ID,
			(payload, context) -> {
				String clientVer = payload.version();
				String serverVer = getModVersion();
				if (isClientOutdated(clientVer, serverVer)) {
					context.packetListener().disconnect(Component.translatable(
						"trmt.disconnect.outdated", clientVer, serverVer
					));
				}
			});

		// Load each dimension's erosion region files (the overworld also migrates the legacy file) while
		// that world is created, i.e. inside initServer() (createLevels), before runServer() starts its tick
		// clock — doing it on SERVER_STARTED counted the whole load as one long first tick ("Can't keep up!").
		ServerLevelEvents.LOAD.register((server, world) -> ErosionMapManager.loadState(world));
		// The legacy grass-entry migration places blocks, so it waits until the worlds are fully ready.
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				ErosionMapManager.migrateGrassEntries(server));
		// Per-chunk erosion data is sent when a chunk is sent to a player (ChunkDataSenderMixin),
		// but only to players whose client reports the debug HUD as on.
		// Fabric API 0.145 runs play payload handlers on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(HudStatePayload.ID, (payload, context) ->
				ErosionMapManager.setHudEnabled(context.player(), payload.enabled()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ErosionMapManager.setHudEnabled(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ErosionMapManager.reset());
		// /trmt convert-to-vanilla runs a few ms per tick; a server stop ends the run and releases its tickets.
		ServerTickEvents.END_SERVER_TICK.register(VanillaConversion::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> VanillaConversion.onServerStopping());
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
				ErosionMapManager.get(world).removeEntry(pos));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("trmt")
						// The root has no permission requirement but every subcommand needs the gamemaster permission, so
						// without it /trmt is all a player sees (GH #43: singleplayer with cheats off). Say why.
						.executes(ctx -> {
							if (ctx.getSource().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
								ctx.getSource().sendSuccess(() -> Component.literal(
										"[TRMT] Commands: /trmt reloadconfig, /trmt convert-to-vanilla [quick], /trmt eroded-chunks"), false);
							} else {
								ctx.getSource().sendSuccess(() -> Component.literal(
										"[TRMT] The /trmt commands need operator permission (cheats). In singleplayer, enable cheats "
										+ "when creating the world, or use Open to LAN with Allow Cheats: ON, then run the command again."), false);
							}
							return 1;
						})
						.then(Commands.literal("reloadconfig")
								.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ctx -> {
									if (TRMTConfig.load(true) == TRMTConfig.LoadResult.INVALID) {
										ctx.getSource().sendFailure(Component.literal("[TRMT] trmt.json is not valid JSON, so nothing was reloaded: the previous settings are still in use. "
												+ "Fix the file (the server log names the line and the problem) and run /trmt reloadconfig again."));
										return 0;
									}
									ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
									return 1;
								}))
						// Converts every TRMT block in the world in the background (VanillaConversion, B3).
						// Default: scan every generated chunk. "quick": only chunks with stored erosion data.
						// "full" is kept as an alias of the default so older instructions still work.
						.then(Commands.literal("convert-to-vanilla")
								.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ctx -> { VanillaConversion.preview(ctx.getSource(), true); return 1; })
								.then(Commands.literal("confirm")
										.executes(ctx -> { VanillaConversion.start(ctx.getSource(), true); return 1; }))
								.then(Commands.literal("quick")
										.executes(ctx -> { VanillaConversion.preview(ctx.getSource(), false); return 1; })
										.then(Commands.literal("confirm")
												.executes(ctx -> { VanillaConversion.start(ctx.getSource(), false); return 1; })))
								.then(Commands.literal("full")
										.executes(ctx -> { VanillaConversion.preview(ctx.getSource(), true); return 1; })
										.then(Commands.literal("confirm")
												.executes(ctx -> { VanillaConversion.start(ctx.getSource(), true); return 1; })))
								.then(Commands.literal("cancel")
										.executes(ctx -> { VanillaConversion.cancel(ctx.getSource()); return 1; })))
						.then(Commands.literal("eroded-chunks")
								.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ctx -> {
									// Each dimension has its own erosion data; report each one with its own loaded-chunk check.
									boolean any = false;
									for (ServerLevel world : ctx.getSource().getServer().getAllLevels()) {
										Set<ChunkPos> allChunks = ErosionMapManager.get(world).getErodedChunkPositions();
										if (allChunks.isEmpty()) continue;
										any = true;
										String dim = world.dimension().identifier().toString();

										List<ChunkPos> unloaded = new ArrayList<>();
										int loadedCount = 0;
										for (ChunkPos cp : allChunks) {
											if (world.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false) != null) {
												loadedCount++;
											} else {
												unloaded.add(cp);
											}
										}

										int total = allChunks.size();
										int unloadedCount = unloaded.size();
										int loadedFinal = loadedCount;
										ctx.getSource().sendSuccess(() -> Component.literal(
												"[TRMT] Eroded chunks in " + dim + ": " + total + " chunk(s) total — " + loadedFinal + " loaded, " + unloadedCount + " unloaded."), false);

										if (unloaded.isEmpty()) {
											ctx.getSource().sendSuccess(() -> Component.literal(
													"[TRMT] All eroded chunks in " + dim + " are currently loaded."), false);
										} else {
											LOGGER.info("[TRMT] Unloaded chunks with erosion data in {} ({}):", dim, unloadedCount);
											for (ChunkPos cp : unloaded) {
												LOGGER.info("[TRMT]   {} {}", cp.getMinBlockX(), cp.getMinBlockZ());
											}
											if (unloadedCount <= 20) {
												ctx.getSource().sendSuccess(() -> Component.literal(
														"[TRMT] Unloaded chunk coordinates in " + dim + ":"), false);
												for (ChunkPos cp : unloaded) {
													int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
													ctx.getSource().sendSuccess(() -> Component.literal("  " + bx + " " + bz), false);
												}
											} else {
												ctx.getSource().sendSuccess(() -> Component.literal(
														"[TRMT] " + unloadedCount + " unloaded chunks in " + dim + " — full list printed to server console."), false);
											}
										}
									}
									if (!any) {
										ctx.getSource().sendSuccess(() -> Component.literal(
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
