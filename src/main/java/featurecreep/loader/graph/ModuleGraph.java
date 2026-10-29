package featurecreep.loader.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

import featurecreep.loader.FCVersionRange;

/**
 * Dependency graph over modules. {@link #resolve()} decides which modules can load,
 * their load order, and warnings about optional or conflicting edges.
 *
 * <p>Edges of kind {@link Kind#REQUIRED} make the dependent unloadable when the target is
 * missing, out of range, or unloadable. Other kinds never block loading.
 */
public final class ModuleGraph {

    /** Dependency strength. {@code CONFLICTS} never orders or blocks modules. */
    public enum Kind { REQUIRED, OPTIONAL, RECOMMENDS, SUGGESTS, CONFLICTS }

    /**
     * Result of {@link ModuleGraph#resolve()}.
     *
     * @param loadOrder  loadable module ids, dependencies before dependents
     * @param unloadable module id to the reason it cannot load
     * @param warnings   non-fatal findings for loadable modules and dropped edges
     */
    public record Resolution(List<String> loadOrder, Map<String, String> unloadable, List<String> warnings) {
        public Resolution {
            loadOrder = List.copyOf(loadOrder);
            unloadable = Collections.unmodifiableMap(new LinkedHashMap<>(unloadable));
            warnings = List.copyOf(warnings);
        }
    }

    private record Edge(String from, String to, Kind kind, FCVersionRange range) {
    }

    private final List<String> ids = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, String> versions = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    /**
     * Registers a module. A null or blank version is stored as no version.
     *
     * @throws IllegalArgumentException if the id is already registered or the version is malformed
     */
    public void addModule(String id, String version) {
        Objects.requireNonNull(id, "id");
        if (index.containsKey(id)) {
            throw new IllegalArgumentException("duplicate module id '" + id + "'");
        }
        String normalized = version == null || version.isBlank() ? null : version.trim();
        if (normalized != null) {
            FCVersionRange.compareVersions(normalized, normalized);
        }
        index.put(id, ids.size());
        ids.add(id);
        versions.put(id, normalized);
    }

