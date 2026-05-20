package milkucha.trmt.erosion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import milkucha.trmt.erosion.operation.ErosionOperationFactory;
import milkucha.trmt.erosion.operation.ErosionTransformSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeErosionOperationFactory {

    private static final long TICKS_PER_DAY = 24000L;

    private DeErosionOperationFactory() {
    }

    public static DeErosionTransformData buildDataFromJson(JsonArray jsonRules) {
        List<DeErosionRule> rules = new ArrayList<>();
        for (JsonElement rule : jsonRules) {
            rules.add(parseRule(rule.getAsJsonObject()));
        }
        return new DeErosionTransformData(rules);
    }

    private static DeErosionRule parseRule(JsonObject json) {
        List<String> identifiers = ErosionOperationFactory.parseIdentifiers(json);
        return new DeErosionRule(
                ErosionOperationFactory.buildMatcher(identifiers),
                getOptionalString(json, "natural_config"),
                getOptionalString(json, "bonemeal_config"),
                daysToTicks(getFloat(json, "timeout_days", 0.0f)),
                parseTimeoutsByProperty(json),
                parseFallback(json)
        );
    }

    private static Map<String, Map<String, Long>> parseTimeoutsByProperty(JsonObject json) {
        if (!json.has("timeout_days_by_property")) {
            return Map.of();
        }

        Map<String, Map<String, Long>> result = new HashMap<>();
        JsonObject properties = json.getAsJsonObject("timeout_days_by_property");
        for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
            Map<String, Long> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> value : property.getValue().getAsJsonObject().entrySet()) {
                values.put(value.getKey(), daysToTicks(value.getValue().getAsFloat()));
            }
            result.put(property.getKey(), Map.copyOf(values));
        }
        return Map.copyOf(result);
    }

    private static Optional<DeErosionRule.FallbackState> parseFallback(JsonObject json) {
        if (!json.has("fallback")) {
            return Optional.empty();
        }

        JsonObject fallback = json.getAsJsonObject("fallback");
        return Optional.of(new DeErosionRule.FallbackState(
                ErosionTransformSupport.resolveBlock(fallback.get("id").getAsString()),
                parseProperties(fallback),
                parseStringMap(fallback, "property_sources")
        ));
    }

    private static Map<String, String> parseProperties(JsonObject json) {
        Map<String, String> properties = new HashMap<>();
        if (json.has("properties")) {
            JsonObject jsonProperties = json.getAsJsonObject("properties");
            for (Map.Entry<String, JsonElement> property : jsonProperties.entrySet()) {
                properties.put(property.getKey(), property.getValue().getAsString());
            }
        }
        return properties;
    }

    private static Map<String, String> parseStringMap(JsonObject json, String key) {
        if (!json.has(key)) {
            return Map.of();
        }

        Map<String, String> values = new HashMap<>();
        JsonObject jsonValues = json.getAsJsonObject(key);
        for (Map.Entry<String, JsonElement> value : jsonValues.entrySet()) {
            values.put(value.getKey(), value.getValue().getAsString());
        }
        return values;
    }

    private static Optional<String> getOptionalString(JsonObject json, String key) {
        return json.has(key) ? Optional.of(json.get(key).getAsString()) : Optional.empty();
    }

    private static float getFloat(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static long daysToTicks(float days) {
        return (long) (days * TICKS_PER_DAY);
    }
}
