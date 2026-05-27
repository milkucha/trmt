package milkucha.trmt;

import com.mojang.logging.LogUtils;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import static net.minecraft.world.item.Items.FEATHER;
import static net.minecraft.world.item.Items.REDSTONE;
import static net.minecraft.world.item.alchemy.Potions.AWKWARD;

@Mod("trmt")
public class TRMTForge {

    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TRMTForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        TRMTConfig.load();

        TRMTEffects.MOB_EFFECTS.register(modEventBus);
        TRMTPotions.POTIONS.register(modEventBus);
        TRMTBlocks.BLOCKS.register(modEventBus);

        TRMTNetwork.register();

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TRMTEffects.initLightnessEntry();
            addPotionMix(AWKWARD, FEATHER, TRMTPotions.LIGHTNESS.get());
            addPotionMix(TRMTPotions.LIGHTNESS.get(), REDSTONE, TRMTPotions.LONG_LIGHTNESS.get());
        });
    }

    private static void addPotionMix(Potion input, Item ingredient, Potion output) {
        BrewingRecipeRegistry.addRecipe(new IBrewingRecipe() {
            @Override
            public boolean isInput(ItemStack stack) {
                return (stack.getItem() instanceof PotionItem
                        || stack.getItem() instanceof SplashPotionItem
                        || stack.getItem() instanceof LingeringPotionItem)
                        && PotionUtils.getPotion(stack) == input;
            }
            @Override
            public boolean isIngredient(ItemStack stack) {
                return stack.getItem() == ingredient;
            }
            @Override
            public ItemStack getOutput(ItemStack inputStack, ItemStack ingredientStack) {
                if (!isInput(inputStack) || !isIngredient(ingredientStack)) return ItemStack.EMPTY;
                ItemStack result = new ItemStack(inputStack.getItem());
                PotionUtils.setPotion(result, output);
                return result;
            }
        });
    }
}
