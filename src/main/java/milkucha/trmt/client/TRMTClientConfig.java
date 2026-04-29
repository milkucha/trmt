package milkucha.trmt.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only configuration loaded from {@code config/trmt-client.json}.
 * Contains settings that only affect the local client (display preferences, HUD toggles).
 * Edit the JSON file and restart the game to apply changes.
 */
public final class TRMTClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "trmt-client.json";

    /** Show the erosion debug HUD overlay. Set to true for development. */
    public boolean debugHud = false;

    // ── singleton ──────────────────────────────────────────────────────────
    private static TRMTClientConfig instance = new TRMTClientConfig();

    private TRMTClientConfig() {}

    public static TRMTClientConfig get() {
        return instance;
    }

    // ── load / save ────────────────────────────────────────────────────────

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTClientConfig loaded = GSON.fromJson(reader, TRMTClientConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    save();
                    return;
                }
            } catch (IOException e) {
                // Fall through to write defaults.
            }
        }
        save();
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException ignored) {}
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
