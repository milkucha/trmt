package milkucha.trmt.erosion.operation;

import milkucha.trmt.erosion.ErosionThresholdRange;

import java.util.List;

public record RuleSpec(List<String> identifiers, ErosionThresholdRange threshold, List<OperationSpec> operations) {
}
