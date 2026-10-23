package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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
     * Load the erosion region files (and migrate the legacy file) while the overworld is created:
     * MinecraftServer.createLevels posts LevelEvent.Load for the overworld right after putting it in the
     * level map, inside loadLevel() and before runServer() starts ticking (same point as home's
     * ServerWorldEvents.LOAD). ClientLevel also posts LevelEvent.Load, hence the ServerLevel check.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ErosionMapManager.getInstance().loadState(level.getServer());
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
            ErosionMapManager.getInstance().onWorldSave(level);
        }
    }

    /** The legacy grass-entry migration places blocks, so it waits until the levels are fully ready. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ErosionMapManager.getInstance().migrateGrassEntries(event.getServer());
    }

    /**
     * Per-chunk erosion data, only for players whose client reports the debug HUD as on.
     * ChunkWatchEvent.Sent is posted at the end of PlayerChunkSender.sendChunk, right after the
     * ClientboundLevelChunkWithLightPacket, so our payload reaches the client after the chunk
     * (ChunkWatchEvent.Watch fires at queue time, before the chunk is sent, and must not be used).
     */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ErosionMapManager.getInstance().sendChunkSyncToPlayer(event.getPlayer(), event.getPos());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ErosionMapManager.reset();
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
            ErosionMapManager.getInstance().setHudEnabled(player, false);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        ErosionMapManager.getInstance().removeEntry(event.getPos());
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
                .then(Commands.literal("reloadconfig")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            TRMTConfig.load();
                            ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
                            return 1;
                        }))
                .then(Commands.literal("convert-to-vanilla")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[TRMT] WARNING: This will convert all existing eroded blocks in all currently loaded chunks to their vanilla counterparts. This cannot be undone. ")
                                    .append(Component.literal("[Click to confirm]")
                                            .withStyle(s -> s
                                                    .withClickEvent(new ClickEvent.RunCommand("/trmt convert-to-vanilla confirm"))
                                                    .withColor(ChatFormatting.YELLOW)
                                                    .withUnderlined(true))), false);
                            return 1;
                        })
                        .then(Commands.literal("confirm")
                                .executes(ctx -> {
                                    ErosionMapManager.getInstance().convertAllErodedToVanilla(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] All eroded blocks in loaded chunks converted to their vanilla counterparts."), true);
                                    return 1;
                                })))
                .then(Commands.literal("eroded-chunks")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                            Set<ChunkPos> allChunks = ErosionMapManager.getInstance().getErodedChunkPositions();

                            List<ChunkPos> unloaded = new ArrayList<>();
                            int loadedCount = 0;
                            for (ChunkPos cp : allChunks) {
                                if (overworld != null && overworld.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false) != null) {
                                    loadedCount++;
                                } else {
                                    unloaded.add(cp);
                                }
                            }

                            int total = allChunks.size();
                            int unloadedCount = unloaded.size();
                            int loadedFinal = loadedCount;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[TRMT] Eroded chunks: " + total + " chunk(s) total — " + loadedFinal + " loaded, " + unloadedCount + " unloaded."), false);

                            if (unloaded.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "[TRMT] All eroded chunks are currently loaded."), false);
                            } else {
                                TRMTNeoForge.LOGGER.info("[TRMT] Unloaded chunks with erosion data ({}):", unloadedCount);
                                for (ChunkPos cp : unloaded) {
                                    TRMTNeoForge.LOGGER.info("[TRMT]   {} {}", cp.getMinBlockX(), cp.getMinBlockZ());
                                }
                                if (unloadedCount <= 20) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Unloaded chunk coordinates:"), false);
                                    for (ChunkPos cp : unloaded) {
                                        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
                                        ctx.getSource().sendSuccess(() -> Component.literal("  " + bx + " " + bz), false);
                                    }
                                } else {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[TRMT] " + unloadedCount + " unloaded chunks — full list printed to server console."), false);
                                }
                            }
                            return 1;
                        })));
    }
}
