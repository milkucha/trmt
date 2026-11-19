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
        /** Free-roaming mobs listed in {@code erosionMobWhitelist}: step amount is {@code player × freeRoaming}. */
        public float freeRoaming = 1.0f;
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

    /** Permanent erosion stages: {@code true} = that stage never de-erodes naturally (mirrors {@link DeErosionTimeoutDays}). */
    public static class GrassPermanent {
        public boolean stage1 = false;
        public boolean stage2 = false;
        public boolean stage3 = false;
        public boolean stage4 = false;
        public boolean stage5 = false;
    }

    public static class DirtPermanent {
        public boolean erodedDirt       = false;
        public boolean erodedCoarseDirt = false;
    }

    public static class SandPermanent {
        public boolean stage1 = false;
        public boolean stage2 = false;
        public boolean stage3 = false;
        public boolean stage4 = false;
        public boolean stage5 = false;
    }

    public static class PermanentStages {
        public GrassPermanent grass = new GrassPermanent();
        public DirtPermanent  dirt  = new DirtPermanent();
        public SandPermanent  sand  = new SandPermanent();
    }

    public static class DeErosionToggles {
        public boolean grassEnabled = true;
        public boolean dirtEnabled  = true;
        public boolean sandEnabled  = true;
    }

    // ── top-level fields ───────────────────────────────────────────────────

    public ErosionToggles       erosion              = new ErosionToggles();
    public DeErosionToggles     deErosion            = new DeErosionToggles();
    public Multipliers          erosionMultipliers   = new Multipliers();
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
    /** Entity type ids (e.g. "minecraft:cow") whose free-roaming (unleashed) members erode like leashed mobs. Empty = only leashed mobs erode. */
    public List<String> erosionMobWhitelist = new ArrayList<>();
    public DeErosionTimeoutDays deErosionTimeoutDays = new DeErosionTimeoutDays();
    public PermanentStages      permanentStages      = new PermanentStages();

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
     * as it was when {@code keepPreviousOnInvalid} is set (used by {@code /trmt reload-config}).
     */
    public static LoadResult load(boolean keepPreviousOnInvalid) {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTConfig loaded = parse(reader, new TRMTConfig(), TRMTConfig.class, FILE_NAME);
                if (loaded != null) {
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
            return x >= 0 && x <= Float.MAX_VALUE && !(key.equals("dropChance") && x > 1);
        }
        return true;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
