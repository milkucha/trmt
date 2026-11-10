package milkucha.trmt.erosion;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import milkucha.trmt.TRMTNeoForge;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /trmt convert-to-vanilla}: replaces every TRMT block in the world, loaded or not, with its
 * vanilla block (B3), a few milliseconds per server tick.
 * <p>
 * Which chunks: by default ({@code full}) every chunk listed in every dimension's region files, plus
 * the chunks with erosion data. {@code quick}: only the chunks with erosion data, in every dimension,
 * plus the Overworld data's coordinates in every other dimension (before B1 every step was recorded
 * under the Overworld); it misses TRMT blocks that have no stored data.
 * <p>
 * Per chunk: a loaded chunk is converted at once. Otherwise its saved NBT is scanned on the chunk
 * IO thread ({@code Status} and the section palettes only). A chunk that is missing or not fully
 * generated is skipped, so nothing is ever generated; one with no TRMT block in any palette is done
 * without loading it. The rest are loaded with a short-lived ticket, converted and released; vanilla
 * saves them when they unload. A chunk's erosion data is removed once it has been visited, so a run
 * that is cancelled or cut short by a server stop continues where it left off when run again.
 */
public final class VanillaConversion {

    /**
     * Main-thread time per tick for this job's own work (converting chunks, starting scans and loads).
     * 10 ms is a fifth of a 50 ms tick: on a server that already uses most of its tick, a run costs at
     * most that much, and it is still enough to convert dozens of loaded chunks per tick. Reading the
     * saved chunks happens on the IO thread and does not count.
     */
    private static final long TICK_BUDGET_NANOS = 10_000_000L;
    /**
     * Chunk NBT scans queued on the IO thread at once. Scans finish within a tick, so this caps the
     * rate at about 20 x this many chunks per second (64 measured 1280/s, 256 about 5000/s). Players'
     * own chunk loads queue behind these scans on the same thread, so the cap is kept moderate.
     */
    private static final int MAX_SCANS = 128;
    /**
     * Chunks being loaded for conversion at once. Their deserialisation runs on the main thread
     * outside the budget above (in vanilla's between-tick task processing), so this bounds that cost.
     */
    private static final int MAX_LOADS = 16;
    /** A chunk that is not loaded after this many ticks is given up (its erosion data is kept). */
    private static final int LOAD_TIMEOUT_TICKS = 600;
    /** Radius 0: ticket level 33, a FULL (border) chunk — loaded, not ticking, neighbours untouched. */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("trmt_convert", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * Chunks visited per second, for the time estimate only. A dev dedicated server (desktop, SSD,
     * warm file cache) did 2000-2600/s in both modes; this is rounded well down for slower disks and CPUs.
     */
    private static final int EST_CHUNKS_PER_SECOND_DEFAULT = 1000;
    private static final int EST_CHUNKS_PER_SECOND_FULL = 1000;
    /** Above this many chunks the confirmation adds a big-world warning. */
    private static final int BIG_WORLD_CHUNKS = 100_000;

    private static final Set<String> TRMT_BLOCK_IDS = new HashSet<>();
    static {
        for (Block block : ErosionMapManager.vanillaEquivalents().keySet()) {
            TRMT_BLOCK_IDS.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
    }

    /** The running (or region-listing) job; null when idle. */
    private static VanillaConversion running;

    // --- One run ---

    private enum ScanResult { ABSENT, CLEAN, HAS_TRMT, FAILED }

    /**
     * Chunks sharing one erosion-data cleanup: the same coordinates in several dimensions (default
     * mode, pre-B1 data) or a single chunk. Data is removed only when every member has been visited,
     * so an interrupted run never loses the coordinates of a chunk it has not reached yet.
     */
    private static final class Group {
        final ChunkPos pos;
        final List<ServerLevel> worlds = new ArrayList<>();
        int remaining;
        boolean failed;
        Group(ChunkPos pos) { this.pos = pos; }
    }

    private record Target(ServerLevel world, ChunkPos pos, Group group) {}
    private record Scan(Target target, CompletableFuture<ScanResult> result) {}
    private record Load(Target target, int startTick) {}

    private final MinecraftServer server;
    private final boolean full;
    /** Player who started the run, or null for the console. */
    private final UUID playerId;
    private List<Target> targets; // null while the region files are being listed
    private int next;
    private final ArrayDeque<Scan> scans = new ArrayDeque<>();
    private final List<Load> loads = new ArrayList<>();
    private int done, convertedChunks, convertedBlocks, skippedAbsent, failed;
    private int lastReportedTenth;
    private long startNanos;

    private VanillaConversion(MinecraftServer server, boolean full, UUID playerId) {
        this.server = server;
        this.full = full;
        this.playerId = playerId;
    }

    // --- Commands ---

    /** First step: says how much work a run would be, and how to confirm it. */
    public static void preview(CommandSourceStack source, boolean full) {
        MinecraftServer server = source.getServer();
        String confirm = "/trmt convert-to-vanilla " + (full ? "confirm" : "quick confirm");
        if (!full) {
            List<Target> targets = buildDefaultTargets(server);
            int dataChunks = 0;
            for (ServerLevel world : server.getAllLevels()) dataChunks += ErosionMapManager.get(world).getErodedChunkPositions().size();
            int crossDimension = targets.size() - dataChunks;
            sendPreview(source, confirm, targets,
                    "quick mode, only the " + dataChunks + " chunk(s) with stored erosion data"
                            + (crossDimension > 0 ? ", plus up to " + crossDimension + " at the same coordinates in other dimensions "
                            + "(older TRMT versions recorded Nether/End erosion under the Overworld; chunks that were never generated are skipped)" : ""),
                    EST_CHUNKS_PER_SECOND_DEFAULT);
            source.sendSuccess(() -> Component.literal("[TRMT] Quick mode may miss eroded blocks that have no stored erosion data "
                    + "(lost data, older versions, /setblock). /trmt convert-to-vanilla scans every generated chunk and finds them all."), false);
        } else {
            source.sendSuccess(() -> Component.literal("[TRMT] Listing the region files of every dimension..."), false);
            listFullTargets(server).whenCompleteAsync((targets, ex) -> {
                if (targets != null) {
                    sendPreview(source, confirm, targets, "every generated chunk in every dimension", EST_CHUNKS_PER_SECOND_FULL);
                } else {
                    TRMTNeoForge.LOGGER.error("[TRMT] convert-to-vanilla: listing the region files failed", ex);
                }
            }, server);
        }
    }

    private static void sendPreview(CommandSourceStack source, String confirm, List<Target> targets, String what, int rate) {
        Set<ServerLevel> dims = new HashSet<>();
        for (Target t : targets) dims.add(t.world());
        int count = targets.size();
        source.sendSuccess(() -> Component.literal("[TRMT] convert-to-vanilla will visit " + count + " chunk(s) in " + dims.size()
                + " dimension(s): " + what + ". Estimated time: " + formatDuration(count / (double) rate)
                + ". It runs in the background a few milliseconds per tick; the server stays playable. "
                + "Every eroded block becomes its vanilla block and the erosion data is deleted. This cannot be undone."), false);
        if (count > BIG_WORLD_CHUNKS) {
            source.sendSuccess(() -> Component.literal("[TRMT] This is a big world: the run will take a long time. Back up the world first. "
                    + "You can stop it with /trmt convert-to-vanilla cancel and run it again later; it continues where it stopped.")
                    .withStyle(ChatFormatting.RED), false);
        }
        source.sendSuccess(() -> Component.literal("[TRMT] ")
                .append(Component.literal("[Click to confirm]").withStyle(s -> s
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, confirm))
                        .withColor(ChatFormatting.YELLOW)
                        .withUnderlined(true)))
                .append(Component.literal(" or run " + confirm)), false);
    }

