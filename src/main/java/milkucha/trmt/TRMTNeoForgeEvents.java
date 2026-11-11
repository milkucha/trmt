package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.erosion.VanillaConversion;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = "trmt")
public class TRMTNeoForgeEvents {

    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();
        builder.addMix(Potions.AWKWARD, Items.FEATHER, TRMTPotions.LIGHTNESS);
        builder.addMix(TRMTPotions.LIGHTNESS, Items.REDSTONE, TRMTPotions.LONG_LIGHTNESS);
    }

    /**
     * Load each dimension's erosion region files (the overworld also migrates the legacy file) while its
     * level is created: MinecraftServer.createLevels posts LevelEvent.Load for each level right after putting
     * it in the level map, inside loadLevel() and before runServer() starts ticking (same point as home's
     * ServerWorldEvents.LOAD). ClientLevel also posts LevelEvent.Load, hence the ServerLevel check.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ErosionMapManager.loadState(level);
        }
    }

    /**
     * ServerLevel.save posts LevelEvent.Save inside its {@code if (!noSave)} block (next to saveLevelData),
     * so it runs on autosave, /save-all and stop, but not under /save-off (stopServer clears noSave first).
     * Same semantics as home's ServerWorld.saveLevel mixin; no mixin needed on NeoForge.
     */
    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ErosionMapManager.onWorldSave(level);
        }
    }

    /** The legacy grass-entry migration places blocks, so it waits until the levels are fully ready. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ErosionMapManager.migrateGrassEntries(event.getServer());
    }

    /**
     * Per-chunk erosion data, only for players whose client reports the debug HUD as on.
     * ChunkWatchEvent.Sent is posted at the end of PlayerChunkSender.sendChunk, right after the
     * ClientboundLevelChunkWithLightPacket, so our payload reaches the client after the chunk
     * (ChunkWatchEvent.Watch fires at queue time, before the chunk is sent, and must not be used).
     */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ErosionMapManager.get(event.getLevel()).sendChunkSyncToPlayer(event.getPlayer(), event.getPos());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ErosionMapManager.reset();
    }

    /** /trmt convert-to-vanilla runs a few ms per tick (END_SERVER_TICK on Fabric = ServerTickEvent.Post here). */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        VanillaConversion.onServerTick(event.getServer());
    }

    /** A server stop ends a conversion run and releases its tickets (ServerStoppingEvent = SERVER_STOPPING). */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VanillaConversion.onServerStopping();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TRMTNetwork.sendVersionCheck(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ErosionMapManager.setHudEnabled(player, false);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        net.minecraft.core.Direction face = event.getFace();
        if (face == null) return;
        BlockPos placePos = event.getPos().relative(face);
        var below = event.getLevel().getBlockState(placePos.below());
        if (below.is(TRMTBlocks.ERODED_SAND.get())
                && below.getValue(ErodedSandBlock.STAGE) > 0
                && event.getItemStack().getItem() instanceof BlockItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trmt")
                // The root has no permission requirement but every subcommand needs level 2, so without
                // it /trmt is all a player sees (GH #43: singleplayer with cheats off). Say why.
                .executes(ctx -> {
                    if (ctx.getSource().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "[TRMT] Commands: /trmt reloadconfig, /trmt convert-to-vanilla [quick], /trmt eroded-chunks"), false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "[TRMT] The /trmt commands need operator permission (cheats). In singleplayer, enable cheats "
                                + "when creating the world, or use Open to LAN with Allow Cheats: ON, then run the command again."), false);
                    }
                    return 1;
                })
                .then(Commands.literal("reloadconfig")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            if (TRMTConfig.load(true) == TRMTConfig.LoadResult.INVALID) {
                                ctx.getSource().sendFailure(Component.literal("[TRMT] trmt.json is not valid JSON, so nothing was reloaded: the previous settings are still in use. "
                                        + "Fix the file (the server log names the line and the problem) and run /trmt reloadconfig again."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
                            return 1;
                        }))
                // Converts every TRMT block in the world in the background (VanillaConversion, B3).
                // Default: scan every generated chunk. "quick": only chunks with stored erosion data.
                // "full" is kept as an alias of the default so older instructions still work.
                .then(Commands.literal("convert-to-vanilla")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> { VanillaConversion.preview(ctx.getSource(), true); return 1; })
                        .then(Commands.literal("confirm")
                                .executes(ctx -> { VanillaConversion.start(ctx.getSource(), true); return 1; }))
                        .then(Commands.literal("quick")
                                .executes(ctx -> { VanillaConversion.preview(ctx.getSource(), false); return 1; })
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> { VanillaConversion.start(ctx.getSource(), false); return 1; })))
                        .then(Commands.literal("full")
                                .executes(ctx -> { VanillaConversion.preview(ctx.getSource(), true); return 1; })
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> { VanillaConversion.start(ctx.getSource(), true); return 1; })))
                        .then(Commands.literal("cancel")
                                .executes(ctx -> { VanillaConversion.cancel(ctx.getSource()); return 1; })))
                .then(Commands.literal("eroded-chunks")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            // Each dimension has its own erosion data; report each one with its own loaded-chunk check.
                            boolean any = false;
                            for (ServerLevel world : ctx.getSource().getServer().getAllLevels()) {
                                Set<ChunkPos> allChunks = ErosionMapManager.get(world).getErodedChunkPositions();
                                if (allChunks.isEmpty()) continue;
                                any = true;
                                String dim = world.dimension().identifier().toString();

                                List<ChunkPos> unloaded = new ArrayList<>();
                                int loadedCount = 0;
                                for (ChunkPos cp : allChunks) {
                                    if (world.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false) != null) {
                                        loadedCount++;
                                    } else {
                                        unloaded.add(cp);
                                    }
                                }

                                int total = allChunks.size();
                                int unloadedCount = unloaded.size();
                                int loadedFinal = loadedCount;
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "[TRMT] Eroded chunks in " + dim + ": " + total + " chunk(s) total — " + loadedFinal + " loaded, " + unloadedCount + " unloaded."), false);

                                if (unloaded.isEmpty()) {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[TRMT] All eroded chunks in " + dim + " are currently loaded."), false);
                                } else {
                                    TRMTNeoForge.LOGGER.info("[TRMT] Unloaded chunks with erosion data in {} ({}):", dim, unloadedCount);
                                    for (ChunkPos cp : unloaded) {
                                        TRMTNeoForge.LOGGER.info("[TRMT]   {} {}", cp.getMinBlockX(), cp.getMinBlockZ());
                                    }
                                    if (unloadedCount <= 20) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "[TRMT] Unloaded chunk coordinates in " + dim + ":"), false);
                                        for (ChunkPos cp : unloaded) {
                                            int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
                                            ctx.getSource().sendSuccess(() -> Component.literal("  " + bx + " " + bz), false);
                                        }
                                    } else {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "[TRMT] " + unloadedCount + " unloaded chunks in " + dim + " — full list printed to server console."), false);
                                    }
                                }
                            }
                            if (!any) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "[TRMT] Eroded chunks: 0 chunk(s) total."), false);
                            }
                            return 1;
                        })));
    }
}
