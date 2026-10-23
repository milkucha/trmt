package milkucha.trmt.erosion;

import milkucha.trmt.TRMTNeoForge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Region-sharded erosion storage (GH #68 / A2). Same format on every branch — plain NBT, no Codec.
 * <pre>
 * &lt;world&gt;/data/trmt/erosion/index.dat                         {formatVersion:int, DataVersion:int}
 * &lt;world&gt;/data/trmt/erosion/&lt;ns&gt;/&lt;path&gt;/r.&lt;rx&gt;.&lt;rz&gt;.dat   {DataVersion:int, chunks:[{cx, cz, entries:[
 *                                                              {x, y, z, block, count, threshold, lastTime, stage}]}]}
 * </pre>
 * {@code <ns>/<path>} is the dimension id. Erosion is not dimension-scoped yet (bug B1), so
 * everything lives under {@code minecraft/overworld}. Regions are 32x32 chunks (vanilla regions).
 * All files are GZIP-compressed and written via temp file + atomic move.
 * <p>
 * The legacy single file (vanilla SavedData) is migrated once when {@code index.dat} does not exist yet.
 * The first existing file of these is used (the other one, if any, is left untouched):
 * <ol>
 *   <li>{@code <world>/dimensions/minecraft/overworld/data/trmt/trmt_erosion.dat} — this branch's SavedData
 *       (id {@code trmt:trmt_erosion} in the overworld's {@code SavedDataStorage}; codec layout);</li>
 *   <li>{@code <world>/data/trmt_erosion.dat} — a world last saved by 1.21.x (key {@code trmt_erosion}; 1.21.11
 *       codec layout or 1.21.1 {@code chunks} list). Vanilla's 26.1 {@code DimensionStorageFileFix} moves only
 *       vanilla files out of {@code <world>/data}, so this file stays where 1.21.x wrote it.</li>
 * </ol>
 * Since 26.1 {@code <world>/data} is the server-wide SavedData folder (the overworld's own data moved to
 * {@code dimensions/minecraft/overworld/data}); the region root keeps home's path relative to the world folder.
 */
public class ErosionRegionStorage {

    static final int FORMAT_VERSION = 1;
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.dat");

    private final Path root;
    /** Legacy SavedData files, in the order they are looked for (see class doc). */
    private final List<Path> legacyCandidates;
    /** The legacy file being migrated (the first existing candidate); null if there is none. */
    private Path legacyFile;
    /** Set while a legacy migration has loaded data but not yet written index.dat. */
    private boolean migrationPending;

    /** @param worldDir the world folder, {@code <world>}. */
    ErosionRegionStorage(Path worldDir) {
        Path dataDir = worldDir.resolve("data");
        this.root = dataDir.resolve("trmt").resolve("erosion");
        this.legacyCandidates = List.of(
                // 26.1 SavedDataStorage of the overworld: <dimension>/data/<namespace>/<path>.dat, id trmt:trmt_erosion
                DimensionType.getStorageFolder(Level.OVERWORLD, worldDir).resolve("data").resolve("trmt").resolve("trmt_erosion.dat"),
                // 1.21.x overworld SavedData key "trmt_erosion"; the 26.1 world upgrade leaves mod files in <world>/data
                dataDir.resolve("trmt_erosion.dat"));
    }

    public static ErosionRegionStorage forServer(MinecraftServer server) {
        return new ErosionRegionStorage(server.getWorldPath(LevelResource.ROOT).normalize());
    }

    Path dimensionDir(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        return root.resolve(id.getNamespace()).resolve(id.getPath());
    }

    Path regionFile(ResourceKey<Level> dimension, long region) {
        return dimensionDir(dimension).resolve("r." + ChunkPos.getX(region) + "." + ChunkPos.getZ(region) + ".dat");
    }

    // --- Loading / migration ---

    /** Fills {@code state} from disk. Called once at server start. */
    public void load(ErosionPersistentState state) {
        Path index = root.resolve("index.dat");
        if (Files.exists(index)) {
            try {
                int version = NbtIo.readCompressed(index, NbtAccounter.unlimitedHeap()).getIntOr("formatVersion", 0);
                if (version > FORMAT_VERSION) {
                    TRMTNeoForge.LOGGER.warn("[TRMT] Erosion data format {} is newer than this TRMT version supports ({}).", version, FORMAT_VERSION);
                }
            } catch (IOException e) {
                TRMTNeoForge.LOGGER.warn("[TRMT] Could not read {}; loading region files anyway.", index, e);
            }
            loadRegions(state, Level.OVERWORLD);
            return;
        }

        legacyFile = legacyCandidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
        if (legacyFile != null) {
            CompoundTag legacy = null;
            try {
                legacy = NbtIo.readCompressed(legacyFile, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                TRMTNeoForge.LOGGER.error("[TRMT] Could not read legacy erosion file {}; renaming it to .unreadable and starting empty.", legacyFile, e);
                moveQuietly(legacyFile, legacyFile.resolveSibling(legacyFile.getFileName() + ".unreadable"));
            }
            if (legacy != null) {
                // Region files left by an interrupted migration are overwritten from the legacy file.
                // SavedData wraps the payload as {data:..., DataVersion}. The vanilla datafixer is skipped
                // (this branch used to pass SAVED_DATA_SCOREBOARD, which has no meaning for erosion data).
                CompoundTag data = legacy.getCompoundOrEmpty("data");
                if (data.get("chunks") instanceof ListTag chunks) {
                    // 1.21.1-era layout (data/trmt_erosion.dat): data.chunks:[{cx, cz, entries:[...]}]
                    state.readChunks(chunks);
                } else {
                    // Codec layout (this branch and 1.21.11): data: {"cx,cz": {"x,y,z": entry}}
                    state.readLegacyCodecData(data);
                }
                state.markAllDirty();
                migrationPending = true;
                TRMTNeoForge.LOGGER.info("[TRMT] Migrating erosion data ({} chunks) to region files, this may take a moment...", state.getAllChunkMaps().size());
                save(state, Level.OVERWORLD); // writes all regions, then index.dat, then renames the legacy file
                return;
            }
        }

        // New world (or index lost): load whatever region files exist and create the index.
        loadRegions(state, Level.OVERWORLD);
        try {
            writeIndex();
        } catch (IOException e) {
            TRMTNeoForge.LOGGER.error("[TRMT] Could not write {}", index, e);
        }
    }

    private void loadRegions(ErosionPersistentState state, ResourceKey<Level> dimension) {
        Path dir = dimensionDir(dimension);
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*.dat")) {
            for (Path file : files) {
                Matcher m = REGION_FILE.matcher(file.getFileName().toString());
                if (!m.matches()) continue;
                try {
                    CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                    state.readChunks(nbt.getListOrEmpty("chunks"));
                } catch (IOException | RuntimeException e) {
                    TRMTNeoForge.LOGGER.error("[TRMT] Could not read erosion region {}; renaming it to .corrupt.", file, e);
                    moveQuietly(file, file.resolveSibling(file.getFileName() + ".corrupt"));
                }
            }
        } catch (IOException e) {
            TRMTNeoForge.LOGGER.error("[TRMT] Could not list erosion regions in {}", dir, e);
        }
    }

    // --- Saving ---

    /**
     * Writes every dirty region of {@code dimension} (deleting files of regions that became empty).
     * A region stays dirty if its write fails, so it is retried on the next save.
     *
     * @return true if all dirty regions were written.
     */
    public boolean save(ErosionPersistentState state, ResourceKey<Level> dimension) {
        boolean ok = true;
        if (!state.getDirtyRegions().isEmpty()) {
            try {
                Files.createDirectories(dimensionDir(dimension));
            } catch (IOException e) {
                TRMTNeoForge.LOGGER.error("[TRMT] Could not create {}", dimensionDir(dimension), e);
                return false;
            }
            for (long region : state.getDirtyRegions().toLongArray()) {
                Path file = regionFile(dimension, region);
                try {
                    var chunks = state.getRegionChunks(region);
                    if (chunks.isEmpty()) {
                        Files.deleteIfExists(file);
                    } else {
                        CompoundTag nbt = new CompoundTag();
                        NbtUtils.addCurrentDataVersion(nbt);
                        nbt.put("chunks", state.writeChunks(chunks));
                        writeAtomically(nbt, file);
                    }
                    state.clearDirty(region);
                } catch (IOException e) {
                    ok = false;
                    TRMTNeoForge.LOGGER.error("[TRMT] Could not save erosion region {}", file, e);
                }
            }
        }
        if (ok && migrationPending) finishMigration();
        return ok;
    }

    private void finishMigration() {
        try {
            writeIndex();
        } catch (IOException e) {
            TRMTNeoForge.LOGGER.error("[TRMT] Could not write erosion index; migration will be retried on the next save.", e);
            return;
        }
        migrationPending = false;
        Path migrated = legacyFile.resolveSibling(legacyFile.getFileName() + ".migrated");
        moveQuietly(legacyFile, migrated);
        TRMTNeoForge.LOGGER.info("[TRMT] Erosion data migrated to {}; old file kept as {}.", root, migrated.getFileName());
    }

    private void writeIndex() throws IOException {
        Files.createDirectories(root);
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("formatVersion", FORMAT_VERSION);
        NbtUtils.addCurrentDataVersion(nbt);
        writeAtomically(nbt, root.resolve("index.dat"));
    }

    /** GZIP NBT to {@code <file>.tmp}, fsync, then atomically replace {@code file}. */
    private static void writeAtomically(CompoundTag nbt, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
            GZIPOutputStream gzip = new GZIPOutputStream(fos, 8192);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(gzip, 8192));
            NbtIo.write(nbt, out); // named root compound, as NbtIo.writeCompressed writes it
            out.flush();
            gzip.finish();
            fos.getFD().sync();
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveQuietly(Path from, Path to) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            TRMTNeoForge.LOGGER.error("[TRMT] Could not rename {} to {}", from, to, e);
        }
    }
}
