package milkucha.trmt.erosion;

import milkucha.trmt.erosion.operation.RuleSpec;
import milkucha.trmt.erosion.operation.TransformRule;

import java.util.List;

public record ErosionTransformData(List<RuleSpec> specs, List<TransformRule> rules) {
}
