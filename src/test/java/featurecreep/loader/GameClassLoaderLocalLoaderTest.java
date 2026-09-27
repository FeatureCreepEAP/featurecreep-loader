package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class GameClassLoaderLocalLoaderTest {

    @Test
    void exposesHostClassesAndResources() throws Exception {
        GameClassLoaderLocalLoader loader = new GameClassLoaderLocalLoader(
                Arrays.asList(getClass().getClassLoader(), getClass().getClassLoader(), null));

        assertEquals(String.class, loader.loadClassLocal("java.lang.String", false));
        assertEquals(getClass(), loader.loadClassLocal(getClass().getName(), false));
        assertNull(loader.loadClassLocal("does.not.Exist", false));
        assertNotNull(loader.loadPackageLocal("featurecreep.loader"));
        assertFalse(loader.loadResourceLocal("featurecreep/loader/GameClassLoaderLocalLoaderTest.class").isEmpty());
    }
}
