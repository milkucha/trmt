package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
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
					server.execute(() -> handler.disconnect(Text.literal(
						"The Roads More Travelled (TRMT) client version is outdated (v" + clientVer + ")!\n" +
						"This server requires v" + serverVer + " or newer.\n" +
						"Please download the update to join this server."
					)));
				}
			});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("trmt")
						.then(CommandManager.literal("reloadconfig")
								.requires(src -> src.hasPermissionLevel(2))
								.executes(ctx -> {
									TRMTConfig.load();
									ErosionMapManager.getInstance().revertDisabledBlocksAllLoaded(ctx.getSource().getServer());
									ctx.getSource().sendFeedback(Text.literal("[TRMT] Config reloaded."), true);
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
