package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.debug.ErosionDebugHud;
import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.client.render.ErodedGrassBlockModels;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.TRMTPackets;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TRMTClientConfig.load();

		// Respond to the server's login version query with our own version.
		ClientLoginNetworking.registerGlobalReceiver(TRMTPackets.VERSION_CHECK, (client, handler, buf, listenerAdder) -> {
			buf.readUtf(32767); // consume server version — server makes the comparison decision
			FriendlyByteBuf response = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
			response.writeUtf(
				FabricLoader.getInstance().getModContainer(TRMT.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString())
					.orElse("0.0.0")
			);
			return CompletableFuture.completedFuture(response);
		});

		// Suppress client-side prediction of block placement above sunken eroded sand (mirrors server rule).
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

		ErodedGrassBlockModels.register();
		// Vanilla now auto-detects the chunk render layer (SOLID/CUTOUT/TRANSLUCENT) per-block from
		// its texture's alpha channel, so eroded grass/sand no longer need explicit render-layer
		// registration (the old BlockRenderLayerMap API was removed in favor of this).
		// Apply biome grass tint (same as vanilla grass_block) so eroded grass is not gray.
		BlockColorRegistry.register(List.of(BlockTintSources.grassBlock()), TRMTBlocks.ERODED_GRASS_BLOCK);
		ErosionDebugHud.register();

		// Full chunk sync received on join.
		ClientPlayNetworking.registerGlobalReceiver(SyncChunkPayload.TYPE, (payload, context) -> {
			Map<net.minecraft.core.BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(payload.entries().size());
			for (SyncChunkPayload.Entry e : payload.entries()) {
				chunkEntries.put(e.pos(), new ClientErosionCache.Entry(e.stage(), e.walkedOnCount(), e.threshold(), e.lastTouchedGameTime()));
			}
			ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
			context.client().execute(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
		});

		// Single-block stage update (advance or reset).
		ClientPlayNetworking.registerGlobalReceiver(UpdateStagePayload.TYPE, (payload, context) ->
			context.client().execute(() ->
				ClientErosionCache.getInstance().setEntry(payload.pos(), payload.stage(), payload.walkedOnCount(), payload.threshold(), payload.lastTouchedGameTime())
			)
		);

		// Clear cached stages when disconnecting so stale data never leaks into the next session.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ClientErosionCache.getInstance().clear());
	}
}
