package milkucha.trmt;

import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TRMT.MOD_ID)
public class TRMT {

	public static final String MOD_ID = "trmt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public TRMT(IEventBus modEventBus) {
		TRMTBlocks.register(modEventBus);
		TRMTEffects.register(modEventBus);
		TRMTPotions.register(modEventBus);

		modEventBus.addListener(TRMT::registerDedicatedServerPayloads);

		NeoForge.EVENT_BUS.addListener(TRMTPotions::registerBrewingRecipes);

		NeoForge.EVENT_BUS.register(TRMTServerEvents.class);
	}

	private static void registerDedicatedServerPayloads(RegisterPayloadHandlersEvent event) {
		if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) return;
		PayloadRegistrar reg = event.registrar("1");
		reg.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.STREAM_CODEC, TRMT::discardSyncChunk);
		reg.playToClient(UpdateStagePayload.TYPE, UpdateStagePayload.STREAM_CODEC, TRMT::discardUpdateStage);
	}

	private static void discardSyncChunk(SyncChunkPayload payload, IPayloadContext ctx) {
	}

	private static void discardUpdateStage(UpdateStagePayload payload, IPayloadContext ctx) {
	}
}