    /** Starts a run (the confirm subcommand). */
    public static void start(CommandSourceStack source, boolean full) {
        if (running != null) {
            source.sendSuccess(() -> Component.literal("[TRMT] convert-to-vanilla is already running" + running.progressSuffix()
                    + ". Use /trmt convert-to-vanilla cancel to stop it."), false);
            return;
        }
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        VanillaConversion job = new VanillaConversion(server, full, player != null ? player.getUUID() : null);
        running = job;
        if (!full) {
            job.begin(buildDefaultTargets(server));
        } else {
            job.send(Component.literal("[TRMT] Listing the region files of every dimension..."));
            listFullTargets(server).whenCompleteAsync((targets, ex) -> {
                if (running != job) return; // cancelled meanwhile
                if (targets != null) {
                    job.begin(targets);
                } else {
                    running = null;
                    TRMTNeoForge.LOGGER.error("[TRMT] convert-to-vanilla: listing the region files failed", ex);
                    job.send(Component.literal("[TRMT] convert-to-vanilla could not list the region files; see the server log."));
                }
            }, server);
        }
    }

    public static void cancel(CommandSourceStack source) {
        if (running == null) {
            source.sendSuccess(() -> Component.literal("[TRMT] convert-to-vanilla is not running."), false);
            return;
        }
        VanillaConversion job = running;
        job.stop();
        job.send(Component.literal("[TRMT] convert-to-vanilla cancelled" + job.progressSuffix()
                + ". Run it again to continue: chunks already visited have no erosion data left."));
        ServerPlayer canceller = source.getPlayer();
        boolean starter = canceller == null ? job.playerId == null : canceller.getUUID().equals(job.playerId);
        if (!starter) {
            source.sendSuccess(() -> Component.literal("[TRMT] convert-to-vanilla cancelled."), false);
        }
    }

