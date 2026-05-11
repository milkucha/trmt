package milkucha.trmt;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;

public final class TRMTPotions {

    public static final Potion LIGHTNESS = Registry.register(
            Registries.POTION,
            Identifier.of("trmt", "lightness"),
            new Potion("lightness", new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(TRMTEffects.LIGHTNESS), 3600))
    );

    public static final Potion LONG_LIGHTNESS = Registry.register(
            Registries.POTION,
            Identifier.of("trmt", "long_lightness"),
            new Potion("lightness", new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(TRMTEffects.LIGHTNESS), 9600))
    );

    private TRMTPotions() {}

    public static void register() {
        // Awkward Potion + Feather → Potion of Lightness (3 min)
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            builder.registerPotionRecipe(Potions.AWKWARD, Items.FEATHER, Registries.POTION.getEntry(LIGHTNESS));
            // Potion of Lightness + Redstone → Long Potion of Lightness (8 min)
            builder.registerPotionRecipe(Registries.POTION.getEntry(LIGHTNESS), Items.REDSTONE, Registries.POTION.getEntry(LONG_LIGHTNESS));
        });
    }
}
