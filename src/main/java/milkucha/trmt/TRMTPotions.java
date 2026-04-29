package milkucha.trmt;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TRMTPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, TRMT.MOD_ID);

    public static final DeferredHolder<Potion, Potion> LIGHTNESS = POTIONS.register("lightness",
        () -> new Potion("trmt.lightness", new MobEffectInstance(TRMTEffects.LIGHTNESS, 3600)));

    public static final DeferredHolder<Potion, Potion> LONG_LIGHTNESS = POTIONS.register("long_lightness",
        () -> new Potion("trmt.lightness", new MobEffectInstance(TRMTEffects.LIGHTNESS, 9600)));

    private TRMTPotions() {}
}
