package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

public final class TRMTEffects {

    public static final MobEffect LIGHTNESS = Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath("trmt", "lightness"),
            new LightnessEffect()
    );

    public static Holder<MobEffect> LIGHTNESS_ENTRY;

    private TRMTEffects() {}

    public static void register() {
        LIGHTNESS_ENTRY = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(LIGHTNESS);
    }
}
