package milkucha.trmt;

import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class TRMTPotions {

    public static Potion LIGHTNESS;
    public static Potion LONG_LIGHTNESS;

    private TRMTPotions() {}

    public static void register() {
        LIGHTNESS = Registry.register(
                Registries.POTION,
                Identifier.of("trmt", "lightness"),
                new Potion("trmt.lightness", new StatusEffectInstance(TRMTEffects.LIGHTNESS_ENTRY, 3600))
        );
        LONG_LIGHTNESS = Registry.register(
                Registries.POTION,
                Identifier.of("trmt", "long_lightness"),
                new Potion("trmt.lightness", new StatusEffectInstance(TRMTEffects.LIGHTNESS_ENTRY, 9600))
        );

        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            RegistryEntry<Potion> lightnessEntry = Registries.POTION.getOrThrow(
                    RegistryKey.of(RegistryKeys.POTION, Identifier.of("trmt", "lightness")));
            RegistryEntry<Potion> longLightnessEntry = Registries.POTION.getOrThrow(
                    RegistryKey.of(RegistryKeys.POTION, Identifier.of("trmt", "long_lightness")));
            builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.ofItems(Items.FEATHER), lightnessEntry);
            builder.registerPotionRecipe(lightnessEntry, Ingredient.ofItems(Items.REDSTONE), longLightnessEntry);
        });
    }
}
