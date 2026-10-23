package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import milkucha.trmt.network.VersionCheckPayload;
import milkucha.trmt.network.VersionResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.Map;

public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TRMTClientConfig.load();

		// Respond to the server's configuration-phase version query with our own version.
		ClientConfigurationNetworking.registerGlobalReceiver(VersionCheckPayload.ID, (payload, context) -> {
			String myVersion = FabricLoader.getInstance().getModContainer(TRMT.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
			context.responseSender().sendPacket(new VersionResponsePayload(myVersion));
		});

		ErodedGrassBlockModels.register();
		ColorProviderRegistry.BLOCK.register(
				(state, world, pos, tintIndex) -> world != null && pos != null
						? BiomeColors.getAverageGrassColor(world, pos)
						: 0x79C05A,
				TRMTBlocks.ERODED_GRASS_BLOCK
		);
		ErosionDebugHud.register();

		// /trmt-hud: toggles the client-side debug HUD, saved to trmt-client.json.
		// Separate root from the server's /trmt: Fabric runs a client command whenever its root matches and
		// only forwards unknown roots to the server, so a client /trmt would swallow /trmt convert-to-vanilla etc.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("trmt-hud")
						.executes(ctx -> setHud(ctx.getSource(), !TRMTClientConfig.get().debugHud))));

		// Full erosion data for one chunk, sent by the server right after the chunk itself.
		ClientPlayNetworking.registerGlobalReceiver(SyncChunkPayload.ID, (payload, context) -> {
			Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(payload.entries().size());
			for (SyncChunkPayload.Entry e : payload.entries()) {
				chunkEntries.put(e.pos(), new ClientErosionCache.Entry(e.stage(), e.walkedOnCount(), e.threshold(), e.lastTouchedGameTime()));
			}
			ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
			context.client().execute(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
		});

		// Single-block stage update (advance or reset).
		ClientPlayNetworking.registerGlobalReceiver(UpdateStagePayload.ID, (payload, context) ->
			context.client().execute(() ->
				ClientErosionCache.getInstance().setEntry(payload.pos(), payload.stage(), payload.walkedOnCount(), payload.threshold(), payload.lastTouchedGameTime())
			)
		);

		// Tell the server the HUD is on so it starts sending erosion data (nothing is sent while it is off).
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (TRMTClientConfig.get().debugHud) ErosionDebugHud.sendHudState(sender, true);
		});

		// Drop a chunk's cached entries whenever it (re)loads or unloads, so re-watching a chunk never shows
		// stale data. The server only sends SYNC_CHUNK for chunks that have data, right after the chunk packet.
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
				ClientErosionCache.getInstance().clearChunk(chunk.getPos()));
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
				ClientErosionCache.getInstance().clearChunk(chunk.getPos()));

		// Clear cached stages when disconnecting so stale data never leaks into the next session.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ClientErosionCache.getInstance().clear());

		// Suppress client-side block-placement prediction above sunken eroded sand (stages 1–4).
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			var placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
			var below = world.getBlockState(placePos.below());
			if (below.is(TRMTBlocks.ERODED_SAND)
					&& below.getValue(ErodedSandBlock.STAGE) > 0
					&& player.getItemInHand(hand).getItem() instanceof BlockItem) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	private static int setHud(FabricClientCommandSource source, boolean enabled) {
		ErosionDebugHud.setEnabled(enabled);
		TRMTClientConfig.save();
		source.sendFeedback(Component.literal("[TRMT] Debug HUD " + (enabled ? "on." : "off.")));
		return 1;
	}
}
