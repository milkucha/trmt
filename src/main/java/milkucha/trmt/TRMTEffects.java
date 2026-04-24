package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class TRMTEffects {

    public static final StatusEffect LIGHTNESS = Registry.register(
            Registries.STATUS_EFFECT,
            Identifier.of("trmt", "lightness"),
            new LightnessEffect()
    );

    public static RegistryEntry<StatusEffect> LIGHTNESS_ENTRY;

    private TRMTEffects() {}

    public static void register() {
        LIGHTNESS_ENTRY = Registries.STATUS_EFFECT.entryOf(
                RegistryKey.of(RegistryKeys.STATUS_EFFECT, Identifier.of("trmt", "lightness"))
        );
    }
}
