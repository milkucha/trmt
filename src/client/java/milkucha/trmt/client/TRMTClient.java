package milkucha.trmt.client;

import milkucha.trmt.client.debug.ErosionDebugHud;
import net.fabricmc.api.ClientModInitializer;

public class TRMTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ErosionDebugHud.register();
	}
}
