package milkucha.trmt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import milkucha.trmt.erosion.BlockThresholds;
import milkucha.trmt.path.PathSpeedBuff;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        public boolean snowEnabled       = true;
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
        public VegetationThreshold snow       = new VegetationThreshold(0.4f, 0.9f, 0f);
        public MinMax              snowBlock  = new MinMax(0.5f, 1.0f);
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

    public static class DeErosionToggles {
        public boolean grassEnabled = true;
        public boolean dirtEnabled  = true;
        public boolean sandEnabled  = true;
    }

    /** Depth weight per stage of a block with stages 0-4 (eroded grass, eroded sand). */
    public static class StageWeights5 {
        public float stage0, stage1, stage2, stage3, stage4;
        public StageWeights5() {}
        StageWeights5(float s0, float s1, float s2, float s3, float s4) {
            stage0 = s0; stage1 = s1; stage2 = s2; stage3 = s3; stage4 = s4;
        }
    }

    /** Depth weight per stage of a block with stages 0-3 (eroded dirt). */
    public static class StageWeights4 {
        public float stage0, stage1, stage2, stage3;
        public StageWeights4() {}
        StageWeights4(float s0, float s1, float s2, float s3) {
            stage0 = s0; stage1 = s1; stage2 = s2; stage3 = s3;
        }
    }

    /** Depth weights of the eroded blocks for the path speed buff; weight 0 = the block does not count as path. */
    public static class PathBlockWeights {
        public StageWeights5 erodedGrass      = new StageWeights5(1f, 1f, 2f, 2f, 2f);
        public StageWeights4 erodedDirt       = new StageWeights4(3f, 3f, 3f, 3f);
        public float         erodedCoarseDirt = 3f;
        public StageWeights5 erodedSand       = new StageWeights5(1f, 1f, 2f, 2f, 3f);
        public PathBlockWeights() {}
    }

    /** Settings of the path speed buff (see {@code milkucha.trmt.path.PathSpeedBuff}). */
    public static class PathSpeedBuffConfig {
        public boolean enabled      = true;
        /** Minimum density (eroded blocks / 9 in the 3x3x3 window) to start the buff. */
        public float   gateEnter    = 0.6f;
        /** Minimum density to keep the buff once it is on (hysteresis); never above gateEnter. */
        public float   gateKeep     = 0.45f;
        /** Consecutive evaluations below gateKeep before the buff is lost. */
        public int     graceBlocks  = 4;
        public float   tier1Percent = 8f;
        public float   tier2Percent = 16f;
        public float   tier3Percent = 25f;
        public float   tier2MinDepth = 1.75f;
        public float   tier3MinDepth = 2.5f;
        /** Extra depth margin that must be crossed before the tier drops again. */
        public float   tierMargin   = 0.25f;
        public PathBlockWeights weights = new PathBlockWeights();
        public PathSpeedBuffConfig() {}
    }

    // ── top-level fields ───────────────────────────────────────────────────

    public ErosionToggles       erosion              = new ErosionToggles();
    public DeErosionToggles     deErosion            = new DeErosionToggles();
    public Multipliers          erosionMultipliers   = new Multipliers();
    public PathSpeedBuffConfig  pathSpeedBuff        = new PathSpeedBuffConfig();
    public ErosionThresholds    erosionThresholds    = new ErosionThresholds();
    public List<String> erodableVegetation = Arrays.asList(
        "minecraft:grass", "minecraft:tall_grass",
        "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
        "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
        "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy",
        "minecraft:cornflower", "minecraft:lily_of_the_valley", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony"
    );
    /** Modded blocks that always revert eroded dirt / coarse dirt / grass below them (vanilla shulker boxes already do). */
    public List<String> revertAlwaysBlocks = new ArrayList<>();
    /** Modded blocks that never revert eroded dirt / coarse dirt / grass below them (wins over revertAlwaysBlocks). */
    public List<String> revertNeverBlocks = new ArrayList<>();
    public DeErosionTimeoutDays deErosionTimeoutDays = new DeErosionTimeoutDays();
    /**
     * Step progress on a block that has not changed yet (grass, dirt, sand, snow, leaves, plants) is
     * forgotten after this many Minecraft days untouched. 0 = never. Eroded blocks are not affected.
     */
    public float entryExpiryDays = 5f;

    // ── singleton ──────────────────────────────────────────────────────────
    private static TRMTConfig instance = new TRMTConfig();

    private TRMTConfig() {}

    public static TRMTConfig get() {
        return instance;
    }

    // ── load / save ────────────────────────────────────────────────────────

    /** Outcome of {@link #load(boolean)}. */
    public enum LoadResult { LOADED, DEFAULTS_WRITTEN, INVALID }

    /**
     * Loads config from disk, or writes a default config if the file does not exist.
     * Called once from {@link TRMT#onInitialize()}.
     */
    public static void load() {
        load(false);
    }

    /**
     * Loads config from disk. If the file is not valid JSON it is left untouched (so the user can fix
     * it) and {@link LoadResult#INVALID} is returned: the running config becomes the defaults, or stays
     * as it was when {@code keepPreviousOnInvalid} is set (used by {@code /trmt reloadconfig}).
     */
    public static LoadResult load(boolean keepPreviousOnInvalid) {
        LoadResult result = loadFile(keepPreviousOnInvalid);
        // Whatever config is now active (loaded, defaults or the kept previous one), rebuild what is derived from it.
        PathSpeedBuff.rebuildWeights(instance.pathSpeedBuff);
        return result;
    }

    private static LoadResult loadFile(boolean keepPreviousOnInvalid) {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTConfig loaded = parse(reader, new TRMTConfig(), TRMTConfig.class, FILE_NAME);
                if (loaded != null) {
                    sanitize(loaded);
                    instance = loaded;
                    BlockThresholds.invalidateVegetationCache();
                    BlockThresholds.invalidateRevertCaches();
                    // Save back immediately so the file shows the values in use: missing or
                    // invalid entries are written with their defaults, unknown keys are dropped.
                    save();
                    TRMT.LOGGER.info("[TRMT] Config loaded from {}", path);
                    return LoadResult.LOADED;
                }
            } catch (JsonParseException e) {
                // Malformed JSON: keep the file so the user can fix it; run on defaults (startup) or keep the previous config (reload).
                if (keepPreviousOnInvalid) {
                    TRMT.LOGGER.error("[TRMT] {} is not valid JSON, keeping the previous settings (file left unchanged): {}", path, e.getMessage());
                } else {
                    TRMT.LOGGER.error("[TRMT] {} is not valid JSON, using defaults (file left unchanged): {}", path, e.getMessage());
                    instance = new TRMTConfig();
                    BlockThresholds.invalidateVegetationCache();
                    BlockThresholds.invalidateRevertCaches();
                }
                return LoadResult.INVALID;
            } catch (IOException e) {
                TRMT.LOGGER.error("[TRMT] Failed to read config, using defaults", e);
            }
        }

        // File missing or unreadable — write defaults so the user can edit them.
        save();
        TRMT.LOGGER.info("[TRMT] Default config written to {}", path);
        return LoadResult.DEFAULTS_WRITTEN;
    }

    /** Cross-field rules that {@link #merge} cannot express; applied before the config is used and saved back. */
    private static void sanitize(TRMTConfig config) {
        PathSpeedBuffConfig p = config.pathSpeedBuff;
        if (p.gateEnter > 1f) {
            TRMT.LOGGER.warn("[TRMT] {}: pathSpeedBuff.gateEnter {} is above 1, using 1", FILE_NAME, p.gateEnter);
            p.gateEnter = 1f;
        }
        if (p.gateKeep > 1f) {
            TRMT.LOGGER.warn("[TRMT] {}: pathSpeedBuff.gateKeep {} is above 1, using 1", FILE_NAME, p.gateKeep);
            p.gateKeep = 1f;
        }
        if (p.gateKeep > p.gateEnter) {
            TRMT.LOGGER.warn("[TRMT] {}: pathSpeedBuff.gateKeep {} is above gateEnter {}, using {} for both",
                    FILE_NAME, p.gateKeep, p.gateEnter, p.gateEnter);
            p.gateKeep = p.gateEnter;
        }
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

    /**
     * Reads a config file on top of {@code defaults}: every key that is missing, {@code null}
     * or invalid keeps its default value (with a warning for invalid ones), unknown keys are
     * ignored. Works at the JSON-tree level, so Gson never has to construct a config object
     * from a partial object (it skips field initialisers for classes without a no-arg
     * constructor, e.g. {@link MinMax}, leaving 0s). Rules: numbers must be finite and
     * {@code >= 0}, an object with {@code min}/{@code max} needs {@code min <= max},
     * {@code dropChance} must be within [0, 1], lists keep only their string entries.
     *
     * @return the merged config, or {@code null} if the file is empty or not a JSON object
     * @throws JsonParseException if the file is not valid JSON
     */
    public static <T> T parse(Reader reader, T defaults, Class<T> type, String fileName) {
        JsonElement user = JsonParser.parseReader(reader);
        if (!user.isJsonObject()) return null;
        JsonObject merged = GSON.toJsonTree(defaults).getAsJsonObject();
        merge(merged, user.getAsJsonObject(), fileName + ": ");
        return GSON.fromJson(merged, type);
    }

    private static void merge(JsonObject defaults, JsonObject user, String path) {
        JsonElement defMin = defaults.get("min"), defMax = defaults.get("max");
        for (String key : new ArrayList<>(defaults.keySet())) {
            JsonElement def = defaults.get(key), val = user.get(key);
            if (val == null || val.isJsonNull()) continue;
            if (def.isJsonObject() && val.isJsonObject()) {
                merge(def.getAsJsonObject(), val.getAsJsonObject(), path + key + ".");
            } else if (def.isJsonArray() && val.isJsonArray()) {
                JsonArray strings = new JsonArray();
                for (JsonElement e : val.getAsJsonArray()) {
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) strings.add(e);
                    else TRMT.LOGGER.warn("[TRMT] {}{}: ignoring non-string entry {}", path, key, e);
                }
                defaults.add(key, strings);
            } else if (isValid(key, def, val)) {
                defaults.add(key, val);
            } else {
                TRMT.LOGGER.warn("[TRMT] {}{}: invalid value {}, using default {}", path, key, val, def);
            }
        }
        JsonElement min = defaults.get("min"), max = defaults.get("max");
        if (defMin != null && defMax != null && min.getAsDouble() > max.getAsDouble()) {
            TRMT.LOGGER.warn("[TRMT] {}min {} > max {}, using default range {}..{}", path, min, max, defMin, defMax);
            defaults.add("min", defMin);
            defaults.add("max", defMax);
        }
    }

    private static boolean isValid(String key, JsonElement def, JsonElement val) {
        if (!def.isJsonPrimitive() || !val.isJsonPrimitive()) return false;
        JsonPrimitive d = def.getAsJsonPrimitive(), v = val.getAsJsonPrimitive();
        if (d.isBoolean()) return v.isBoolean();
        if (d.isNumber()) {
            double x;
            try { x = v.getAsDouble(); } catch (NumberFormatException e) { return false; } // "1.5" is fine
            if (d.getAsNumber() instanceof Integer || d.getAsNumber() instanceof Long) {
                // Integer setting (e.g. graceBlocks): Gson would reject 2.5 and fail the whole file, so only integral values pass.
                return x >= 0 && x <= Integer.MAX_VALUE && x == Math.rint(x);
            }
            return x >= 0 && x <= Float.MAX_VALUE && !(key.equals("dropChance") && x > 1);
        }
        return true;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
