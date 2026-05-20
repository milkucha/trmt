package milkucha.trmt.erosion.operation;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import milkucha.trmt.erosion.ErosionThresholdRange;
import milkucha.trmt.erosion.ErosionTransformData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class ErosionOperationFactory {

    private ErosionOperationFactory() {
    }

    public static List<TransformRule> buildRules(List<RuleSpec> specs) {
        return specs.stream()
                .map(spec -> new TransformRule(
                        buildMatcher(spec.identifiers()),
                        Optional.ofNullable(spec.threshold()),
                        spec.operations().stream().map(ErosionOperationFactory::buildOperation).toList()))
                .toList();
    }

    public static ErosionTransformData buildDataFromJson(JsonArray jsonRules) {
        List<RuleSpec> specs = new ArrayList<>();
        for (JsonElement rule : jsonRules) {
            specs.add(parseRuleSpec(rule.getAsJsonObject()));
        }
        return new ErosionTransformData(specs, buildRules(specs));
    }

    public static RuleSpec parseRuleSpec(JsonObject json) {
        List<OperationSpec> operations = new ArrayList<>();
        for (JsonElement operation : json.getAsJsonArray("operations")) {
            operations.add(parseOperationSpec(operation.getAsJsonObject()));
        }

        return new RuleSpec(
                parseIdentifiers(json),
                parseThreshold(json),
                operations
        );
    }

    public static OperationSpec parseOperationSpec(JsonObject json) {
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if ("name".equals(entry.getKey())) continue;
            params.put(entry.getKey(), readJsonParam(entry.getValue()));
        }

        return new OperationSpec(json.get("name").getAsString(), params);
    }

    public static ErosionOperation buildOperation(OperationSpec spec) {
        return switch (spec.name()) {
            case "requires_config" -> new RequiresConfigOperation(getString(spec, "key"));
            case "requires_air" -> new RequiresAirOperation(getString(spec, "side", "top"));
            case "next_stage" -> new NextStageOperation(getInt(spec, "max"));
            case "next_state" -> new NextStateOperation(
                    ErosionTransformSupport.resolveBlock(getString(spec, "id")),
                    getOptionalInt(spec, "stage"),
                    getString(spec, "facing", "none"));
            case "clear_if_no_state" -> new ClearIfNoStateOperation();
            case "stop_tracking" -> new StopTrackingOperation();
            case "apply_state" -> new ApplyStateOperation();
            case "break_block" -> new BreakBlockOperation(getOptionalFloat(spec, "drop_chance"));
            default -> throw new IllegalArgumentException("Unknown erosion operation: " + spec.name());
        };
    }

    private static Object readJsonParam(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsNumber();
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        return value.getAsString();
    }

    public static Predicate<BlockState> buildMatcher(List<String> identifiers) {
        List<Predicate<BlockState>> matchers = identifiers.stream()
                .map(ErosionOperationFactory::buildMatcher)
                .toList();
        return state -> matchers.stream().anyMatch(matcher -> matcher.test(state));
    }

    private static Predicate<BlockState> buildMatcher(String identifier) {
        if (identifier.startsWith("#")) {
            return state -> state.isIn(TagKey.of(RegistryKeys.BLOCK, Identifier.tryParse(identifier.substring(1))));
        }

        Block block = ErosionTransformSupport.resolveBlock(identifier);
        return state -> state.isOf(block);
    }

    public static List<String> parseIdentifiers(JsonObject json) {
        boolean hasIdentifier = json.has("identifier");
        boolean hasIdentifiers = json.has("identifiers");
        if (hasIdentifier == hasIdentifiers) {
            throw new IllegalArgumentException("Erosion rule must define exactly one of identifier or identifiers");
        }

        if (hasIdentifier) {
            return List.of(json.get("identifier").getAsString());
        }

        JsonArray jsonIdentifiers = json.getAsJsonArray("identifiers");
        List<String> identifiers = new ArrayList<>();
        for (JsonElement identifier : jsonIdentifiers) {
            identifiers.add(identifier.getAsString());
        }
        if (identifiers.isEmpty()) {
            throw new IllegalArgumentException("Erosion rule identifiers list cannot be empty");
        }
        return identifiers;
    }

    private static ErosionThresholdRange parseThreshold(JsonObject json) {
        if (!json.has("threshold")) {
            return null;
        }

        JsonObject threshold = json.getAsJsonObject("threshold");
        return new ErosionThresholdRange(
                threshold.get("min").getAsFloat(),
                threshold.get("max").getAsFloat()
        );
    }

    private static String getString(OperationSpec spec, String key) {
        Object value = spec.params().get(key);
        if (value instanceof String string) return string;
        throw new IllegalArgumentException("Operation " + spec.name() + " needs string parameter: " + key);
    }

    private static String getString(OperationSpec spec, String key, String fallback) {
        Object value = spec.params().get(key);
        if (value == null) return fallback;
        if (value instanceof String string) return string;
        throw new IllegalArgumentException("Operation " + spec.name() + " needs string parameter: " + key);
    }

    private static int getInt(OperationSpec spec, String key) {
        Integer value = getOptionalInt(spec, key);
        if (value != null) return value;
        throw new IllegalArgumentException("Operation " + spec.name() + " needs integer parameter: " + key);
    }

    private static Integer getOptionalInt(OperationSpec spec, String key) {
        Object value = spec.params().get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException("Operation " + spec.name() + " needs integer parameter: " + key);
    }

    private static Float getOptionalFloat(OperationSpec spec, String key) {
        Object value = spec.params().get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.floatValue();
        throw new IllegalArgumentException("Operation " + spec.name() + " needs number parameter: " + key);
    }
}
