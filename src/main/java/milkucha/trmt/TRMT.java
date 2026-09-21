package milkucha.trmt;

import milkucha.trmt.block.ErodedSandBlock;
import milkucha.trmt.erosion.ErosionMapManager;
import milkucha.trmt.network.SyncChunkPayload;
import milkucha.trmt.network.TRMTPackets;
import milkucha.trmt.network.UpdateStagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TRMT implements ModInitializer {
    public static final String MOD_ID = "trmt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TRMTConfig.load();
        TRMTEffects.register();
        TRMTPotions.register();
        TRMTBlocks.register();
        BlockTransformers.register();

        // Register our custom play-phase payload types so they can be (de)serialized on both sides.
        // 26.3: PayloadTypeRegistry.playS2C() was renamed to clientboundPlay().
        PayloadTypeRegistry.clientboundPlay().register(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateStagePayload.TYPE, UpdateStagePayload.CODEC);

        // Load (or create) persistent erosion state on server start.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ErosionMapManager manager = ErosionMapManager.getInstance();
            manager.loadState(server);
            // Run migration immediately after loading the state to fix old data.
            manager.migrateGrassEntries(server);
        });

        // Send full erosion data to each player when they join (covers existing erosion they'd otherwise miss).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ErosionMapManager.getInstance().sendFullSyncToPlayer(handler.player));

        // Reset the erosion manager when the server stops so state does not bleed between sessions.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ErosionMapManager.reset());

        // Clear the erosion entry when any block is broken so a freshly placed block always starts from zero.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
                ErosionMapManager.getInstance().removeEntry(pos));

        // Prevent blocks from being placed above sunken eroded sand (stages 1-4) from any angle.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            var placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
            var below = world.getBlockState(placePos.below());
            if (below.is(TRMTBlocks.ERODED_SAND)
                    && below.getValue(ErodedSandBlock.STAGE) > 0
                    && player.getItemInHand(hand).getItem() instanceof BlockItem) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // During login, send server version; disconnect client if its version is older.
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            // 26.3: PacketByteBufs.create() was removed; wrap a fresh Netty buffer directly instead.
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeUtf(getModVersion());
            sender.sendPacket(TRMTPackets.VERSION_CHECK, buf);
        });
        ServerLoginNetworking.registerGlobalReceiver(TRMTPackets.VERSION_CHECK,
            (server, handler, understood, buf, synchronizer, responseSender) -> {
                if (!understood) return;
                String clientVer = buf.readUtf(32767);
                String serverVer = getModVersion();
                if (isClientOutdated(clientVer, serverVer)) {
                    server.execute(() -> handler.disconnect(
                        Component.translatable("trmt.disconnect.outdated", clientVer, serverVer)
                    ));
                }
            });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("trmt")
                        .then(Commands.literal("reloadconfig")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    TRMTConfig.load();
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] Config reloaded."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("convert-to-vanilla")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TRMT] WARNING: This will convert all existing eroded blocks in all currently loaded chunks to their vanilla counterparts. This cannot be undone. ")
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
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                                    Set<ChunkPos> allChunks = ErosionMapManager.getInstance().getErodedChunkPositions();

                                    ArrayList<ChunkPos> unloaded = new ArrayList<>();
                                    int loadedCount = 0;
                                    for (ChunkPos cp : allChunks) {
                                        if (overworld.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false) != null) {
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
                                        LOGGER.info("[TRMT] Unloaded chunks with erosion data ({}):", unloadedCount);
                                        for (ChunkPos cp : allChunks) {
                                            if (!(overworld.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false) != null)) {
                                                LOGGER.info("[TRMT]   {} {}", cp.getMinBlockX(), cp.getMinBlockZ());
                                            }
                                        }
                                        if (unloaded.size() <= 20) {
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "[TRMT] Unloaded chunk coordinates:"), false);
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
                                }))));

        LOGGER.info("[TRMT] Initialized.");
    }

    private static String getModVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    }

    private static boolean isClientOutdated(String clientVer, String serverVer) {
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
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
