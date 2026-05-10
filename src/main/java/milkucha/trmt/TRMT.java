package milkucha.trmt;

import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.erosion.SinglePlayerErosionTracker;
import milkucha.trmt.erosion.TRMTSyncChunkPayload;
import milkucha.trmt.erosion.TRMTUpdateStagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TRMT implements ModInitializer {
    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final SinglePlayerErosionTracker EROSION_TRACKER = new SinglePlayerErosionTracker();

    @Override
    public void onInitialize() {
        TRMTConfig.load();
        TRMTEffects.register();
        TRMTPotions.register();
        TRMTBlocks.register();

        PayloadTypeRegistry.clientboundPlay().register(TRMTSyncChunkPayload.TYPE, TRMTSyncChunkPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TRMTUpdateStagePayload.TYPE, TRMTUpdateStagePayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EROSION_TRACKER.clear();
            ErosionMapManager manager = ErosionMapManager.getInstance();
            manager.loadState(server);
            manager.migrateGrassEntries(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ErosionMapManager.getInstance().sendFullSyncToPlayer(handler.player));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EROSION_TRACKER.clear();
            ErosionMapManager.reset();
        });
        ServerTickEvents.END_SERVER_TICK.register(server ->
                EROSION_TRACKER.tick(server, ErosionMapManager.getInstance()));

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
                ErosionMapManager.getInstance().removeEntry(pos));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("trmt")
                        .then(Commands.literal("debug")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Debug: tracker activo; camina sobre grass/dirt/sand para acumular desgaste."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("reloadconfig")
                                .requires(src -> src.permissions() instanceof LevelBasedPermissionSet permissions
                                        && permissions.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS))
                                .executes(ctx -> {
                                    TRMTConfig.load();
                                    ErosionMapManager.getInstance().revertDisabledBlocksAllLoaded(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
                                    return 1;
                                }))));

        LOGGER.info("[TRMT] Initialized.");
    }
}
