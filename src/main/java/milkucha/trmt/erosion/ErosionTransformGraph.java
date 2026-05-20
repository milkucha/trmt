package milkucha.trmt.erosion;

import milkucha.trmt.TRMT;
import milkucha.trmt.erosion.operation.OperationSpec;
import milkucha.trmt.erosion.operation.RuleSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ErosionTransformGraph {

    private static Report latestReport = new Report(List.of(), List.of());

    private ErosionTransformGraph() {
    }

    public static Report inspect(List<RuleSpec> specs) {
        Map<String, List<String>> edges = buildEdges(specs);
        List<String> cycles = findCycles(edges);
        List<String> paths = findPaths(edges);

        latestReport = new Report(paths, cycles);
        cycles.forEach(cycle -> TRMT.LOGGER.warn("[TRMT] Erosion transform cycle detected: {}", cycle));
        paths.forEach(path -> TRMT.LOGGER.info("[TRMT] Erosion path: {}", path));
        return latestReport;
    }

    public static Report latestReport() {
        return latestReport;
    }

    private static Map<String, List<String>> buildEdges(List<RuleSpec> specs) {
        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (RuleSpec spec : specs) {
            for (String source : spec.identifiers()) {
                for (OperationSpec operation : spec.operations()) {
                    if (!"next_state".equals(operation.name())) {
                        continue;
                    }

                    Object id = operation.params().get("id");
                    if (id instanceof String target) {
                        edges.computeIfAbsent(source, ignored -> new ArrayList<>())
                                .add(target);
                    }
                }
                edges.computeIfAbsent(source, ignored -> new ArrayList<>());
            }
        }
        return edges;
    }

    private static List<String> findCycles(Map<String, List<String>> edges) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        Set<String> reported = new LinkedHashSet<>();

        for (String node : edges.keySet()) {
            findCycles(node, edges, visited, visiting, path, reported);
        }
        return List.copyOf(reported);
    }

    private static void findCycles(String node, Map<String, List<String>> edges, Set<String> visited,
                                   Set<String> visiting, ArrayDeque<String> path, Set<String> reported) {
        if (visited.contains(node)) {
            return;
        }
        if (visiting.contains(node)) {
            List<String> cycle = new ArrayList<>();
            boolean inCycle = false;
            for (String pathNode : path) {
                if (pathNode.equals(node)) {
                    inCycle = true;
                }
                if (inCycle) {
                    cycle.add(pathNode);
                }
            }
            cycle.add(node);

            reported.add(String.join(" -> ", cycle));
            return;
        }

        visiting.add(node);
        path.addLast(node);
        for (String target : edges.getOrDefault(node, List.of())) {
            findCycles(target, edges, visited, visiting, path, reported);
        }
        path.removeLast();
        visiting.remove(node);
        visited.add(node);
    }

    private static List<String> findPaths(Map<String, List<String>> edges) {
        Set<String> targets = new HashSet<>();
        edges.values().forEach(targets::addAll);

        List<String> roots = edges.keySet().stream()
                .filter(node -> !targets.contains(node))
                .toList();
        List<String> starts = roots.isEmpty() ? new ArrayList<>(edges.keySet()) : roots;

        Set<String> logged = new LinkedHashSet<>();
        for (String start : starts) {
            logPathsFrom(start, edges, new ArrayList<>(), new HashSet<>(), logged);
        }
        return List.copyOf(logged);
    }

    private static void logPathsFrom(String node, Map<String, List<String>> edges, List<String> path,
                                     Set<String> visiting, Set<String> logged) {
        if (visiting.contains(node)) {
            logCyclePath(path, node, logged);
            return;
        }

        visiting.add(node);
        path.add(node);

        List<String> targets = edges.getOrDefault(node, List.of());
        if (targets.isEmpty()) {
            logPath(path, logged);
        } else {
            for (String target : targets) {
                logPathsFrom(target, edges, path, visiting, logged);
            }
        }

        path.remove(path.size() - 1);
        visiting.remove(node);
    }

    private static void logPath(List<String> path, Set<String> logged) {
        if (path.size() < 2) {
            return;
        }

        logged.add(String.join(" -> ", path));
    }

    private static void logCyclePath(List<String> path, String repeatedNode, Set<String> logged) {
        if (path.isEmpty()) {
            return;
        }

        List<String> cyclePath = new ArrayList<>(path);
        cyclePath.add(repeatedNode);
        logged.add(String.join(" -> ", cyclePath) + " (cycle)");
    }

    public record Report(List<String> paths, List<String> cycles) {
        public boolean hasCycles() {
            return !cycles.isEmpty();
        }
    }
}
