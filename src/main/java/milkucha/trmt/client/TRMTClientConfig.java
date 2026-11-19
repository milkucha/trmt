package milkucha.trmt.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTNeoForge;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only configuration loaded from {@code config/trmt-client.json}.
 * Edit the JSON file and restart the game to apply changes ({@code debugHud} can also be set in-game with {@code /trmt-hud};
 * {@code mipmapFix} is re-read on every resource reload (F3+T) and chunk reload (F3+A), see {@link #refreshMipmapFix()}).
 */
public final class TRMTClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "trmt-client.json";

    /** Show the erosion debug HUD overlay. Set to true for development. */
    public boolean debugHud = false;

    /**
     * Dithered far-distance rendering of the worn-grass overlay (reduces shimmer at distance).
     * Off = the overlay renders exactly as before the fix. Applied by a resource reload
     * (F3+T, which rebuilds the block atlas with the overlay's extra copies and mip data); turning it off
     * also takes effect on a chunk reload (F3+A). See milkucha.trmt.client.render.MipmapFix.
     */
    public volatile boolean mipmapFix = false;

    private static TRMTClientConfig instance = new TRMTClientConfig();

    private TRMTClientConfig() {}

    public static TRMTClientConfig get() {
        return instance;
    }

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTClientConfig loaded = TRMTConfig.parse(reader, new TRMTClientConfig(), TRMTClientConfig.class, FILE_NAME);
                if (loaded != null) {
                    instance = loaded;
                    save();
                    return;
                }
            } catch (JsonParseException e) {
                TRMTNeoForge.LOGGER.error("[TRMT] {} is not valid JSON, using defaults (file left unchanged): {}", path, e.getMessage());
                return;
            } catch (IOException e) {
                // Fall through to write defaults.
            }
        }
        save();
    }

    /**
     * Re-reads only {@code mipmapFix} from disk (no write, other settings untouched). Called from
     * worker threads during a resource reload as well as from the render thread; on any error the
     * current value is kept. Returns the value now in effect.
     */
    public static boolean refreshMipmapFix() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                TRMTClientConfig loaded = TRMTConfig.parse(reader, new TRMTClientConfig(), TRMTClientConfig.class, FILE_NAME);
                if (loaded != null) instance.mipmapFix = loaded.mipmapFix;
            } catch (JsonParseException | IOException e) {
                TRMTNeoForge.LOGGER.error("[TRMT] could not re-read {}, keeping mipmapFix={}: {}", path, instance.mipmapFix, e.getMessage());
            }
        }
        return instance.mipmapFix;
    }

    public static void save() {
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
