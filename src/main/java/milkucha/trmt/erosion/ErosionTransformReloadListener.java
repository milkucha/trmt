package milkucha.trmt.erosion;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import milkucha.trmt.TRMT;
import milkucha.trmt.erosion.operation.ErosionOperationFactory;
import milkucha.trmt.erosion.operation.RuleSpec;
import milkucha.trmt.erosion.operation.TransformRule;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ErosionTransformReloadListener implements SimpleSynchronousResourceReloadListener {

    public static final ErosionTransformReloadListener INSTANCE = new ErosionTransformReloadListener();

    private static final Identifier ID = Identifier.of(TRMT.MOD_ID, "erosion_transforms");
    private static final String DIRECTORY = "trmt/erosion_transforms";

    private ErosionTransformReloadListener() {
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        List<RuleSpec> specs = new ArrayList<>();
        List<TransformRule> rules = new ArrayList<>();
        Map<Identifier, Resource> resources = manager.findResources(
                DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        resources.entrySet().stream()
                .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
                .forEach(entry -> loadRules(entry.getKey(), entry.getValue(), specs, rules));

        ErosionTransformGraph.Report report = ErosionTransformGraph.inspect(specs);
        if (report.hasCycles()) {
            ErosionLogic.replaceTransformRules(List.of(), false);
            ErosionMapManager.getInstance().broadcastMessage(Text.literal(
                    "[TRMT] Erosion transform DAG contains a cycle. Erosion transforms are disabled; run /trmt erosion-dag for details."
            ));
            TRMT.LOGGER.error("[TRMT] Erosion transforms disabled because {} cycle(s) were detected.", report.cycles().size());
            return;
        }

        ErosionLogic.replaceTransformRules(rules, true);
        TRMT.LOGGER.info("[TRMT] Loaded {} erosion transform rule file(s).", resources.size());
    }

    private void loadRules(Identifier id, Resource resource, List<RuleSpec> specs, List<TransformRule> rules) {
        try (Reader reader = resource.getReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) {
                throw new IllegalStateException("Erosion transform file must contain a JSON array: " + id);
            }

            ErosionTransformData data = ErosionOperationFactory.buildDataFromJson(json.getAsJsonArray());
            specs.addAll(data.specs());
            rules.addAll(data.rules());
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load erosion transform file: " + id, e);
        }
    }
}
