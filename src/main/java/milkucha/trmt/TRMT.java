package milkucha.trmt;

import milkucha.trmt.client.network.ClientErosionCache;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.UpdateStagePayload;
import milkucha.trmt.network.VersionCheckPayload;
import milkucha.trmt.network.VersionResponsePayload;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Mod(TRMT.MOD_ID)
public class TRMT {
    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public TRMT(IEventBus modBus) {
        TRMTConfig.load();

        TRMTBlocks.BLOCKS.register(modBus);
        TRMTEffects.MOB_EFFECTS.register(modBus);
        TRMTPotions.POTIONS.register(modBus);

        modBus.addListener(this::registerPayloadHandlers);
        modBus.addListener(this::registerConfigTasks);

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::registerBrewingRecipes);

        LOGGER.info("[TRMT] Initialized.");
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.configurationToClient(VersionCheckPayload.TYPE, VersionCheckPayload.STREAM_CODEC, (payload, context) -> {
            String myVersion = ModList.get().getModContainerById(MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("0.0.0");
            context.reply(new VersionResponsePayload(myVersion));
        });
        registrar.configurationToServer(VersionResponsePayload.TYPE, VersionResponsePayload.STREAM_CODEC, this::handleVersionResponse);

        registrar.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.STREAM_CODEC, (payload, context) -> {
            Map<BlockPos, ClientErosionCache.Entry> chunkEntries = new HashMap<>(payload.entries().size());
            for (SyncChunkPayload.Entry e : payload.entries()) {
                chunkEntries.put(e.pos(), new ClientErosionCache.Entry(
                    e.stage(), e.walkedOnCount(), e.threshold(), e.lastTouchedGameTime()));
            }
            ChunkPos chunkPos = new ChunkPos(payload.chunkX(), payload.chunkZ());
            context.enqueueWork(() -> ClientErosionCache.getInstance().setChunk(chunkPos, chunkEntries));
        });

        registrar.playToClient(UpdateStagePayload.TYPE, UpdateStagePayload.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> ClientErosionCache.getInstance().setEntry(
                payload.pos(), payload.stage(), payload.walkedOnCount(),
                payload.threshold(), payload.lastTouchedGameTime()));
        });
    }

    private void handleVersionResponse(VersionResponsePayload payload, IPayloadContext context) {
        String clientVer = payload.version();
        String serverVer = getModVersion();
        if (isClientOutdated(clientVer, serverVer)) {
            context.disconnect(Component.literal(
                "The Roads More Travelled (TRMT) client version is outdated (v" + clientVer + ")!\n" +
                "This server requires v" + serverVer + " or newer.\n" +
                "Please download the update to join this server."
            ));
        } else {
            context.finishCurrentTask(VersionCheckConfigTask.TYPE);
        }
    }

    private void registerConfigTasks(RegisterConfigurationTasksEvent event) {
        event.register(new VersionCheckConfigTask());
    }

    public record VersionCheckConfigTask() implements ICustomConfigurationTask {
        public static final Type TYPE = new Type(ResourceLocation.fromNamespaceAndPath(MOD_ID, "version_check"));

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(new VersionCheckPayload(getModVersion()));
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        manager.loadState(event.getServer());
        manager.migrateGrassEntries(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        ErosionMapManager.reset();
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ErosionMapManager.getInstance().sendFullSyncToPlayer(serverPlayer);
        }
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        ErosionMapManager.getInstance().removeEntry(event.getPos());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trmt")
            .then(Commands.literal("reloadconfig")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    TRMTConfig.load();
                    ErosionMapManager.getInstance().revertDisabledBlocksAllLoaded(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
                    return 1;
                })));
    }

    private void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        builder.addMix(Potions.AWKWARD, Items.FEATHER, TRMTPotions.LIGHTNESS);
        builder.addMix(TRMTPotions.LIGHTNESS, Items.REDSTONE, TRMTPotions.LONG_LIGHTNESS);
    }

    public static String getModVersion() {
        return ModList.get().getModContainerById(MOD_ID)
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("0.0.0");
    }

    static boolean isClientOutdated(String clientVer, String serverVer) {
        int[] cv = parseVersionParts(clientVer);
        int[] sv = parseVersionParts(serverVer);
        for (int i = 0; i < Math.max(cv.length, sv.length); i++) {
            int c = i < cv.length ? cv[i] : 0;
            int s = i < sv.length ? sv[i] : 0;
            if (c < s) return true;
            if (c > s) return false;
        }
        return false;
    }

    private static int[] parseVersionParts(String ver) {
        String[] parts = ver.split("-")[0].split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
