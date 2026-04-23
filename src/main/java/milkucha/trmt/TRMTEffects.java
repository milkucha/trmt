package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class TRMTEffects {

    public static final StatusEffect LIGHTNESS = Registry.register(
            Registries.STATUS_EFFECT,
            new Identifier("trmt", "lightness"),
            new LightnessEffect()
    );

    private TRMTEffects() {}

    public static void register() {}
}
