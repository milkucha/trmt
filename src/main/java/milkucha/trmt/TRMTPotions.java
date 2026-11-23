package milkucha.trmt;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public final class TRMTPotions {

    public static Potion LIGHTNESS;
    public static Potion LONG_LIGHTNESS;

    private TRMTPotions() {}

    public static void register() {
        LIGHTNESS = Registry.register(
                BuiltInRegistries.POTION,
                Identifier.fromNamespaceAndPath("trmt", "lightness"),
                new Potion("trmt.lightness", new MobEffectInstance(TRMTEffects.LIGHTNESS_ENTRY, 3600))
        );
        LONG_LIGHTNESS = Registry.register(
                BuiltInRegistries.POTION,
                Identifier.fromNamespaceAndPath("trmt", "long_lightness"),
                new Potion("trmt.lightness", new MobEffectInstance(TRMTEffects.LIGHTNESS_ENTRY, 9600))
        );

        // Brewing is data-driven on 26.3: data/trmt/recipe/brewing/ (awkward + feather -> lightness,
        // lightness + redstone -> long lightness, for potion/splash/lingering, plus the gunpowder and
        // dragon's breath container transforms for both potions).
    }
}
