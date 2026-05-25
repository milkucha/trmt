package milkucha.trmt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class TRMTConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "trmt.json";

    // ── nested config types ────────────────────────────────────────────────

    public static class Multipliers {
        public float player  = 0.5f;
        public float mounted = 2.0f;
        public float leash   = 1.5f;
    }

    public static class MinMax {
        public float min, max;
        MinMax(float min, float max) { this.min = min; this.max = max; }
    }

    public static class ErosionToggles {
        public boolean grassEnabled      = true;
        public boolean dirtEnabled       = true;
        public boolean sandEnabled       = true;
        public boolean leavesEnabled     = true;
        public boolean vegetationEnabled = true;
    }

    public static class DeErosionToggles {
        public boolean grassEnabled = true;
        public boolean dirtEnabled  = true;
        public boolean sandEnabled  = true;
    }

    public static class VegetationThreshold extends MinMax {
        public float dropChance;
        VegetationThreshold(float min, float max, float dropChance) {
            super(min, max);
            this.dropChance = dropChance;
        }
    }

    public static class ErosionThresholds {
        public MinMax              grass      = new MinMax(2f, 4f);
        public MinMax              dirt       = new MinMax(8f, 12f);
        public MinMax              coarseDirt = new MinMax(12f, 20f);
        public MinMax              sand       = new MinMax(1.5f, 3f);
        public VegetationThreshold leaves     = new VegetationThreshold(2f, 3f, 0.1f);
        public VegetationThreshold vegetation = new VegetationThreshold(2f, 3f, 0.2f);
    }

    public static class GrassDeErosion {
        public float stage1 = 5f;
        public float stage2 = 5f;
        public float stage3 = 5f;
        public float stage4 = 5f;
        public float stage5 = 5f;
    }

    public static class DirtDeErosion {
        public float erodedDirt       = 8f;
        public float erodedCoarseDirt = 13f;
    }

    public static class SandDeErosion {
        public float stage1 =  3f;
        public float stage2 =  5f;
        public float stage3 =  8f;
        public float stage4 = 13f;
        public float stage5 = 13f;
    }

    public static class DeErosionTimeoutDays {
        public GrassDeErosion grass = new GrassDeErosion();
        public DirtDeErosion  dirt  = new DirtDeErosion();
        public SandDeErosion  sand  = new SandDeErosion();
    }

    // ── top-level fields ───────────────────────────────────────────────────

    public ErosionToggles       erosion              = new ErosionToggles();
    public DeErosionToggles     deErosion            = new DeErosionToggles();
    public Multipliers          erosionMultipliers   = new Multipliers();
    public ErosionThresholds    erosionThresholds    = new ErosionThresholds();
    public List<String> erodableVegetation = Arrays.asList(
        "minecraft:short_grass", "minecraft:tall_grass",
        "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
        "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
        "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy",
        "minecraft:cornflower", "minecraft:lily_of_the_valley", "minecraft:wither_rose",
        "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony"
    );
    public DeErosionTimeoutDays deErosionTimeoutDays = new DeErosionTimeoutDays();

    // ── singleton ──────────────────────────────────────────────────────────
    private static TRMTConfig instance = new TRMTConfig();

    private TRMTConfig() {}

    public static TRMTConfig get() {
        return instance;
    }

    // ── load / save ────────────────────────────────────────────────────────

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTConfig loaded = GSON.fromJson(reader, TRMTConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    BlockThresholds.invalidateVegetationCache();
                    save();
                    LOGGER.info("[TRMT] Config loaded from {}", path);
                    return;
                }
            } catch (IOException e) {
                LOGGER.error("[TRMT] Failed to read config, using defaults", e);
            }
        }

        save();
        LOGGER.info("[TRMT] Default config written to {}", path);
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[TRMT] Failed to write default config", e);
        }
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
