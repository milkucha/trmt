package milkucha.trmt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod configuration loaded from {@code config/trmt.json}.
 * Each erodable block type has a min/max step-count range; a random
 * threshold is drawn from that range the first time a position is tracked.
 *
 * <p>Edit the JSON file and restart the server (or world) to apply changes.
 */
public final class TRMTConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "trmt.json";

    // ── nested config types ────────────────────────────────────────────────

    public static class Multipliers {
        public float player  = 0.5f;
        public float mounted = 1.5f;
    }

    public static class MinMax {
        public float min, max;
        MinMax(float min, float max) { this.min = min; this.max = max; }
    }

    public static class ErosionToggles {
        public boolean grassEnabled             = true;
        public boolean dirtEnabled              = true;
        /** When true, the erosion chain continues past eroded_coarse_dirt to eroded_rooted_dirt. */
        public boolean erodedRootedDirtEnabled  = false;
        public boolean sandEnabled              = true;
        public boolean leavesEnabled            = true;
        public boolean vegetationEnabled        = true;
    }

    public static class VegetationThreshold extends MinMax {
        public float dropChance;
        VegetationThreshold(float min, float max, float dropChance) {
            super(min, max);
            this.dropChance = dropChance;
        }
    }

    public static class ErosionThresholds {
        public MinMax            grass               = new MinMax(2f, 4f);
        public MinMax            dirt                = new MinMax(8f, 12f);
        public MinMax            coarseDirt          = new MinMax(12f, 20f);
        public MinMax            sand                = new MinMax(4f, 8f);
        public VegetationThreshold vegetation        = new VegetationThreshold(2f, 3f, 0.2f);
        public VegetationThreshold leaves            = new VegetationThreshold(2f, 3f, 0.1f);
    }

    public static class GrassDeErosion {
        public float stage1 = 1f;
        public float stage2 = 2f;
        public float stage3 = 3f;
        public float stage4 = 5f;
        public float stage5 = 8f;
    }

    public static class DirtDeErosion {
        public float erodedDirt       = 13f;
        public float erodedCoarseDirt = 21f;
        public float erodedRootedDirt = 34f;
    }

    public static class SandDeErosion {
        public float stage0 = 1f;
        public float stage1 = 1f;
        public float stage2 = 2f;
        public float stage3 = 3f;
        public float stage4 = 5f;
    }

    public static class DeErosionTimeoutDays {
        public GrassDeErosion grass = new GrassDeErosion();
        public DirtDeErosion  dirt  = new DirtDeErosion();
        public SandDeErosion  sand  = new SandDeErosion();
    }

    // ── top-level fields ───────────────────────────────────────────────────

    public ErosionToggles       erosion              = new ErosionToggles();
    public Multipliers          erosionMultipliers   = new Multipliers();
    public ErosionThresholds    erosionThresholds    = new ErosionThresholds();
    public DeErosionTimeoutDays deErosionTimeoutDays = new DeErosionTimeoutDays();

    // ── singleton ──────────────────────────────────────────────────────────
    private static TRMTConfig instance = new TRMTConfig();

    private TRMTConfig() {}

    public static TRMTConfig get() {
        return instance;
    }

    // ── load / save ────────────────────────────────────────────────────────

    /**
     * Loads config from disk, or writes a default config if the file does not exist.
     * Called once from {@link TRMT#onInitialize()}.
     */
    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTConfig loaded = GSON.fromJson(reader, TRMTConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    // Save back immediately so any fields added since the last run
                    // are written to disk with their default values.
                    save();
                    TRMT.LOGGER.info("[TRMT] Config loaded from {}", path);
                    return;
                }
            } catch (IOException e) {
                TRMT.LOGGER.error("[TRMT] Failed to read config, using defaults", e);
            }
        }

        // File missing or unreadable — write defaults so the user can edit them.
        save();
        TRMT.LOGGER.info("[TRMT] Default config written to {}", path);
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            TRMT.LOGGER.error("[TRMT] Failed to write default config", e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
