package milkucha.trmt;

import milkucha.trmt.effect.LightnessEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TRMTEffects {

	public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
		BuiltInRegistries.MOB_EFFECT.key(),
		TRMT.MOD_ID);

	public static final DeferredHolder<MobEffect, LightnessEffect> LIGHTNESS =
		EFFECTS.register("lightness", LightnessEffect::new);

	private TRMTEffects() {}

	public static void register(IEventBus modBus) {
		EFFECTS.register(modBus);
	}
}
