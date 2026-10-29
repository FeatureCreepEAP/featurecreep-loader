package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FCModuleCompatibilityTest {

    private static Map<String, String> props(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void modulesWithoutRequirementsAreCompatible() {
        assertNull(FCModuleCompatibility.findMismatch(props(), "minecraft", "1.21.1", "12"));
        assertNull(FCModuleCompatibility.findMismatch(props(), null, null, null));
    }

    @Test
    void matchingGameAndVersionAreCompatible() {
        Map<String, String> properties = props(
                FCModuleProperties.TARGET_GAME, "Minecraft",
                FCModuleProperties.TARGET_GAME_VERSION, "[1.20,1.22)",
                FCModuleProperties.LOADER_VERSION, ">=12");
        assertNull(FCModuleCompatibility.findMismatch(properties, "minecraft", "1.21.1", "12"));
    }

    @Test
    void wrongGameIsAMismatch() {
        String reason = FCModuleCompatibility.findMismatch(props(FCModuleProperties.TARGET_GAME, "terraria"),
                "minecraft", "1.21.1", "12");
        assertTrue(reason != null && reason.contains("terraria"), reason);
    }

    @Test
    void outOfRangeGameVersionIsAMismatch() {
        String reason = FCModuleCompatibility.findMismatch(
                props(FCModuleProperties.TARGET_GAME_VERSION, "[1.20,1.21)"), "minecraft", "1.21.1", "12");
        assertTrue(reason != null && reason.contains("1.21.1"), reason);
    }

    @Test
    void outOfRangeLoaderVersionIsAMismatch() {
        String reason = FCModuleCompatibility.findMismatch(
                props(FCModuleProperties.LOADER_VERSION, ">=13"), "minecraft", "1.21.1", "12");
        assertTrue(reason != null && reason.contains("loader"), reason);
    }

    @Test
    void unknownHostValuesAreNotMismatches() {
        Map<String, String> properties = props(
                FCModuleProperties.TARGET_GAME, "minecraft",
                FCModuleProperties.TARGET_GAME_VERSION, ">=99",
                FCModuleProperties.LOADER_VERSION, ">=99");
        assertNull(FCModuleCompatibility.findMismatch(properties, null, null, null));
    }

    @Test
    void malformedRangesAreMismatchesNeverCompatible() {
        String reason = FCModuleCompatibility.findMismatch(
                props(FCModuleProperties.TARGET_GAME_VERSION, "[1.20,"), "minecraft", "1.21.1", "12");
        assertTrue(reason != null, "malformed range must be a mismatch");
        assertTrue(reason.contains("malformed"), reason);

        String loaderReason = FCModuleCompatibility.findMismatch(
                props(FCModuleProperties.LOADER_VERSION, "!=12"), null, null, null);
        assertTrue(loaderReason != null && loaderReason.contains("malformed"), loaderReason);
    }

    @Test
    void providerOverloadReadsProviderIdentity() {
        GameProvider provider = new GameProvider() {
            @Override public boolean getDebugMode() { return false; }
            @Override public boolean setDebugMode(boolean val) { return val; }
            @Override public java.nio.file.Path[] getModulePKZipLocations() { return new java.nio.file.Path[0]; }
            @Override public java.nio.file.Path[] getClassPathPKZipLocations() { return new java.nio.file.Path[0]; }
            @Override public java.lang.instrument.Instrumentation getInstrumentation() { return null; }
            @Override public java.lang.instrument.Instrumentation setInstrumentation(java.lang.instrument.Instrumentation i) { return i; }
            @Override public java.util.Set<String> getNeededPackages() { return new java.util.HashSet<String>(); }
            @Override public void addNeededPackage(String pkg) { }
            @Override public java.util.List<String> getAvoidedModSuffixes() { return new java.util.ArrayList<String>(); }
            @Override public ExecutionSide getExecutionSide() { return ExecutionSide.CLIENT; }
            @Override public java.util.List<org.jboss.modules.ModuleFinder> getDefaultModuleFinders() { return new java.util.ArrayList<org.jboss.modules.ModuleFinder>(); }
            @Override public boolean isSuperLoaderModZip(java.io.File zip) { return false; }
            @Override public boolean isSuperLoaderModFolder(java.io.File folder) { return false; }
            @Override public String getGameId() { return "minecraft"; }
            @Override public String getGameVersion() { return "26.3"; }
            @Override public String getLoaderVersion() { return "12"; }
        };
        assertNull(FCModuleCompatibility.findMismatch(props(FCModuleProperties.TARGET_GAME_VERSION, ">=26"), provider));
        assertTrue(FCModuleCompatibility.findMismatch(props(FCModuleProperties.TARGET_GAME_VERSION, "<26"), provider) != null);
        assertEquals("minecraft", provider.getGameId());
    }
}
