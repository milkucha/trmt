package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Sodium 0.5.x ignores Fabric Rendering API (FRAPI) models unless Indium is installed: the
 * worn-grass overlay then does not render and the sunken sand floor gets ambient occlusion back.
 * If Sodium is loaded without Indium, log one warning at client start and show it once in chat on
 * the first world join of the session. (Sodium 0.6+ implements FRAPI itself; that is 1.21+ only.)
 */
public final class IndiumWarning {

    private static final String KEY = "trmt.warning.sodium_without_indium";
    private static boolean shown = false;

    private IndiumWarning() {}

    public static void register() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("sodium") || loader.isModLoaded("indium")) return;
        TRMT.LOGGER.warn("[TRMT] Sodium is installed without Indium: worn paths won't render correctly. Install Indium.");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (shown) return;
            shown = true;
            client.inGameHud.getChatHud().addMessage(Text.translatable(KEY).formatted(Formatting.YELLOW));
        }));
    }
}
