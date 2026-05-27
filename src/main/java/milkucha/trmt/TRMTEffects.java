package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TRMTEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "trmt");

    public static final RegistryObject<MobEffect> LIGHTNESS =
            MOB_EFFECTS.register("lightness", LightnessEffect::new);

    public static MobEffect LIGHTNESS_ENTRY;

    private TRMTEffects() {}

    public static void initLightnessEntry() {
        LIGHTNESS_ENTRY = LIGHTNESS.get();
    }
}
