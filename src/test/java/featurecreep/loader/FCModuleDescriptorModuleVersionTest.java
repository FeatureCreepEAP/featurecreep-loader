package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.finder.PathResourceLoader;

class FCModuleDescriptorModuleVersionTest {

    @TempDir
    Path tempDir;

    @Test
    void moduleVersionPropertyIsUsedWhenRootHasNoVersion() throws Exception {
        writeModuleXml("<module xmlns=\"urn:jboss:module:1.9\" name=\"ukulib\">"
                + "<properties><property name=\"Module-Version\" value=\"2.2.0\"/></properties></module>");

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertEquals("2.2.0", descriptor.getVersion());
        assertEquals("2.2.0", descriptor.getProperties().get(FCModuleProperties.MODULE_VERSION));
    }

    @Test
    void rootVersionAttributeWinsOverModuleVersionProperty() throws Exception {
        writeModuleXml("<module xmlns=\"urn:jboss:module:1.9\" name=\"ukulib\" version=\"3.0\">"
                + "<properties><property name=\"Module-Version\" value=\"2.2.0\"/></properties></module>");

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertEquals("3.0", descriptor.getVersion());
        assertEquals("3.0", descriptor.getProperties().get(FCModuleProperties.MODULE_VERSION));
    }

    @Test
    void moduleWithoutAnyVersionDeclaresNone() throws Exception {
        writeModuleXml("<module xmlns=\"urn:jboss:module:1.9\" name=\"plain\"/>");

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));

        assertNull(descriptor.getVersion());
        assertNull(descriptor.getProperties().get(FCModuleProperties.MODULE_VERSION));
    }

    private void writeModuleXml(String xml) throws Exception {
        Files.write(tempDir.resolve("module.xml"), xml.getBytes(StandardCharsets.UTF_8));
    }
}
