package milkucha.trmt.client;

import milkucha.trmt.TRMT;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

/**
 * Sodium 0.5.x ignores Fabric Rendering API (FRAPI) models unless Indium is installed: the
 * worn-grass overlay then does not render and the sunken sand floor gets ambient occlusion back.
 * If Sodium is loaded without Indium, log one warning at client start and show it once in chat on
 * the first world join of the session. Only Sodium 0.5.x is affected (1.21/1.21.1: 0.5.11); Sodium
 * 0.6+ implements FRAPI itself, so the warning is gated on the Sodium version.
 */
public final class IndiumWarning {

    private static final String KEY = "trmt.warning.sodium_without_indium";
    private static boolean shown = false;

    private IndiumWarning() {}

    public static void register() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("indium")) return;
        Optional<ModContainer> sodium = loader.getModContainer("sodium");
        if (sodium.isEmpty()) return;
        // Only Sodium 0.5.x (1.21/1.21.1: 0.5.11) lacks FRAPI; 0.6+ implements it itself.
        if (!(sodium.get().getMetadata().getVersion() instanceof SemanticVersion v)
                || v.getVersionComponent(0) != 0 || v.getVersionComponent(1) >= 6) return;
        TRMT.LOGGER.warn("[TRMT] Sodium is installed without Indium: worn paths won't render correctly. Install Indium.");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (shown) return;
            shown = true;
            client.inGameHud.getChatHud().addMessage(Text.translatable(KEY).formatted(Formatting.YELLOW));
        }));
    }
}
