package milkucha.trmt;

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

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

        FabricPotionBrewingBuilder.BUILD.register(builder -> {
            Holder<Potion> lightnessEntry = BuiltInRegistries.POTION.wrapAsHolder(LIGHTNESS);
            Holder<Potion> longLightnessEntry = BuiltInRegistries.POTION.wrapAsHolder(LONG_LIGHTNESS);
            builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.FEATHER), lightnessEntry);
            builder.registerPotionRecipe(lightnessEntry, Ingredient.of(Items.REDSTONE), longLightnessEntry);
        });
    }
}
