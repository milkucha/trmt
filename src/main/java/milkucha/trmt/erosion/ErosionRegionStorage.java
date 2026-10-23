package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * The legacy single file {@code <world>/data/trmt_erosion.dat} (vanilla PersistentState, key
 * {@code trmt_erosion}) is migrated once when {@code index.dat} does not exist yet.
 */
public class ErosionRegionStorage {

    static final int FORMAT_VERSION = 1;
    static final String LEGACY_DATA_KEY = "trmt_erosion";
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.dat");

    private final Path root;
    private final Path legacyFile;
    /** Set while a legacy migration has loaded data but not yet written index.dat. */
    private boolean migrationPending;

    /** @param dataDir the overworld data directory, {@code <world>/data}. */
    ErosionRegionStorage(Path dataDir) {
        this.root = dataDir.resolve("trmt").resolve("erosion");
        this.legacyFile = dataDir.resolve(LEGACY_DATA_KEY + ".dat");
    }

    public static ErosionRegionStorage forServer(MinecraftServer server) {
        return new ErosionRegionStorage(server.getSavePath(WorldSavePath.ROOT).resolve("data").normalize());
    }

    Path dimensionDir(RegistryKey<World> dimension) {
        Identifier id = dimension.getValue();
        return root.resolve(id.getNamespace()).resolve(id.getPath());
    }

    Path regionFile(RegistryKey<World> dimension, long region) {
        return dimensionDir(dimension).resolve("r." + ChunkPos.getPackedX(region) + "." + ChunkPos.getPackedZ(region) + ".dat");
    }

    // --- Loading / migration ---

    /** Fills {@code state} from disk. Called once at server start. */
    public void load(ErosionPersistentState state) {
        Path index = root.resolve("index.dat");
        if (Files.exists(index)) {
            try {
                int version = NbtIo.readCompressed(index.toFile()).getInt("formatVersion");
                if (version > FORMAT_VERSION) {
                    TRMT.LOGGER.warn("[TRMT] Erosion data format {} is newer than this TRMT version supports ({}).", version, FORMAT_VERSION);
                }
            } catch (IOException e) {
                TRMT.LOGGER.warn("[TRMT] Could not read {}; loading region files anyway.", index, e);
            }
            loadRegions(state, World.OVERWORLD);
            return;
        }

        if (Files.exists(legacyFile)) {
            NbtCompound legacy = null;
            try {
                legacy = NbtIo.readCompressed(legacyFile.toFile());
            } catch (IOException e) {
                TRMT.LOGGER.error("[TRMT] Could not read legacy erosion file {}; renaming it to .unreadable and starting empty.", legacyFile, e);
                moveQuietly(legacyFile, legacyFile.resolveSibling(legacyFile.getFileName() + ".unreadable"));
            }
            if (legacy != null) {
                // Region files left by an interrupted migration are overwritten from the legacy file.
                state.readChunks(legacy.getCompound("data").getList("chunks", NbtElement.COMPOUND_TYPE));
                state.markAllDirty();
                migrationPending = true;
                TRMT.LOGGER.info("[TRMT] Migrating erosion data ({} chunks) to region files, this may take a moment...", state.getAllChunkMaps().size());
                save(state, World.OVERWORLD); // writes all regions, then index.dat, then renames the legacy file
                return;
            }
        }

        // New world (or index lost): load whatever region files exist and create the index.
        loadRegions(state, World.OVERWORLD);
        try {
            writeIndex();
        } catch (IOException e) {
            TRMT.LOGGER.error("[TRMT] Could not write {}", index, e);
        }
    }

    private void loadRegions(ErosionPersistentState state, RegistryKey<World> dimension) {
        Path dir = dimensionDir(dimension);
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*.dat")) {
            for (Path file : files) {
                Matcher m = REGION_FILE.matcher(file.getFileName().toString());
                if (!m.matches()) continue;
                try {
                    NbtCompound nbt = NbtIo.readCompressed(file.toFile());
                    state.readChunks(nbt.getList("chunks", NbtElement.COMPOUND_TYPE));
                } catch (IOException | RuntimeException e) {
                    TRMT.LOGGER.error("[TRMT] Could not read erosion region {}; renaming it to .corrupt.", file, e);
                    moveQuietly(file, file.resolveSibling(file.getFileName() + ".corrupt"));
                }
            }
        } catch (IOException e) {
            TRMT.LOGGER.error("[TRMT] Could not list erosion regions in {}", dir, e);
        }
    }

    // --- Saving ---

    /**
     * Writes every dirty region of {@code dimension} (deleting files of regions that became empty).
     * A region stays dirty if its write fails, so it is retried on the next save.
     *
     * @return true if all dirty regions were written.
     */
    public boolean save(ErosionPersistentState state, RegistryKey<World> dimension) {
        boolean ok = true;
        if (!state.getDirtyRegions().isEmpty()) {
            try {
                Files.createDirectories(dimensionDir(dimension));
            } catch (IOException e) {
                TRMT.LOGGER.error("[TRMT] Could not create {}", dimensionDir(dimension), e);
                return false;
            }
            for (long region : state.getDirtyRegions().toLongArray()) {
                Path file = regionFile(dimension, region);
                try {
                    var chunks = state.getRegionChunks(region);
                    if (chunks.isEmpty()) {
                        Files.deleteIfExists(file);
                    } else {
                        NbtCompound nbt = new NbtCompound();
                        NbtHelper.putDataVersion(nbt);
                        nbt.put("chunks", state.writeChunks(chunks));
                        writeAtomically(nbt, file);
                    }
                    state.clearDirty(region);
                } catch (IOException e) {
                    ok = false;
                    TRMT.LOGGER.error("[TRMT] Could not save erosion region {}", file, e);
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
            TRMT.LOGGER.error("[TRMT] Could not write erosion index; migration will be retried on the next save.", e);
            return;
        }
        migrationPending = false;
        Path migrated = legacyFile.resolveSibling(legacyFile.getFileName() + ".migrated");
        moveQuietly(legacyFile, migrated);
        TRMT.LOGGER.info("[TRMT] Erosion data migrated to {}; old file kept as {}.", root, migrated.getFileName());
    }

    private void writeIndex() throws IOException {
        Files.createDirectories(root);
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("formatVersion", FORMAT_VERSION);
        NbtHelper.putDataVersion(nbt);
        writeAtomically(nbt, root.resolve("index.dat"));
    }

    /** GZIP NBT to {@code <file>.tmp}, fsync, then atomically replace {@code file}. */
    private static void writeAtomically(NbtCompound nbt, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
            GZIPOutputStream gzip = new GZIPOutputStream(fos, 8192);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(gzip, 8192));
            NbtIo.write(nbt, out);
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
            TRMT.LOGGER.error("[TRMT] Could not rename {} to {}", from, to, e);
        }
    }
}
