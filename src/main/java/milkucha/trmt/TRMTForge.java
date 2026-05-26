package milkucha.trmt;

import com.mojang.logging.LogUtils;
import milkucha.trmt.client.TRMTForgeClient;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("trmt")
public class TRMTForge {

    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TRMTForge(FMLJavaModLoadingContext context) {
        BusGroup busGroup = context.getModBusGroup();
        TRMTConfig.load();

        TRMTEffects.MOB_EFFECTS.register(busGroup);
        TRMTPotions.POTIONS.register(busGroup);
        TRMTBlocks.BLOCKS.register(busGroup);

        TRMTNetwork.register();

        FMLCommonSetupEvent.getBus(busGroup).addListener(this::onCommonSetup);

        if (FMLEnvironment.dist.isClient()) {
            TRMTForgeClient.register(busGroup);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TRMTEffects::initLightnessEntry);
    }
}
