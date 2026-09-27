package featurecreep.loader.finder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathResourceLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsClassesResourcesAndPathsWithoutSecurityManager() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("example/data"));
        byte[] bytes = "resource-data".getBytes(StandardCharsets.UTF_8);
        Files.write(nested.resolve("sample.bin"), bytes);
        Files.write(tempDir.resolve("Example.class"), new byte[] { 1, 2, 3, 4 });

        PathResourceLoader loader = new PathResourceLoader("test", tempDir);
        Resource resource = loader.getResource("example/data/sample.bin");
        assertNotNull(resource);
        assertArrayEquals(bytes, resource.openStream().readAllBytes());

        ClassSpec spec = loader.getClassSpec("Example.class");
        assertNotNull(spec);
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, spec.getBytes());
        assertTrue(loader.getPaths().contains("example/data"));
    }
}
