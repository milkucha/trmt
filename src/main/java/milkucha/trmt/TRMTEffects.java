package milkucha.trmt;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * Manages the registration of custom status effects.
 * NOTE: For 26.3, all effect definitions should ideally be moved to data components/JSON files.
 * This class remains as a placeholder for now, but its usage must be reviewed.
 */
public final class TRMTEffects {

    // Placeholder: If the effect is purely cosmetic or only used in recipes, it might not need
    // programmatic registration if the recipe JSON handles the application.
    // Typed as Holder<MobEffect> since that is what LivingEntity#hasEffect now expects.
    public static final Holder<MobEffect> LIGHTNESS = null; // Set to null as a placeholder for data-driven definition

    private TRMTEffects() {}

    /**
     * This method is kept empty/minimal because all effect definitions are expected
     * to be handled by JSON components in the future.
     */
    public static void register() {
        // No programmatic registration needed here for 26.3 compatibility.
    }
}
