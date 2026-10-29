package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.entrypoint.Entrypoint;
import featurecreep.loader.finder.PathResourceLoader;

class FCModuleDescriptorMainClassTest {

    private static final String MANIFEST_WITH_MAIN = "Manifest-Version: 1.0\nMain-Class: manifest.Main\n\n";

    @TempDir
    Path tempDir;

    @Test
    void moduleXmlMainClassWinsOverManifestMainClass() throws Exception {
        writeManifest(MANIFEST_WITH_MAIN);
        writeModuleXml("<module xmlns=\"urn:jboss:module:1.9\" name=\"xml.mod\"><main-class name=\"xml.Main\"/></module>");

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertEquals("xml.Main", descriptor.getMainClass());
        assertEquals("xml.Main", descriptor.getProperty(FCModuleProperties.MAIN_CLASS));
        assertMainEntrypoints(descriptor.getEntrypoints(), "xml.Main");
    }

    @Test
    void manifestMainClassIsIgnoredWhenModuleXmlExistsWithoutMainClass() throws Exception {
        writeManifest(MANIFEST_WITH_MAIN);
        writeModuleXml("<module xmlns=\"urn:jboss:module:1.9\" name=\"xml.mod\"/>");

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertNull(descriptor.getMainClass());
        assertNull(descriptor.getProperty(FCModuleProperties.MAIN_CLASS));
        assertFalse(descriptor.getProperties().containsKey(FCModuleProperties.MAIN_CLASS));
        assertMainEntrypoints(descriptor.getEntrypoints());
    }

    @Test
    void manifestOnlyModStillUsesManifestMainClass() throws Exception {
        writeManifest(MANIFEST_WITH_MAIN);

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertFalse(descriptor.hasModuleXml());
        assertEquals("manifest.Main", descriptor.getMainClass());
        assertEquals("manifest.Main", descriptor.getProperty(FCModuleProperties.MAIN_CLASS));
        assertMainEntrypoints(descriptor.getEntrypoints(), "manifest.Main");
    }

    private void writeManifest(String content) throws Exception {
        Path metaInf = Files.createDirectories(tempDir.resolve("META-INF"));
        Files.write(metaInf.resolve("MANIFEST.MF"), content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeModuleXml(String content) throws Exception {
        Files.write(tempDir.resolve("module.xml"), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertMainEntrypoints(List<Entrypoint> entrypoints, String... expectedClasses) {
        List<String> mains = entrypoints.stream()
                .filter(e -> Entrypoint.MAIN.equals(e.type()))
                .map(Entrypoint::className)
                .toList();
        assertEquals(List.of(expectedClasses), mains);
    }
}
