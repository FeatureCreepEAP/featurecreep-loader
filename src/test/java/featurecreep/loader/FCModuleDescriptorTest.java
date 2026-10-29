package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.finder.PathResourceLoader;

class FCModuleDescriptorTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesFeatureCreepPropertiesDependenciesResourcesAndMixins() throws Exception {
        String xml = "<module xmlns=\"urn:jboss:module:1.9\" name=\"example.mod\" version=\"2.1\">"
                + "<main-class name=\"example.Main\"/>"
                + "<properties>"
                + "<property name=\"FeatureCreep-Visibility\" value=\"public\"/>"
                + "<property name=\"MixinConfigs\" value=\"example.mixins.json,compat.mixins.json\"/>"
                + "</properties>"
                + "<dependencies><module name=\"dep.one\" optional=\"true\" export=\"true\" services=\"import\"/></dependencies>"
                + "<resources><resource-root path=\"lib/classes\"/></resources>"
                + "<provides><service name=\"example.Service\"><with-class name=\"example.ServiceImpl\"/></service></provides>"
                + "</module>";
        Files.write(tempDir.resolve("module.xml"), xml.getBytes(StandardCharsets.UTF_8));

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertEquals("example.mod", descriptor.getDeclaredName());
        assertEquals("2.1", descriptor.getVersion());
        assertEquals("example.Main", descriptor.getMainClass());
        assertTrue(descriptor.hasModuleXml());
        assertEquals(Arrays.asList("example.mixins.json", "compat.mixins.json"),
                FCModuleProperties.getMixinConfigs(descriptor.getProperties()));
        assertEquals(1, descriptor.getDependencies().size());
        assertTrue(descriptor.getDependencies().get(0).isOptional());
        assertTrue(descriptor.getDependencies().get(0).isExport());
        assertTrue(descriptor.getDependencies().get(0).isServices());
        assertEquals(Arrays.asList("lib/classes"), descriptor.getResourceRoots());
        assertEquals(Arrays.asList("example.ServiceImpl"), descriptor.getProvidedServices().get("example.Service"));
    }

    @Test
    void moduleXmlOverridesManifestMetadata() throws Exception {
        Path metaInf = Files.createDirectories(tempDir.resolve("META-INF"));
        Files.write(metaInf.resolve("MANIFEST.MF"),
                ("Manifest-Version: 1.0\nAutomatic-Module-Name: manifest.name\nMixinConfigs: old.mixins.json\n\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("module.xml"),
                ("<module xmlns=\"urn:jboss:module:1.9\" name=\"xml.name\">"
                        + "<properties><property name=\"MixinConfigs\" value=\"new.mixins.json\"/></properties>"
                        + "</module>").getBytes(StandardCharsets.UTF_8));

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));
        assertEquals("xml.name", descriptor.getDeclaredName());
        assertEquals(Arrays.asList("new.mixins.json"), FCModuleProperties.getMixinConfigs(descriptor.getProperties()));
        assertFalse(descriptor.getProperties().isEmpty());
    }

    @Test
    void parsesDependencyVersionRangesAndKeepsOldDescriptorsWorking() throws Exception {
        String xml = "<module xmlns=\"urn:jboss:module:1.9\" name=\"ranged.mod\" version=\"3.0\">"
                + "<properties>"
                + "<property name=\"FeatureCreep-Target-Game\" value=\"minecraft\"/>"
                + "<property name=\"FeatureCreep-Target-Game-Version\" value=\"[1.20,1.22)\"/>"
                + "<property name=\"FeatureCreep-Loader-Version\" value=\">=12\"/>"
                + "</properties>"
                + "<dependencies>"
                + "<module name=\"dep.ranged\" version-range=\">=1.2,&lt;2.0\"/>"
                + "<module name=\"dep.attr\" version=\"[1.0,)\" optional=\"true\"/>"
                + "<module name=\"dep.plain\"/>"
                + "</dependencies>"
                + "</module>";
        Files.write(tempDir.resolve("module.xml"), xml.getBytes(StandardCharsets.UTF_8));

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertEquals("[1.20,1.22)", descriptor.getProperty(FCModuleProperties.TARGET_GAME_VERSION));
        assertEquals(">=12", descriptor.getProperty(FCModuleProperties.LOADER_VERSION));
        assertEquals("minecraft", descriptor.getProperty(FCModuleProperties.TARGET_GAME));
        assertEquals(3, descriptor.getDependencies().size());
        assertEquals(">=1.2,<2.0", descriptor.getDependencies().get(0).getVersionRange());
        assertEquals("[1.0,)", descriptor.getDependencies().get(1).getVersionRange());
        assertTrue(descriptor.getDependencies().get(1).isOptional());
        assertNull(descriptor.getDependencies().get(2).getVersionRange());
    }
}
