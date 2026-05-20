package milkucha.trmt.erosion;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import milkucha.trmt.TRMT;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DeErosionTransformReloadListener implements SimpleSynchronousResourceReloadListener {

    public static final DeErosionTransformReloadListener INSTANCE = new DeErosionTransformReloadListener();

    private static final Identifier ID = Identifier.of(TRMT.MOD_ID, "deerosion_transforms");
    private static final String DIRECTORY = "trmt/deerosion_transforms";

    private DeErosionTransformReloadListener() {
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        List<DeErosionRule> rules = new ArrayList<>();
        Map<Identifier, Resource> resources = manager.findResources(
                DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        resources.entrySet().stream()
                .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
                .forEach(entry -> loadRules(entry.getKey(), entry.getValue(), rules));

        DeErosionLogic.replaceRules(rules);
        TRMT.LOGGER.info("[TRMT] Loaded {} de-erosion transform rule file(s).", resources.size());
    }

    private void loadRules(Identifier id, Resource resource, List<DeErosionRule> rules) {
        try (Reader reader = resource.getReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) {
                throw new IllegalStateException("De-erosion transform file must contain a JSON array: " + id);
            }

            DeErosionTransformData data = DeErosionOperationFactory.buildDataFromJson(json.getAsJsonArray());
            rules.addAll(data.rules());
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load de-erosion transform file: " + id, e);
        }
    }
}
