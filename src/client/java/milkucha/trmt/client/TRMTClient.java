package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.network.TRMTPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import milkucha.trmt.block.ErodedSandBlock;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TRMTClientConfig.load();

		// Respond to the server's login version query with our own version.
		ClientLoginNetworking.registerGlobalReceiver(TRMTPackets.VERSION_CHECK, (client, handler, buf, listenerAdder) -> {
			buf.readString(32767); // consume server version — server makes the comparison decision
			PacketByteBuf response = PacketByteBufs.create();
			response.writeString(
				FabricLoader.getInstance().getModContainer(TRMT.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString())
					.orElse("0.0.0")
			);
			return CompletableFuture.completedFuture(response);
		});

		// Suppress client-side prediction of block placement above sunken eroded sand (mirrors server rule).
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

		ErodedGrassBlockModels.register();
		// Eroded grass models have transparent overlay pixels — must use CUTOUT_MIPPED.
		BlockRenderLayerMap.INSTANCE.putBlock(TRMTBlocks.ERODED_GRASS_BLOCK, RenderLayer.getCutoutMipped());
		// Eroded sand is nonOpaque(); without an explicit render layer it defaults to SOLID,
		// which causes the damage-overlay renderer to produce white pixels when breaking.
		BlockRenderLayerMap.INSTANCE.putBlock(TRMTBlocks.ERODED_SAND, RenderLayer.getCutoutMipped());
		// Apply biome grass tint (same as vanilla grass_block) so eroded grass is not gray.
		ColorProviderRegistry.BLOCK.register(
				(state, world, pos, tintIndex) -> world != null && pos != null
						? BiomeColors.getGrassColor(world, pos)
						: 0x79C05A, // default plains grass color fallback (item rendering)
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
		ClientPlayNetworking.registerGlobalReceiver(TRMTPackets.SYNC_CHUNK, (client, handler, buf, responseSender) -> {
			int chunkX = buf.readInt();
			int chunkZ = buf.readInt();
			int count  = buf.readInt();
			Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(count);
			for (int i = 0; i < count; i++) {
				BlockPos pos                = buf.readBlockPos();
				int      stage              = buf.readInt();
				float    walkedOnCount      = buf.readFloat();
				float    threshold          = buf.readFloat();
				long     lastTouchedGameTime = buf.readLong();
				chunkEntries.put(pos, new ClientErosionCache.Entry(stage, walkedOnCount, threshold, lastTouchedGameTime));
			}
			ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
			client.execute(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
		});

		// Single-block stage update (advance or reset).
		ClientPlayNetworking.registerGlobalReceiver(TRMTPackets.UPDATE_STAGE, (client, handler, buf, responseSender) -> {
			BlockPos pos                = buf.readBlockPos();
			int      stage              = buf.readInt();
			float    walkedOnCount      = buf.readFloat();
			float    threshold          = buf.readFloat();
			long     lastTouchedGameTime = buf.readLong();
			client.execute(() ->
				ClientErosionCache.getInstance().setEntry(pos, stage, walkedOnCount, threshold, lastTouchedGameTime)
			);
		});

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
	}

	private static int setHud(FabricClientCommandSource source, boolean enabled) {
		ErosionDebugHud.setEnabled(enabled);
		TRMTClientConfig.save();
		source.sendFeedback(Text.literal("[TRMT] Debug HUD " + (enabled ? "on." : "off.")));
		return 1;
	}
}
