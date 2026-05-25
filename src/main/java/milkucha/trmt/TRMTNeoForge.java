package milkucha.trmt;

import com.mojang.logging.LogUtils;
import milkucha.trmt.network.TRMTNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod("trmt")
public class TRMTNeoForge {

    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TRMTNeoForge(IEventBus modEventBus) {
        TRMTConfig.load();

        TRMTEffects.MOB_EFFECTS.register(modEventBus);
        TRMTPotions.POTIONS.register(modEventBus);
        TRMTBlocks.BLOCKS.register(modEventBus);

        TRMTNetwork.register(modEventBus);
    }
}
