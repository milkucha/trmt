package milkucha.trmt;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public final class TRMTPotions {

    public static final Potion LIGHTNESS = Registry.register(
            BuiltInRegistries.POTION,
            Identifier.fromNamespaceAndPath("trmt", "lightness"),
            new Potion("trmt.lightness", new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(TRMTEffects.LIGHTNESS), 3600))
    );

    public static final Potion LONG_LIGHTNESS = Registry.register(
            BuiltInRegistries.POTION,
            Identifier.fromNamespaceAndPath("trmt", "long_lightness"),
            new Potion("trmt.lightness", new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(TRMTEffects.LIGHTNESS), 9600))
    );

    private TRMTPotions() {}

    public static void register() {
        // Brewing recipes are data-driven in MC 26.x.
        // Recipe JSON files define: awkward + feather → lightness, lightness + redstone → long_lightness.
    }
}
