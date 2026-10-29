package featurecreep.loader.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import featurecreep.loader.graph.ModuleGraph.Kind;
import featurecreep.loader.graph.ModuleGraph.Resolution;

class ModuleGraphTest {

    @Test
    void dependenciesLoadBeforeDependents() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addModule("c", "1.0");
        graph.addDependency("a", "b", Kind.REQUIRED, null);
        graph.addDependency("b", "c", Kind.REQUIRED, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("c", "b", "a"), result.loadOrder());
        assertTrue(result.unloadable().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void independentModulesKeepInsertionOrder() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("zeta", null);
        graph.addModule("alpha", null);
        graph.addModule("mid", null);

        assertEquals(List.of("zeta", "alpha", "mid"), graph.resolve().loadOrder());
    }

    @Test
    void tieBreakPrefersEarlierInsertionAmongReadyModules() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("b", "1.0");
        graph.addModule("a", "1.0");
        graph.addModule("c", "1.0");
        graph.addDependency("c", "b", Kind.REQUIRED, null);

        assertEquals(List.of("b", "a", "c"), graph.resolve().loadOrder());
    }

    @Test
    void requiredMissingModuleIsUnloadable() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addDependency("a", "x", Kind.REQUIRED, null);

        Resolution result = graph.resolve();

        assertEquals(List.of(), result.loadOrder());
        assertEquals("requires x * but it is not present", result.unloadable().get("a"));
    }

    @Test
    void requiredOutOfRangeModuleIsUnloadable() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addDependency("a", "b", Kind.REQUIRED, ">=2.0");

        Resolution result = graph.resolve();

        assertEquals(List.of("b"), result.loadOrder());
        assertEquals("requires b >=2.0 but found 1.0", result.unloadable().get("a"));
    }

    @Test
    void requiredRangeAcceptsUnionAlternatives() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "3.4");
        graph.addDependency("a", "b", Kind.REQUIRED, "^1.0 || ^3.0");

        assertTrue(graph.resolve().unloadable().isEmpty());
    }

    @Test
    void optionalMissingModuleIsSilent() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addDependency("a", "x", Kind.OPTIONAL, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("a"), result.loadOrder());
        assertTrue(result.unloadable().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void recommendsMissingModuleWarns() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addDependency("a", "x", Kind.RECOMMENDS, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("a"), result.loadOrder());
        assertEquals(List.of("a recommends x * but it is not present"), result.warnings());
    }

    @Test
    void suggestsOutOfRangeModuleWarns() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addDependency("a", "b", Kind.SUGGESTS, ">=2.0");

        Resolution result = graph.resolve();

        assertEquals(List.of("a", "b"), result.loadOrder());
        assertEquals(List.of("a suggests b >=2.0 but found 1.0"), result.warnings());
    }

    @Test
    void conflictingModulePresentWarnsWithoutOrdering() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "2.0");
        graph.addDependency("a", "b", Kind.CONFLICTS, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("a", "b"), result.loadOrder());
        assertTrue(result.unloadable().isEmpty());
        assertEquals(List.of("a conflicts with b * (found 2.0)"), result.warnings());
    }

    @Test
    void conflictOutOfRangeIsSilent() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addDependency("a", "b", Kind.CONFLICTS, ">=2.0");

        assertTrue(graph.resolve().warnings().isEmpty());
    }

    @Test
    void conflictingMissingModuleIsSilent() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addDependency("a", "x", Kind.CONFLICTS, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("a"), result.loadOrder());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void requiredCycleMembersAreUnloadableAndDependentsFollow() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addModule("c", "1.0");
        graph.addModule("d", "1.0");
        graph.addDependency("a", "b", Kind.REQUIRED, null);
        graph.addDependency("b", "a", Kind.REQUIRED, null);
        graph.addDependency("c", "a", Kind.REQUIRED, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("d"), result.loadOrder());
        assertEquals("required cycle among [a, b]", result.unloadable().get("a"));
        assertEquals("required cycle among [a, b]", result.unloadable().get("b"));
        assertEquals("requires a which cannot be loaded", result.unloadable().get("c"));
    }

    @Test
    void requiredSelfDependencyIsUnloadable() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addDependency("a", "a", Kind.REQUIRED, null);

        assertTrue(graph.resolve().unloadable().containsKey("a"));
    }

    @Test
    void dependentsOfUnloadableModulesAreUnloadable() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addModule("c", "1.0");
        graph.addModule("d", "1.0");
        graph.addDependency("a", "x", Kind.REQUIRED, null);
        graph.addDependency("b", "a", Kind.REQUIRED, null);
        graph.addDependency("c", "b", Kind.REQUIRED, null);
        graph.addDependency("d", "a", Kind.OPTIONAL, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("d"), result.loadOrder());
        assertEquals("requires a which cannot be loaded", result.unloadable().get("b"));
        assertEquals("requires b which cannot be loaded", result.unloadable().get("c"));
        assertFalse(result.unloadable().containsKey("d"));
    }

    @Test
    void optionalCycleIsBrokenWithWarningAndStillLoads() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addDependency("a", "b", Kind.REQUIRED, null);
        graph.addDependency("b", "a", Kind.OPTIONAL, null);

        Resolution result = graph.resolve();

        assertEquals(List.of("b", "a"), result.loadOrder());
        assertTrue(result.unloadable().isEmpty());
        assertEquals(List.of("dropped optional dependency b -> a to break a cycle among [a, b]"),
                result.warnings());
    }

    @Test
    void recommendsOnUnloadableModuleWarns() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addModule("b", "1.0");
        graph.addDependency("a", "b", Kind.REQUIRED, null);
        graph.addDependency("b", "x", Kind.REQUIRED, null);
        graph.addDependency("c", "b", Kind.RECOMMENDS, null);
        graph.addModule("c", "1.0");

        Resolution result = graph.resolve();

        assertEquals(List.of("c"), result.loadOrder());
        assertEquals(List.of("c recommends b * but it cannot be loaded"), result.warnings());
    }

    @Test
    void resolutionCollectionsAreImmutable() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        graph.addDependency("a", "x", Kind.REQUIRED, null);

        Resolution result = graph.resolve();

        assertThrows(UnsupportedOperationException.class, () -> result.loadOrder().add("z"));
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("z"));
        assertThrows(UnsupportedOperationException.class, () -> result.unloadable().put("z", "z"));
    }

    @Test
    void duplicateModuleIdIsRejected() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        assertThrows(IllegalArgumentException.class, () -> graph.addModule("a", "2.0"));
    }

    @Test
    void malformedModuleVersionIsRejected() {
        ModuleGraph graph = new ModuleGraph();
        assertThrows(IllegalArgumentException.class, () -> graph.addModule("a", "not a version"));
    }

    @Test
    void malformedDependencyRangeIsRejected() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", "1.0");
        assertThrows(IllegalArgumentException.class,
                () -> graph.addDependency("a", "b", Kind.REQUIRED, "[1.0"));
    }

    @Test
    void presenceReflectsRegisteredModules() {
        ModuleGraph graph = new ModuleGraph();
        graph.addModule("a", null);
        assertTrue(graph.isPresent("a"));
        assertFalse(graph.isPresent("b"));
    }

    @Test
    void emptyGraphResolvesToEmptyResult() {
        Resolution result = new ModuleGraph().resolve();

        assertEquals(List.of(), result.loadOrder());
        assertEquals(Map.of(), result.unloadable());
        assertEquals(List.of(), result.warnings());
    }
}
