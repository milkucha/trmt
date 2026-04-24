package milkucha.trmt;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class TRMTPotions {

    public static final Potion LIGHTNESS = Registry.register(
            Registry.POTION,
            new Identifier("trmt", "lightness"),
            new Potion("trmt.lightness", new StatusEffectInstance(TRMTEffects.LIGHTNESS, 3600))
    );

    public static final Potion LONG_LIGHTNESS = Registry.register(
            Registry.POTION,
            new Identifier("trmt", "long_lightness"),
            new Potion("trmt.lightness", new StatusEffectInstance(TRMTEffects.LIGHTNESS, 9600))
    );

    private TRMTPotions() {}

    public static void register() {
        // Awkward Potion + Feather → Potion of Lightness (3 min)
        BrewingRecipeRegistry.registerPotionRecipe(Potions.AWKWARD, Items.FEATHER, LIGHTNESS);
        // Potion of Lightness + Redstone → Long Potion of Lightness (8 min)
        BrewingRecipeRegistry.registerPotionRecipe(LIGHTNESS, Items.REDSTONE, LONG_LIGHTNESS);
        // Splash conversion (Potion + Gunpowder → Splash Potion) is handled by vanilla globally.
    }
}
