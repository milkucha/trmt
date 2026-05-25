package milkucha.trmt;

import com.mojang.logging.LogUtils;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("trmt")
public class TRMTForge {

    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TRMTForge(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        TRMTConfig.load();

        TRMTEffects.MOB_EFFECTS.register(modEventBus);
        TRMTPotions.POTIONS.register(modEventBus);
        TRMTBlocks.BLOCKS.register(modEventBus);

        TRMTNetwork.register();

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TRMTEffects::initLightnessEntry);
    }
}
