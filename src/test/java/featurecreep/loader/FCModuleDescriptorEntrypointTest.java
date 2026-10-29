package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.entrypoint.Entrypoint;
import featurecreep.loader.finder.PathResourceLoader;

class FCModuleDescriptorEntrypointTest {

    @TempDir
    Path tempDir;

    private FCModuleDescriptor read(String moduleXml) throws Exception {
        Files.write(tempDir.resolve("module.xml"), moduleXml.getBytes(StandardCharsets.UTF_8));
        return FCModuleDescriptor.read(new PathResourceLoader("test", tempDir));
    }

    private static String module(String body) {
        return "<module xmlns=\"urn:jboss:module:1.9\" name=\"example.mod\" version=\"1.0\">" + body + "</module>";
    }

    @Test
    void mainClassComesFirstThenDeclaredEntrypointsInDocumentOrder() throws Exception {
        FCModuleDescriptor descriptor = read(module(
                "<main-class name=\"example.Main\"/>"
                + "<entrypoint type=\"client\" class=\"example.Client\"/>"
                + "<entrypoints><entrypoint type=\"preLaunch\" class=\"example.Pre\" adapter=\"java\"/></entrypoints>"));

        List<Entrypoint> entrypoints = descriptor.getEntrypoints();
        assertEquals(3, entrypoints.size());
        assertEquals(new Entrypoint("main", "example.Main", "java"), entrypoints.get(0));
        assertEquals(new Entrypoint("client", "example.Client", "java"), entrypoints.get(1));
        assertEquals(new Entrypoint("preLaunch", "example.Pre", "java"), entrypoints.get(2));
    }

    @Test
    void declaredMainMatchingMainClassIsNotDuplicated() throws Exception {
        FCModuleDescriptor descriptor = read(module(
                "<main-class name=\"example.Main\"/>"
                + "<entrypoint type=\"main\" class=\"example.Main\"/>"));

        assertEquals(1, descriptor.getEntrypoints().size());
    }

    @Test
    void moduleWithoutMainClassHasOnlyDeclaredEntrypoints() throws Exception {
        FCModuleDescriptor descriptor = read(module("<entrypoint type=\"server\" class=\"example.Server\"/>"));

        assertEquals(List.of(new Entrypoint("server", "example.Server", null)), descriptor.getEntrypoints());
    }

    @Test
    void entrypointWithoutClassIsRejected() throws Exception {
        assertThrows(IOException.class, () -> read(module("<entrypoint type=\"client\"/>")));
    }

    @Test
    void entrypointWithoutTypeIsRejected() throws Exception {
        assertThrows(IOException.class, () -> read(module("<entrypoint class=\"example.Client\"/>")));
    }

    @Test
    void dependencyKindsParse() throws Exception {
        FCModuleDescriptor descriptor = read(module(
                "<dependencies>"
                + "<module name=\"a.required\" kind=\"required\"/>"
                + "<module name=\"b.optional\" kind=\"optional\"/>"
                + "<module name=\"c.recommends\" kind=\"recommends\"/>"
                + "<module name=\"d.suggests\" kind=\"Suggests\"/>"
                + "<module name=\"e.conflicts\" kind=\"conflicts\"/>"
                + "<module name=\"f.legacy\" optional=\"true\"/>"
                + "<module name=\"g.default\"/>"
                + "</dependencies>"));

        List<FCModuleDescriptor.Dependency> deps = descriptor.getDependencies();
        assertEquals(FCModuleDescriptor.DependencyKind.REQUIRED, deps.get(0).getKind());
        assertEquals(FCModuleDescriptor.DependencyKind.OPTIONAL, deps.get(1).getKind());
        assertEquals(FCModuleDescriptor.DependencyKind.RECOMMENDS, deps.get(2).getKind());
        assertEquals(FCModuleDescriptor.DependencyKind.SUGGESTS, deps.get(3).getKind());
        assertEquals(FCModuleDescriptor.DependencyKind.CONFLICTS, deps.get(4).getKind());
        assertEquals(FCModuleDescriptor.DependencyKind.OPTIONAL, deps.get(5).getKind());
        assertEquals(FCModuleDescriptor.DependencyKind.REQUIRED, deps.get(6).getKind());

        assertFalse(deps.get(0).isOptional());
        assertTrue(deps.get(2).isOptional());
        assertTrue(deps.get(3).isOptional());
        assertFalse(deps.get(2).isConflict());
        assertTrue(deps.get(4).isConflict());
        assertFalse(deps.get(4).isOptional());
    }

    @Test
    void unknownDependencyKindIsRejected() throws Exception {
        assertThrows(IOException.class, () -> read(module(
                "<dependencies><module name=\"x.breaks\" kind=\"breaks\"/></dependencies>")));
    }
}