    /**
     * Declares that {@code from} depends on {@code to} with the given kind.
     *
     * @param versionRange a {@link FCVersionRange} specification; null means any version
     * @throws IllegalArgumentException if the range is malformed
     */
    public void addDependency(String from, String to, Kind kind, String versionRange) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        edges.add(new Edge(from, to, kind, FCVersionRange.parse(versionRange)));
    }

    public boolean isPresent(String id) {
        return index.containsKey(id);
    }

    public Resolution resolve() {
        Map<String, String> unloadable = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        Map<String, List<Edge>> outgoing = new HashMap<>();
        Map<String, List<String>> requiredForward = new HashMap<>();
        Map<String, List<String>> requiredBackward = new HashMap<>();
        for (Edge edge : edges) {
            if (!index.containsKey(edge.from())) {
                continue;
            }
            outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            if (edge.kind() == Kind.REQUIRED && index.containsKey(edge.to())) {
                requiredForward.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
                requiredBackward.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge.from());
            }
        }

        for (String id : ids) {
            for (Edge edge : outgoing.getOrDefault(id, List.of())) {
                if (edge.kind() != Kind.REQUIRED) {
                    continue;
                }
                String detail = unmetDetail(edge);
                if (detail != null) {
                    unloadable.putIfAbsent(id, "requires " + edge.to() + " " + rangeText(edge) + " but " + detail);
                }
            }
        }

        for (List<String> component : components(ids, requiredForward)) {
            if (isCyclic(component, requiredForward)) {
                String reason = "required cycle among " + sortByIndex(component);
                for (String member : sortByIndex(component)) {
                    unloadable.putIfAbsent(member, reason);
                }
            }
        }

        Deque<String> pending = new ArrayDeque<>(unloadable.keySet());
        while (!pending.isEmpty()) {
            String failed = pending.poll();
            for (String dependent : requiredBackward.getOrDefault(failed, List.of())) {
                if (!unloadable.containsKey(dependent)) {
                    unloadable.put(dependent, "requires " + failed + " which cannot be loaded");
                    pending.add(dependent);
                }
            }
        }

        List<String> loadable = new ArrayList<>();
        for (String id : ids) {
            if (!unloadable.containsKey(id)) {
                loadable.add(id);
            }
        }

        Map<String, List<Edge>> ordering = new HashMap<>();
        Map<String, List<String>> orderingTargets = new HashMap<>();
        for (String id : loadable) {
            for (Edge edge : outgoing.getOrDefault(id, List.of())) {
                if (edge.kind() == Kind.CONFLICTS || !inRange(edge) || unloadable.containsKey(edge.to())) {
                    continue;
                }
                ordering.computeIfAbsent(id, k -> new ArrayList<>()).add(edge);
                orderingTargets.computeIfAbsent(id, k -> new ArrayList<>()).add(edge.to());
            }
        }

        Map<String, Integer> componentOf = new HashMap<>();
        List<List<String>> components = components(loadable, orderingTargets);
        for (int c = 0; c < components.size(); c++) {
            for (String member : components.get(c)) {
                componentOf.put(member, c);
            }
        }

        List<Edge> retained = new ArrayList<>();
        for (String id : loadable) {
            for (Edge edge : ordering.getOrDefault(id, List.of())) {
                boolean inCycle = componentOf.get(edge.from()).equals(componentOf.get(edge.to()));
                if (inCycle && edge.kind() != Kind.REQUIRED) {
                    warnings.add("dropped " + edge.kind().name().toLowerCase(Locale.ROOT) + " dependency "
                            + edge.from() + " -> " + edge.to() + " to break a cycle among "
                            + sortByIndex(components.get(componentOf.get(edge.from()))));
                } else {
                    retained.add(edge);
                }
            }
        }

        Map<String, Integer> pendingDependencies = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String id : loadable) {
            pendingDependencies.put(id, 0);
        }
        for (Edge edge : retained) {
            pendingDependencies.merge(edge.from(), 1, Integer::sum);
            dependents.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge.from());
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (String id : loadable) {
            if (pendingDependencies.get(id) == 0) {
                ready.add(index.get(id));
            }
        }
        List<String> loadOrder = new ArrayList<>(loadable.size());
        while (!ready.isEmpty()) {
            String id = ids.get(ready.poll());
            loadOrder.add(id);
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                if (pendingDependencies.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(index.get(dependent));
                }
            }
        }
        if (loadOrder.size() != loadable.size()) {
            throw new IllegalStateException("dependency cycle remains after breaking non-required edges");
        }

        for (String id : loadable) {
            for (Edge edge : outgoing.getOrDefault(id, List.of())) {
                switch (edge.kind()) {
                    case RECOMMENDS, SUGGESTS -> {
                        String detail = unmetDetail(edge);
                        if (detail == null && unloadable.containsKey(edge.to())) {
                            detail = "it cannot be loaded";
                        }
                        if (detail != null) {
                            warnings.add(id + " " + edge.kind().name().toLowerCase(Locale.ROOT) + " " + edge.to()
                                    + " " + rangeText(edge) + " but " + detail);
                        }
                    }
                    case CONFLICTS -> {
                        if (inRange(edge)) {
                            warnings.add(id + " conflicts with " + edge.to() + " " + rangeText(edge)
                                    + " (found " + versionText(edge.to()) + ")");
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        return new Resolution(loadOrder, unloadable, warnings);
    }

    /** Null when the target is present and in range, otherwise a human-readable detail. */
    private String unmetDetail(Edge edge) {
        if (!index.containsKey(edge.to())) {
            return "it is not present";
        }
        if (!edge.range().matches(versions.get(edge.to()))) {
            return "found " + versionText(edge.to());
        }
        return null;
    }

    private boolean inRange(Edge edge) {
        return index.containsKey(edge.to()) && edge.range().matches(versions.get(edge.to()));
    }

    private String versionText(String id) {
        String version = versions.get(id);
        return version == null ? "no version" : version;
    }

    private static String rangeText(Edge edge) {
        return edge.range().isAny() ? "*" : edge.range().getSpec();
    }

    private List<String> sortByIndex(List<String> members) {
        List<String> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingInt(index::get));
        return sorted;
    }

    private static boolean isCyclic(List<String> component, Map<String, List<String>> adjacency) {
        if (component.size() > 1) {
            return true;
        }
        String only = component.get(0);
        return adjacency.getOrDefault(only, List.of()).contains(only);
    }

    private static List<List<String>> components(List<String> nodes, Map<String, List<String>> adjacency) {
        Tarjan tarjan = new Tarjan(adjacency);
        for (String node : nodes) {
            if (!tarjan.index.containsKey(node)) {
                tarjan.visit(node);
            }
        }
        return tarjan.components;
    }

    private static final class Tarjan {
        private final Map<String, List<String>> adjacency;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> low = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();
        private int counter;

        Tarjan(Map<String, List<String>> adjacency) {
            this.adjacency = adjacency;
        }

        void visit(String node) {
            int order = counter++;
            index.put(node, order);
            low.put(node, order);
            stack.push(node);
            onStack.add(node);
            for (String next : adjacency.getOrDefault(node, List.of())) {
                if (!index.containsKey(next)) {
                    visit(next);
                    low.put(node, Math.min(low.get(node), low.get(next)));
                } else if (onStack.contains(next)) {
                    low.put(node, Math.min(low.get(node), index.get(next)));
                }
            }
            if (low.get(node).intValue() == order) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(component);
            }
        }
    }
}
