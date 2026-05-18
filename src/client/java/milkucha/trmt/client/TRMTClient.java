package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.client.network.ClientErosionCache;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TRMT.MOD_ID, value = Dist.CLIENT)
public final class TRMTClient {

	private TRMTClient() {}

	@SubscribeEvent
	public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientErosionCache.getInstance().clear();
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!event.getLevel().isClientSide()) return;
		if (event.getItemStack().getItem() instanceof BlockItem) {
			var placePos = event.getPos().relative(event.getFace());
			var below = event.getLevel().getBlockState(placePos.below());
			if (below.is(TRMTBlocks.ERODED_SAND.get())
				&& below.getValue(ErodedSandBlock.STAGE) > 0) {
				event.setCancellationResult(InteractionResult.FAIL);
				event.setCanceled(true);
			}
		}
	}
}
