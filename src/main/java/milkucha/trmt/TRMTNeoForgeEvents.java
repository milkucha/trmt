package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.TRMTNetwork;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.minecraft.ChatFormatting;
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
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
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

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ErosionMapManager manager = ErosionMapManager.getInstance();
        manager.loadState(event.getServer());
        manager.migrateGrassEntries(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ErosionMapManager.reset();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ErosionMapManager.getInstance().sendFullSyncToPlayer(player);
            TRMTNetwork.sendVersionCheck(player);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
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
