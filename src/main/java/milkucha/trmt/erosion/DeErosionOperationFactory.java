package milkucha.trmt.erosion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import milkucha.trmt.erosion.operation.ErosionOperationFactory;
import milkucha.trmt.erosion.operation.ErosionTransformSupport;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
                parseItemTriggers(json),
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

    private static Map<Item, DeErosionRule.ItemTrigger> parseItemTriggers(JsonObject json) {
        if (!json.has("item_triggers")) {
            return Map.of();
        }

        Map<Item, DeErosionRule.ItemTrigger> triggers = new HashMap<>();
        JsonObject itemTriggers = json.getAsJsonObject("item_triggers");
        for (Map.Entry<String, JsonElement> trigger : itemTriggers.entrySet()) {
            Item item = resolveItem(trigger.getKey());
            JsonObject triggerSpec = trigger.getValue().getAsJsonObject();
            String mode = getString(triggerSpec, "mode", "instant");
            if (!mode.equals("instant") && !mode.equals("hold")) {
                throw new IllegalArgumentException("Unsupported de-erosion item trigger mode: " + mode);
            }

            int consume = getNonNegativeInt(triggerSpec, "consume", 0);
            int damage = getNonNegativeInt(triggerSpec, "damage", 0);
            int ticks = getNonNegativeInt(triggerSpec, "ticks", 20);
            triggers.put(item, new DeErosionRule.ItemTrigger(
                    getOptionalString(triggerSpec, "config"),
                    mode,
                    consume,
                    damage,
                    ticks,
                    getInt(triggerSpec, "world_event", 0)
            ));
        }
        return Map.copyOf(triggers);
    }

    private static Item resolveItem(String identifier) {
        Identifier id = Identifier.tryParse(identifier.toLowerCase());
        if (id == null) {
            throw new IllegalArgumentException("Invalid de-erosion item identifier: " + identifier);
        }
        return Registries.ITEM.getOrEmpty(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown de-erosion item identifier: " + identifier));
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

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static int getNonNegativeInt(JsonObject json, String key, int fallback) {
        int value = getInt(json, key, fallback);
        if (value < 0) {
            throw new IllegalArgumentException("De-erosion item trigger parameter must be non-negative: " + key);
        }
        return value;
    }

    private static float getFloat(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static long daysToTicks(float days) {
        return (long) (days * TICKS_PER_DAY);
    }
}
