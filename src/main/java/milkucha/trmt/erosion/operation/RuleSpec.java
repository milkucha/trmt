package milkucha.trmt.erosion.operation;

import milkucha.trmt.erosion.ErosionThresholdRange;

import java.util.List;

public record RuleSpec(String identifier, ErosionThresholdRange threshold, List<OperationSpec> operations) {
}
