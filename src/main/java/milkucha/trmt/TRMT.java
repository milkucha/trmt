package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TRMT implements ModInitializer {
	public static final String MOD_ID = "trmt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Load (or create) persistent erosion state once the server and its worlds are ready.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> ErosionMapManager.getInstance().loadState(server));
		// Reset the erosion manager when the server stops so state does not bleed between sessions.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ErosionMapManager.reset());

		LOGGER.info("[TRMT] Initialized.");
	}
}
