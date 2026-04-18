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

    // ── defaults (match the hardcoded values that existed before) ──────────
    public float grassBlockMin  = 2.0f;
    public float grassBlockMax  = 4.0f;

    public float dirtMin        = 4.0f;
    public float dirtMax        = 8.0f;

    public float coarseDirtMin  = 8.0f;
    public float coarseDirtMax  = 12.0f;

    public float vegetationMin        = 2.0f;
    public float vegetationMax        = 3.0f;
    /** 0.0 = never drops, 1.0 = always drops. Applied per-break as a random roll. */
    public float vegetationDropChance = 0.2f;

    /** Multiplier applied to all erosion amounts for every player step. Default 1.0 = vanilla behaviour. */
    public float playerErosionMultiplier = 0.5f;

    /** Multiplier applied to all erosion amounts when the player is riding a vehicle (e.g. a horse). */
    public float mountedErosionMultiplier = 1.5f;

    /** Show the erosion debug HUD overlay. Disabled by default; set to true for development. */
    public boolean debugHud = false;

    // ── de-erosion timeouts (ticks) ────────────────────────────────────────
    // Ticks of inactivity before a block reverts one step toward un-eroded state.

    public long deErosionTimeoutTicks_grassStage1 = 12000L;
    public long deErosionTimeoutTicks_grassStage2 = 24000L;
    public long deErosionTimeoutTicks_grassStage3 = 72000L;
    public long deErosionTimeoutTicks_grassStage4 = 120000L;
    public long deErosionTimeoutTicks_grassStage5 = 168000L;

    public long deErosionTimeoutTicks_erodedDirt       = 24000L;
    public long deErosionTimeoutTicks_erodedCoarseDirt = 72000L;
    public long deErosionTimeoutTicks_erodedRootedDirt = 168000L;

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
