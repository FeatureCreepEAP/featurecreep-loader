package featurecreep.loader.config;

import static featurecreep.loader.testfixture.JarFixtures.compile;
import static featurecreep.loader.testfixture.JarFixtures.jarClasses;
import static featurecreep.loader.testfixture.JarFixtures.moduleXml;
import static featurecreep.loader.testfixture.JarFixtures.writeService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.FCLoaderBasicR9;
import featurecreep.loader.testfixture.StubGameProvider;

class FCConfigRegistryDuplicateProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void providerVisibleFromSeveralModulesRegistersOnceWithoutDuplicateWarnings() throws Exception {
        Path mods = Files.createDirectories(tempDir.resolve("mods"));
        Path classes = compile(tempDir.resolve("cfg-src"), Map.of("cfg.Provider",
                "package cfg; public final class Provider implements featurecreep.loader.config.FCConfigProvider {"
                        + " public featurecreep.loader.config.FCConfigEntry createConfigEntry() {"
                        + " return featurecreep.loader.config.FCConfigEntry.builder(\"cfg.mod\")"
                        + ".displayName(\"Configured\").screenFactory(parent -> parent).build(); } }"));
        writeService(classes, "featurecreep.loader.config.FCConfigProvider", "cfg.Provider");
        jarClasses(classes, mods.resolve("cfg.jar"), moduleXml("cfg.mod"));

        Path empty = Files.createDirectories(tempDir.resolve("empty"));
        jarClasses(empty, mods.resolve("view-a.jar"), viewerXml("view.a", "cfg.mod"));
        jarClasses(empty, mods.resolve("view-b.jar"), viewerXml("view.b", "cfg.mod"));

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        FCLoaderBasicR9 loader;
        System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8.name()));
        try {
            loader = new FCLoaderBasicR9(new StubGameProvider(mods), 1);
            loader.loadMods();
        } finally {
            System.setErr(originalErr);
        }

        assertEquals(1, countProviders(loader.getModule("view.a")), "the provider must reach view.a for this test to mean anything");
        assertEquals(1, countProviders(loader.getModule("view.b")));
        assertTrue(loader.getConfigApi().getEntry("cfg.mod").isPresent());
        assertFalse(errors.toString(StandardCharsets.UTF_8.name()).contains("already registered"),
                "a provider seen through several modules must not be registered twice");
    }

    private static String viewerXml(String name, String dependency) {
        return "<module xmlns=\"urn:jboss:module:1.9\" name=\"" + name + "\">"
                + "<properties>"
                + "<property name=\"FeatureCreep-Mod-Id\" value=\"" + name + "\"/>"
                + "<property name=\"FeatureCreep-Visibility\" value=\"public\"/>"
                + "<property name=\"FeatureCreep-Runnable\" value=\"false\"/>"
                + "</properties>"
                + "<dependencies><module name=\"" + dependency + "\" services=\"import\"/></dependencies>"
                + "</module>";
    }

    private static int countProviders(org.jboss.modules.Module module) {
        int count = 0;
        for (Object ignored : module.loadService(FCConfigProvider.class)) {
            count++;
        }
        return count;
    }
}
