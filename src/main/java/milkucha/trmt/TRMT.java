package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import milkucha.trmt.network.VersionCheckPayload;
import milkucha.trmt.network.VersionResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
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
					context.networkHandler().disconnect(Text.literal(
						"The Roads More Travelled (TRMT) client version is outdated (v" + clientVer + ")!\n" +
						"This server requires v" + serverVer + " or newer.\n" +
						"Please download the update to join this server."
					));
				}
			});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ErosionMapManager manager = ErosionMapManager.getInstance();
			manager.loadState(server);
			manager.migrateGrassEntries(server);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ErosionMapManager.getInstance().sendFullSyncToPlayer(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ErosionMapManager.reset());
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
				ErosionMapManager.getInstance().removeEntry(pos));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("trmt")
						.then(CommandManager.literal("reloadconfig")
								.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
								.executes(ctx -> {
									TRMTConfig.load();
									ErosionMapManager.getInstance().revertDisabledBlocksAllLoaded(ctx.getSource().getServer());
									ctx.getSource().sendFeedback(() -> Text.literal("[TRMT] Config reloaded."), true);
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
