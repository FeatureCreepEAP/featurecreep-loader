package featurecreep.loader.entrypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import featurecreep.loader.ExecutionSide;

class EntrypointTest {

    @Test
    void adapterDefaultsToJava() {
        assertEquals(Entrypoint.JAVA_ADAPTER, new Entrypoint("main", "example.Main", null).adapter());
        assertEquals(Entrypoint.JAVA_ADAPTER, new Entrypoint("main", "example.Main", "  ").adapter());
    }

    @Test
    void trimsFields() {
        Entrypoint entrypoint = new Entrypoint(" client ", " example.Client ", " java ");
        assertEquals("client", entrypoint.type());
        assertEquals("example.Client", entrypoint.className());
        assertEquals("java", entrypoint.adapter());
    }

    @Test
    void rejectsMissingTypeOrClass() {
        assertThrows(IllegalArgumentException.class, () -> new Entrypoint(null, "example.Main", null));
        assertThrows(IllegalArgumentException.class, () -> new Entrypoint(" ", "example.Main", null));
        assertThrows(IllegalArgumentException.class, () -> new Entrypoint("main", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Entrypoint("main", "", null));
    }

    @Test
    void mapsTypesToPhases() {
        assertEquals(EntrypointPhase.PRE_LAUNCH, new Entrypoint("preLaunch", "a.B", null).phase());
        assertEquals(EntrypointPhase.MAIN, new Entrypoint("main", "a.B", null).phase());
        assertEquals(EntrypointPhase.READY, new Entrypoint("client", "a.B", null).phase());
        assertEquals(EntrypointPhase.READY, new Entrypoint("server", "a.B", null).phase());
        assertEquals(EntrypointPhase.READY, new Entrypoint("custom", "a.B", null).phase());
    }

    @Test
    void clientAndServerRunOnlyOnTheirSide() {
        Entrypoint client = new Entrypoint("client", "a.Client", null);
        Entrypoint server = new Entrypoint("server", "a.Server", null);

        assertTrue(client.runsOn(ExecutionSide.CLIENT));
        assertFalse(client.runsOn(ExecutionSide.SERVER));
        assertTrue(server.runsOn(ExecutionSide.SERVER));
        assertFalse(server.runsOn(ExecutionSide.CLIENT));
    }

    @Test
    void mainPreLaunchAndCustomRunOnEverySide() {
        for (ExecutionSide side : new ExecutionSide[] { ExecutionSide.CLIENT, ExecutionSide.SERVER }) {
            assertTrue(new Entrypoint("main", "a.Main", null).runsOn(side));
            assertTrue(new Entrypoint("preLaunch", "a.Pre", null).runsOn(side));
            assertTrue(new Entrypoint("custom", "a.Custom", null).runsOn(side));
        }
    }
}
