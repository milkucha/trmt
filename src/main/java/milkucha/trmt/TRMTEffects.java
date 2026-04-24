package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class TRMTEffects {

    public static final StatusEffect LIGHTNESS = Registry.register(
            Registry.STATUS_EFFECT,
            new Identifier("trmt", "lightness"),
            new LightnessEffect()
    );

    private TRMTEffects() {}

    public static void register() {}
}
