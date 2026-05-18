package milkucha.trmt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Items;

public final class TRMTPotions {

	public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(
		BuiltInRegistries.POTION.key(),
		TRMT.MOD_ID);

	public static final DeferredHolder<Potion, Potion> LIGHTNESS = POTIONS.register("lightness",
		() -> new Potion(new MobEffectInstance(TRMTEffects.LIGHTNESS, 3600)));

	public static final DeferredHolder<Potion, Potion> LONG_LIGHTNESS = POTIONS.register("long_lightness",
		() -> new Potion(new MobEffectInstance(TRMTEffects.LIGHTNESS, 9600)));

	private TRMTPotions() {}

	public static void register(IEventBus modBus) {
		POTIONS.register(modBus);
	}

	public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
		var builder = event.getBuilder();
		builder.addMix(Potions.AWKWARD, Items.FEATHER, LIGHTNESS);
		builder.addMix(LIGHTNESS, Items.REDSTONE, LONG_LIGHTNESS);
	}
}
