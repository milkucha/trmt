package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TRMTEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, TRMT.MOD_ID);

    public static final DeferredHolder<MobEffect, LightnessEffect> LIGHTNESS = MOB_EFFECTS.register("lightness", LightnessEffect::new);

    private TRMTEffects() {}
}