    /** ServerStoppingEvent: release the tickets; chunks already converted are saved by the shutdown save. */
    public static void onServerStopping() {
        if (running == null) return;
        VanillaConversion job = running;
        job.stop();
        TRMTNeoForge.LOGGER.info("[TRMT] convert-to-vanilla interrupted by server stop{}. Run it again to continue.", job.progressSuffix());
    }

    /** ServerTickEvent.Post. */
    public static void onServerTick(MinecraftServer server) {
        if (running != null && running.targets != null) running.tick();
    }

    // --- Target lists ---

    /** Chunks with erosion data in every dimension, plus the Overworld's coordinates in every other dimension. */
    private static List<Target> buildDefaultTargets(MinecraftServer server) {
        List<Target> targets = new ArrayList<>();
        Map<ServerLevel, LongSet> visited = new HashMap<>();
        ServerLevel overworld = server.overworld();
        for (ChunkPos pos : sorted(ErosionMapManager.get(overworld).getErodedChunkPositions())) {
            Group group = new Group(pos);
            for (ServerLevel world : server.getAllLevels()) {
                if (world != overworld) addTarget(targets, visited, world, pos, group);
            }
            addTarget(targets, visited, overworld, pos, group); // last: its data is what points at the others
        }
        for (ServerLevel world : server.getAllLevels()) {
            if (world == overworld) continue;
            for (ChunkPos pos : sorted(ErosionMapManager.get(world).getErodedChunkPositions())) {
                addTarget(targets, visited, world, pos, new Group(pos));
            }
        }
        return targets;
    }

    /**
     * Every chunk present in each dimension's region files (read from the files' location tables
     * off the main thread), plus the chunks with erosion data (covers chunks not saved yet).
     * Completes on an IO thread; the caller hops back to the server thread.
     */
    private static CompletableFuture<List<Target>> listFullTargets(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Map<ServerLevel, Path> regionDirs = new LinkedHashMap<>();
        Map<ServerLevel, List<ChunkPos>> dataChunks = new HashMap<>();
        for (ServerLevel world : server.getAllLevels()) {
            regionDirs.put(world, DimensionType.getStorageFolder(world.dimension(), root).resolve("region"));
            dataChunks.put(world, sorted(ErosionMapManager.get(world).getErodedChunkPositions()));
        }
        return CompletableFuture.supplyAsync(() -> {
            List<Target> targets = new ArrayList<>();
            Map<ServerLevel, LongSet> visited = new HashMap<>();
            for (Map.Entry<ServerLevel, Path> e : regionDirs.entrySet()) {
                ServerLevel world = e.getKey();
                for (ChunkPos pos : listRegionChunks(e.getValue())) addTarget(targets, visited, world, pos, new Group(pos));
                for (ChunkPos pos : dataChunks.get(world)) addTarget(targets, visited, world, pos, new Group(pos));
            }
            return targets;
        }, Util.ioPool());
    }

