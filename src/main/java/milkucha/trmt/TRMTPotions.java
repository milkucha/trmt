package milkucha.trmt;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TRMTPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, "trmt");

    public static final RegistryObject<Potion> LIGHTNESS =
            POTIONS.register("lightness",
                    () -> new Potion("trmt.lightness",
                            new MobEffectInstance(TRMTEffects.LIGHTNESS.getHolder().orElseThrow(), 3600)));

    public static final RegistryObject<Potion> LONG_LIGHTNESS =
            POTIONS.register("long_lightness",
                    () -> new Potion("trmt.lightness",
                            new MobEffectInstance(TRMTEffects.LIGHTNESS.getHolder().orElseThrow(), 9600)));

    private TRMTPotions() {}
}
