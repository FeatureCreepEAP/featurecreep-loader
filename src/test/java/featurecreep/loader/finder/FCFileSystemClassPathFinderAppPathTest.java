package featurecreep.loader.finder;

import static featurecreep.loader.testfixture.JarFixtures.compile;
import static featurecreep.loader.testfixture.JarFixtures.jarClasses;
import static featurecreep.loader.testfixture.JarFixtures.moduleXml;
import static featurecreep.loader.testfixture.JarFixtures.writeService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.FCLoaderBasicR9;
import featurecreep.loader.testfixture.StubGameProvider;

class FCFileSystemClassPathFinderAppPathTest {

    @TempDir
    Path tempDir;

    private Path mods;
    private final List<URLClassLoader> appLoaders = new ArrayList<URLClassLoader>();
    private URLClassLoader appLoader;

    @BeforeEach
    void setUp() throws IOException {
        FCFileSystemClassPathFinder.resetAppClassPathForTests();
        mods = Files.createDirectories(tempDir.resolve("mods"));
    }

    @AfterEach
    void tearDown() throws IOException {
        FCFileSystemClassPathFinder.resetAppClassPathForTests();
        for (URLClassLoader loader : appLoaders) {
            loader.close();
        }
    }

    @Test
    void registeredProviderLoadsWithInterfaceFromAppLoader() throws Exception {
        Path apiJar = apiJar();
        Path implJar = implJar();
        appLoaderOver(apiJar, implJar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(apiJar.toFile());
        FCFileSystemClassPathFinder.registerAppClassPathJar(implJar.toFile());

        FCLoaderBasicR9 loader = loadMods();

        Class<?> service = appLoader.loadClass("api.Service");
        Class<?> provider = loader.getModule("impl.mod").getClassLoader().loadClass("impl.Provider");
        assertSame(appLoader.loadClass("impl.Provider"), provider);
        assertSame(service, provider.getInterfaces()[0]);
        assertEquals(List.of("impl.Provider"), providers(service, loader.getModule("impl.mod").getClassLoader()));
    }

    @Test
    void multiSegmentPackageClassesDelegateToAppLoader() throws Exception {
        Path apiClasses = compile(tempDir.resolve("nested-api-src"),
                Map.of("net.example.api.Service", "package net.example.api; public interface Service { String name(); }"));
        Path apiJar = mods.resolve("nested-api.jar");
        jarClasses(apiClasses, apiJar, moduleXml("nested.api.mod"));
        Path implClasses = compile(tempDir.resolve("nested-impl-src"),
                Map.of("net.example.impl.Provider", "package net.example.impl; public final class Provider"
                        + " implements net.example.api.Service { public String name() { return \"impl\"; } }"),
                apiClasses);
        writeService(implClasses, "net.example.api.Service", "net.example.impl.Provider");
        Path implJar = mods.resolve("nested-impl.jar");
        jarClasses(implClasses, implJar, moduleXml("nested.impl.mod", "nested.api.mod"));
        appLoaderOver(apiJar, implJar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(apiJar.toFile());
        FCFileSystemClassPathFinder.registerAppClassPathJar(implJar.toFile());

        FCLoaderBasicR9 loader = loadMods();

        ClassLoader implView = loader.getModule("nested.impl.mod").getClassLoader();
        assertSame(appLoader.loadClass("net.example.impl.Provider"), implView.loadClass("net.example.impl.Provider"));
    }

    @Test
    void autoDependencyDescriptorWithResourceRootDelegatesToAppLoader() throws Exception {
        Path apiClasses = compile(tempDir.resolve("auto-api-src"),
                Map.of("net.example.api.Service", "package net.example.api; public interface Service { String name(); }"));
        Path apiJar = mods.resolve("auto-api.jar");
        jarClasses(apiClasses, apiJar, moduleXml("auto.api.mod"));
        Path implClasses = compile(tempDir.resolve("auto-impl-src"),
                Map.of("net.example.impl.Provider", "package net.example.impl; public final class Provider"
                        + " implements net.example.api.Service { public String name() { return \"impl\"; } }"),
                apiClasses);
        writeService(implClasses, "net.example.api.Service", "net.example.impl.Provider");
        Path implJar = mods.resolve("auto-impl.jar");
        jarClasses(implClasses, implJar, realisticXml("auto.impl.mod", "auto.api.mod"));
        appLoaderOver(apiJar, implJar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(apiJar.toFile());
        FCFileSystemClassPathFinder.registerAppClassPathJar(implJar.toFile());

        FCLoaderBasicR9 loader = loadMods();

        ClassLoader implView = loader.getModule("auto.impl.mod").getClassLoader();
        assertSame(appLoader.loadClass("net.example.impl.Provider"), implView.loadClass("net.example.impl.Provider"));
    }

    @Test
    void registeredJarClassesAreNeverDefinedByTheModuleWhenAppLoaderLacksThem() throws Exception {
        Path apiJar = apiJar();
        Path implJar = implJar();
        appLoaderOver(apiJar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(implJar.toFile());

        FCLoaderBasicR9 loader = loadMods();

        ClassLoader implView = loader.getModule("impl.mod").getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> implView.loadClass("impl.Provider"));
    }

    private static String realisticXml(String name, String dependency) {
        return "<module xmlns=\"urn:jboss:module:1.9\" name=\"" + name + "\"><properties>"
                + "<property name=\"FeatureCreep-Mod-Id\" value=\"" + name + "\"/>"
                + "<property name=\"FeatureCreep-Visibility\" value=\"public\"/>"
                + "<property name=\"FeatureCreep-Auto-Dependencies\" value=\"true\"/>"
                + "<property name=\"FeatureCreep-Runnable\" value=\"true\"/>"
                + "</properties><dependencies><module name=\"" + dependency + "\"/></dependencies>"
                + "<resources><resource-root path=\".\"/></resources></module>";
    }

    @Test
    void unregisteredJarServedByAppLoaderIsNotDefinedAgainInsideModule() throws Exception {
        Path apiJar = apiJar();
        Path implJar = implJar();
        appLoaderOver(apiJar, implJar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(implJar.toFile());

        FCLoaderBasicR9 loader = loadMods();

        ClassLoader apiView = loader.getModule("api.mod").getClassLoader();
        assertSame(appLoader.loadClass("api.Service"), apiView.loadClass("api.Service"));
        assertEquals(List.of("impl.Provider"), providers(apiView.loadClass("api.Service"),
                loader.getModule("impl.mod").getClassLoader()));
    }

    @Test
    void defaultPackageClassesOfRegisteredJarResolveThroughAppLoader() throws Exception {
        Path classes = compile(tempDir.resolve("default-src"),
                Map.of("DefaultMain", "public class DefaultMain { public static String value() { return \"d\"; } }"));
        Path jar = mods.resolve("default.jar");
        jarClasses(classes, jar, moduleXml("default.mod"));
        appLoaderOver(jar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(jar.toFile());

        FCLoaderBasicR9 loader = loadMods();

        assertSame(appLoader.loadClass("DefaultMain"),
                loader.getModule("default.mod").getClassLoader().loadClass("DefaultMain"));
    }

    @Test
    void jarOutsideAppClassPathStaysModuleOwned() throws Exception {
        Path classes = compile(tempDir.resolve("solo-src"),
                Map.of("solo.Thing", "package solo; public class Thing { }"));
        Path jar = mods.resolve("solo.jar");
        jarClasses(classes, jar, moduleXml("solo.mod"));
        appLoaderOver();

        FCLoaderBasicR9 loader = loadMods();

        Class<?> thing = loader.getModule("solo.mod").getClassLoader().loadClass("solo.Thing");
        assertNotSame(appLoader, thing.getClassLoader());
        assertThrows(ClassNotFoundException.class, () -> appLoader.loadClass("solo.Thing"));
    }

    @Test
    void registrationThroughSymlinkMatchesModuleJar() throws Exception {
        Path apiJar = apiJar();
        Path implJar = implJar();
        Path link = Files.createDirectories(tempDir.resolve("links")).resolve("impl-link.jar");
        Files.createSymbolicLink(link, implJar);
        appLoaderOver(apiJar, implJar);
        FCFileSystemClassPathFinder.registerAppClassPathJar(apiJar.toFile());
        FCFileSystemClassPathFinder.registerAppClassPathJar(link.toFile());

        FCLoaderBasicR9 loader = loadMods();

        assertSame(appLoader.loadClass("impl.Provider"),
                loader.getModule("impl.mod").getClassLoader().loadClass("impl.Provider"));
    }

    private Path apiJar() throws IOException {
        Path classes = compile(tempDir.resolve("api-src"),
                Map.of("api.Service", "package api; public interface Service { String name(); }"));
        Path jar = mods.resolve("api.jar");
        jarClasses(classes, jar, moduleXml("api.mod"));
        return jar;
    }

    private Path implJar() throws IOException {
        Path apiClasses = tempDir.resolve("api-src").resolve("classes");
        Path classes = compile(tempDir.resolve("impl-src"),
                Map.of("impl.Provider", "package impl; public final class Provider implements api.Service {"
                        + " public String name() { return \"impl\"; } }"),
                apiClasses);
        writeService(classes, "api.Service", "impl.Provider");
        Path jar = mods.resolve("impl.jar");
        jarClasses(classes, jar, moduleXml("impl.mod", "api.mod"));
        return jar;
    }

    private void appLoaderOver(Path... jars) throws IOException {
        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            urls[i] = jars[i].toUri().toURL();
        }
        appLoader = new URLClassLoader(urls, null);
        appLoaders.add(appLoader);
        FCFileSystemClassPathFinder.useAppClassLoaderForTests(appLoader);
    }

    private FCLoaderBasicR9 loadMods() {
        FCLoaderBasicR9 loader = new FCLoaderBasicR9(new StubGameProvider(mods), 1);
        loader.loadMods();
        return loader;
    }

    private static List<String> providers(Class<?> service, ClassLoader loader) {
        List<String> names = new ArrayList<String>();
        for (Object provider : ServiceLoader.load(service, loader)) {
            names.add(provider.getClass().getName());
        }
        return names;
    }
}