    /** Chunks present in the {@code r.X.Z.mca} files of one folder, region by region. */
    private static List<ChunkPos> listRegionChunks(Path regionDir) {
        List<ChunkPos> chunks = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(regionDir, "r.*.*.mca")) {
            for (Path file : files) {
                String[] parts = file.getFileName().toString().split("\\.");
                if (parts.length != 4) continue;
                int regionX, regionZ;
                try {
                    regionX = Integer.parseInt(parts[1]);
                    regionZ = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                // The first 4 KiB of a region file: 1024 chunk locations, index x + z * 32; 0 = no chunk.
                ByteBuffer header = ByteBuffer.allocate(4096);
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                    while (header.hasRemaining() && channel.read(header) >= 0) { }
                } catch (IOException ex) {
                    TRMTNeoForge.LOGGER.warn("[TRMT] convert-to-vanilla: cannot read {}: {}", file, ex.toString());
                    continue;
                }
                if (header.hasRemaining()) continue; // empty or truncated file
                for (int i = 0; i < 1024; i++) {
                    if (header.getInt(i * 4) != 0) {
                        chunks.add(new ChunkPos(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5)));
                    }
                }
            }
        } catch (NoSuchFileException ignored) {
            // dimension never visited
        } catch (IOException ex) {
            TRMTNeoForge.LOGGER.warn("[TRMT] convert-to-vanilla: cannot list {}: {}", regionDir, ex.toString());
        }
        return chunks;
    }

    private static void addTarget(List<Target> targets, Map<ServerLevel, LongSet> visited, ServerLevel world, ChunkPos pos, Group group) {
        if (!visited.computeIfAbsent(world, w -> new LongOpenHashSet()).add(pos.toLong())) return;
        group.worlds.add(world);
        group.remaining++;
        targets.add(new Target(world, pos, group));
    }

    /** Region by region, so the scans read each region file in one go. */
    private static List<ChunkPos> sorted(Set<ChunkPos> chunks) {
        List<ChunkPos> list = new ArrayList<>(chunks);
        list.sort(Comparator.comparingInt(ChunkPos::getRegionX).thenComparingInt(ChunkPos::getRegionZ)
                .thenComparingInt(p -> p.z).thenComparingInt(p -> p.x));
        return list;
    }

    // --- Running ---

    private void begin(List<Target> targets) {
        this.targets = targets;
        this.startNanos = System.nanoTime();
        Set<ServerLevel> dims = new HashSet<>();
        for (Target t : targets) dims.add(t.world());
        send(Component.literal("[TRMT] convert-to-vanilla" + (full ? "" : " quick") + " started: " + targets.size() + " chunk(s) in "
                + dims.size() + " dimension(s). Progress is reported every 10%. Stop it with /trmt convert-to-vanilla cancel."));
        if (targets.isEmpty()) finishRun();
    }

    private void tick() {
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;

        // 1. Chunks requested earlier that have finished loading.
        for (Iterator<Load> it = loads.iterator(); it.hasNext() && System.nanoTime() < deadline; ) {
            Load load = it.next();
            Target t = load.target();
            LevelChunk chunk = t.world().getChunkSource().getChunkNow(t.pos().x, t.pos().z);
            if (chunk != null) {
                convert(t, chunk);
                release(t);
                it.remove();
                finishTarget(t, true);
            } else if (server.getTickCount() - load.startTick() > LOAD_TIMEOUT_TICKS) {
                TRMTNeoForge.LOGGER.warn("[TRMT] convert-to-vanilla: chunk {} in {} did not load; skipped, its erosion data is kept.",
                        t.pos(), t.world().dimension().location());
                release(t);
                it.remove();
                finishTarget(t, false);
            }
        }

        // 2. Finished scans (the IO thread answers in order).
        while (!scans.isEmpty() && scans.peek().result().isDone() && System.nanoTime() < deadline) {
            Scan scan = scans.peek();
            ScanResult result = scan.result().getNow(ScanResult.FAILED);
            if (result == ScanResult.HAS_TRMT && loads.size() >= MAX_LOADS) break;
            scans.poll();
            Target t = scan.target();
            switch (result) {
                case ABSENT -> { skippedAbsent++; finishTarget(t, true); }
                case CLEAN -> finishTarget(t, true);
                case FAILED -> {
                    TRMTNeoForge.LOGGER.warn("[TRMT] convert-to-vanilla: could not read chunk {} in {}; skipped, its erosion data is kept.",
                            t.pos(), t.world().dimension().location());
                    finishTarget(t, false);
                }
                case HAS_TRMT -> startLoad(t);
            }
        }

        // 3. New chunks: loaded ones are converted now, the others are scanned.
        while (next < targets.size() && scans.size() < MAX_SCANS && System.nanoTime() < deadline) {
            Target t = targets.get(next++);
            LevelChunk chunk = t.world().getChunkSource().getChunkNow(t.pos().x, t.pos().z);
            if (chunk != null) {
                convert(t, chunk);
                finishTarget(t, true);
            } else {
                scans.add(new Scan(t, scan(t)));
            }
        }

        int tenth = targets.isEmpty() ? 10 : (int) (done * 10L / targets.size());
        if (tenth > lastReportedTenth && tenth < 10) {
            lastReportedTenth = tenth;
            send(Component.literal("[TRMT] convert-to-vanilla: " + tenth * 10 + "%" + progressSuffix()));
        }
        if (next >= targets.size() && scans.isEmpty() && loads.isEmpty()) finishRun();
    }

    /** Reads {@code Status} and the block palettes of the saved chunk, on the chunk IO thread. */
    private static CompletableFuture<ScanResult> scan(Target t) {
        CollectFields collector = new CollectFields(
                new FieldSelector(StringTag.TYPE, "Status"),
                new FieldSelector(ListTag.TYPE, "sections"));
        return t.world().getChunkSource().chunkScanner().scanChunk(t.pos(), collector)
                .thenApply(v -> classify(collector.getResult()))
                .exceptionally(ex -> ScanResult.FAILED);
    }

    private static ScanResult classify(Tag root) {
        if (!(root instanceof CompoundTag nbt)) return ScanResult.ABSENT; // no saved chunk
        // Only a fully generated chunk can hold TRMT blocks, and only loading one of those never generates anything.
        if (ChunkStatus.byName(nbt.getString("Status")) != ChunkStatus.FULL) return ScanResult.ABSENT;
        if (!nbt.contains("sections", Tag.TAG_LIST)) return ScanResult.HAS_TRMT; // unexpected layout: load and look
        ListTag sections = nbt.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sections.size(); i++) {
            ListTag palette = sections.getCompound(i).getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
            for (int j = 0; j < palette.size(); j++) {
                if (TRMT_BLOCK_IDS.contains(palette.getCompound(j).getString("Name"))) return ScanResult.HAS_TRMT;
            }
        }
        return ScanResult.CLEAN;
    }

    private void startLoad(Target t) {
        LevelChunk chunk = t.world().getChunkSource().getChunkNow(t.pos().x, t.pos().z);
        if (chunk != null) { // loaded meanwhile
            convert(t, chunk);
            finishTarget(t, true);
            return;
        }
        t.world().getChunkSource().addRegionTicket(TICKET, t.pos(), 0, t.pos());
        loads.add(new Load(t, server.getTickCount()));
    }

    /** Drops this job's ticket; the chunk unloads (and vanilla saves it) unless something else holds it. */
    private void release(Target t) {
        t.world().getChunkSource().removeRegionTicket(TICKET, t.pos(), 0, t.pos());
    }

    private void convert(Target t, LevelChunk chunk) {
        int blocks = ErosionMapManager.get(t.world()).convertChunkToVanilla(chunk);
        if (blocks > 0) {
            convertedChunks++;
            convertedBlocks += blocks;
        }
    }

    private void finishTarget(Target t, boolean ok) {
        done++;
        Group group = t.group();
        if (!ok) {
            failed++;
            group.failed = true;
        }
        if (--group.remaining == 0 && !group.failed) {
            for (ServerLevel world : group.worlds) ErosionMapManager.get(world).clearChunk(group.pos);
        }
    }

    private void finishRun() {
        running = null;
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        String rate = seconds > 0 ? String.format(" (%.0f chunks/s)", done / seconds) : "";
        send(Component.literal("[TRMT] convert-to-vanilla" + (full ? "" : " quick") + " finished in " + formatDuration(seconds) + rate
                + ": visited " + done + " chunk(s), " + skippedAbsent + " not generated; converted " + convertedBlocks
                + " block(s) in " + convertedChunks + " chunk(s)."
                + (failed > 0 ? " " + failed + " chunk(s) could not be read or loaded (see the log); their erosion data was kept, run it again." : "")));
        TRMTNeoForge.LOGGER.info("[TRMT] convert-to-vanilla finished: {} chunks in {} ms", done, (long) (seconds * 1000));
    }

    /** Cancel / server stop: forget the queue and release every ticket this job holds. */
    private void stop() {
        if (running == this) running = null;
        for (Load load : loads) release(load.target());
        loads.clear();
        scans.clear();
    }

    private String progressSuffix() {
        if (targets == null) return " (listing region files)";
        return " (" + done + "/" + targets.size() + " chunks, " + convertedBlocks + " blocks converted)";
    }

    /** To the console (log) and to the player who started the run, if online. */
    private void send(MutableComponent message) {
        TRMTNeoForge.LOGGER.info(message.getString());
        if (playerId == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.sendSystemMessage(message);
    }

    private static String formatDuration(double seconds) {
        if (seconds < 60) return Math.max(1, Math.round(seconds)) + " s";
        long minutes = Math.round(seconds / 60);
        if (minutes < 60) return minutes + " min";
        return minutes / 60 + " h " + minutes % 60 + " min";
    }
}
