package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.DeErosionItemTriggerHandler;
import milkucha.trmt.erosion.DeErosionLogic;
import milkucha.trmt.erosion.DeErosionRule;
import milkucha.trmt.erosion.DeErosionTransformReloadListener;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.erosion.ErosionLogic;
import milkucha.trmt.erosion.ErosionTransformGraph;
import milkucha.trmt.erosion.ErosionTransformReloadListener;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import milkucha.trmt.network.VersionCheckPayload;
import milkucha.trmt.network.VersionResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.BlockItem;
import net.minecraft.resource.ResourceType;
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
import java.util.Optional;
import java.util.Set;

public class TRMT implements ModInitializer {
	public static final String MOD_ID = "trmt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final int DE_EROSION_SCAN_INTERVAL_TICKS = 200;
	private static int deErosionScanTicks = 0;

	@Override
	public void onInitialize() {
		TRMTConfig.load();
		TRMTEffects.register();
		TRMTPotions.register();
		TRMTBlocks.register();
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(ErosionTransformReloadListener.INSTANCE);
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(DeErosionTransformReloadListener.INSTANCE);

		PayloadTypeRegistry.configurationS2C().register(VersionCheckPayload.ID, VersionCheckPayload.CODEC);
		PayloadTypeRegistry.configurationC2S().register(VersionResponsePayload.ID, VersionResponsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncChunkPayload.ID, SyncChunkPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(UpdateStagePayload.ID, UpdateStagePayload.CODEC);

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
					context.networkHandler().disconnect(Text.translatable(
						"trmt.disconnect.outdated", clientVer, serverVer
					));
				}
			});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ErosionMapManager manager = ErosionMapManager.getInstance();
			manager.loadState(server);
			manager.migrateGrassEntries(server);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
				ErosionMapManager.getInstance().sendFullSyncToPlayer(handler.player);
				if (!ErosionLogic.areTransformRulesEnabled() && ErosionTransformGraph.latestReport().hasCycles()) {
					handler.player.sendMessage(Text.literal(
							"[TRMT] Erosion transform DAG contains a cycle. Erosion transforms are disabled; run /trmt erosion-dag for details."
					), false);
				}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			deErosionScanTicks = 0;
			ErosionMapManager.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			deErosionScanTicks++;
			if (deErosionScanTicks >= DE_EROSION_SCAN_INTERVAL_TICKS) {
				deErosionScanTicks = 0;
				ErosionMapManager.getInstance().tickNaturalDeErosion(server);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
				ErosionMapManager.getInstance().removeEntry(pos));

		// Prevent blocks from being placed above sunken eroded sand (stages 1–4) from any angle.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world instanceof ServerWorld serverWorld) {
				var pos = hitResult.getBlockPos();
				var state = world.getBlockState(pos);
				var stack = player.getStackInHand(hand);
				Optional<DeErosionRule.ItemTrigger> trigger = DeErosionLogic.getItemTrigger(state, stack.getItem(), "instant");
				if (trigger.isPresent()
						&& DeErosionLogic.tryItemDeErode(serverWorld, ErosionMapManager.getInstance(), pos, state, stack.getItem(), "instant")) {
					DeErosionItemTriggerHandler.apply(serverWorld, pos, player, stack, trigger.get());
					return ActionResult.SUCCESS;
				}
			}

			var placePos = hitResult.getBlockPos().offset(hitResult.getSide());
			var below = world.getBlockState(placePos.down());
			if (below.isOf(TRMTBlocks.ERODED_SAND)
					&& below.get(ErodedSandBlock.STAGE) > 0
					&& player.getStackInHand(hand).getItem() instanceof BlockItem) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
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
						.then(CommandManager.literal("erosion-dag")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									ErosionTransformGraph.Report report = ErosionTransformGraph.latestReport();
									if (report.paths().isEmpty() && report.cycles().isEmpty()) {
										ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] No erosion transform DAG is loaded."), false);
										return 1;
									}

									ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] Erosion transform DAG:"), false);
									for (String path : report.paths()) {
										ctx.getSource().sendFeedback(() -> Text.literal("  " + path), false);
									}
									if (!report.cycles().isEmpty()) {
										ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] Cycles:"), false);
										for (String cycle : report.cycles()) {
											ctx.getSource().sendFeedback(() -> Text.literal("  " + cycle), false);
										}
									}
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
										if (overworld != null && overworld.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null) {
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
