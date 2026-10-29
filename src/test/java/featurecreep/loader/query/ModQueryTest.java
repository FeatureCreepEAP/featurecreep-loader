package featurecreep.loader.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import featurecreep.loader.ExecutionSide;
import featurecreep.loader.FCLoaderBasic;
import featurecreep.loader.FCModuleDescriptor;
import featurecreep.loader.GameProvider;
import featurecreep.loader.config.FCConfigRegistry;
import featurecreep.loader.finder.ModuleLoadingMap;
import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;
import featurecreep.loader.finder.PathResourceLoader;

class ModQueryTest {

    private static final Path GAME_DIR = Path.of("query-test-game").toAbsolutePath().normalize();

    private static final String MODULE_XML = "<module xmlns=\"urn:jboss:module:1.9\" name=\"example.mod\" version=\"2.1\">"
            + "<properties><property name=\"FeatureCreep-Side\" value=\"Client\"/></properties>"
            + "<dependencies><module name=\"dep.one\"/></dependencies>"
            + "</module>";

    @TempDir
    Path tempDir;

    @Test
    void describesLoadedModuleFromItsDescriptor() throws Exception {
        Path moduleDir = Files.createDirectories(tempDir.resolve("example"));
        Files.writeString(moduleDir.resolve("module.xml"), MODULE_XML, StandardCharsets.UTF_8);
        FCModuleDescriptor descriptor = FCModuleDescriptor.read(new PathResourceLoader("example", moduleDir));

        ModuleLoadingMap map = new ModuleLoadingMap();
        map.put("example.mod", new ModuleLoadingMapEntry("example.mod", moduleDir.toUri().toString(), null,
                descriptor, moduleDir.toFile(), true, true, false, null));
        ModQuery query = query(map, "example.mod");

        assertTrue(query.isModLoaded("example.mod"));
        assertFalse(query.isModLoaded("missing.mod"));
        assertFalse(query.isModLoaded(null));

        ModContainer container = query.getModContainer("example.mod").orElseThrow();
        assertEquals("example.mod", container.id());
        assertEquals("2.1", container.version());
        assertEquals("example.mod", container.name());
        assertEquals(moduleDir, container.path());
        assertEquals("client", container.side());
        assertEquals(List.of("dep.one"), container.dependencies());
    }

    @Test
    void modulesWithoutDescriptorsFallBackToDefaults() throws Exception {
        Path jar = Files.writeString(tempDir.resolve("plain.jar"), "not a real jar");
        ModuleLoadingMap map = new ModuleLoadingMap();
        map.put("plain.mod", new ModuleLoadingMapEntry("plain.mod", jar.toUri().toString(), null, null,
                jar.toFile(), false, true, false, null));

        ModContainer container = query(map, "plain.mod").getModContainer("plain.mod").orElseThrow();
        assertEquals("plain.mod", container.id());
        assertNull(container.version());
        assertEquals("plain.mod", container.name());
        assertEquals(jar, container.path());
        assertEquals("both", container.side());
        assertEquals(List.of(), container.dependencies());
    }

    @Test
    void unknownModulesHaveNoContainer() throws Exception {
        ModQuery query = query(new ModuleLoadingMap(), "loaded.mod");
        assertTrue(query.getModContainer("loaded.mod").isPresent());
        assertTrue(query.getModContainer("missing.mod").isEmpty());
        assertTrue(query.getModContainer(null).isEmpty());
    }

    @Test
    void developmentIsDetectedFromDirectoryModulesOrTheProperty() throws Exception {
        String previous = System.getProperty(ModQuery.DEVELOPMENT_PROPERTY);
        System.clearProperty(ModQuery.DEVELOPMENT_PROPERTY);
        try {
            Path jar = Files.writeString(tempDir.resolve("packed.jar"), "x");
            ModuleLoadingMap packed = new ModuleLoadingMap();
            packed.put("packed.mod", new ModuleLoadingMapEntry("packed.mod", jar.toUri().toString(), null, null,
                    jar.toFile(), false, true, false, null));
            assertFalse(query(packed, "packed.mod").isDevelopmentEnvironment());

            ModuleLoadingMap directory = new ModuleLoadingMap();
            directory.put("dir.mod", new ModuleLoadingMapEntry("dir.mod", tempDir.toUri().toString(), null, null,
                    tempDir.toFile(), false, true, false, null));
            assertTrue(query(directory, "dir.mod").isDevelopmentEnvironment());

            System.setProperty(ModQuery.DEVELOPMENT_PROPERTY, "true");
            assertTrue(query(packed, "packed.mod").isDevelopmentEnvironment());
        } finally {
            restore(ModQuery.DEVELOPMENT_PROPERTY, previous);
        }
    }

    @Test
    void reportsGameConfigAndEnvironmentLocations() throws Exception {
        Path config = tempDir.resolve("cfg");
        String previous = System.getProperty(FCConfigRegistry.CONFIG_DIR_PROPERTY);
        System.setProperty(FCConfigRegistry.CONFIG_DIR_PROPERTY, config.toString());
        try {
            ModQuery query = query(new ModuleLoadingMap());
            assertEquals(config.toAbsolutePath().normalize(), query.getConfigDir());
            assertEquals(GAME_DIR, query.getGameDir());
            assertEquals(ExecutionSide.CLIENT, query.getEnvironmentType());
        } finally {
            restore(FCConfigRegistry.CONFIG_DIR_PROPERTY, previous);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static ModQuery query(ModuleLoadingMap map, String... moduleNames) throws ModuleLoadException {
        ModuleLoader moduleLoader = new ModuleLoader(new ModuleFinder[] {
                (name, finderLoader) -> ModuleSpec.build(name).create() });
        ArrayList<Module> modules = new ArrayList<>();
        for (String name : moduleNames) {
            modules.add(moduleLoader.loadModule(name));
        }

        GameProvider provider = (GameProvider) Proxy.newProxyInstance(GameProvider.class.getClassLoader(),
                new Class<?>[] { GameProvider.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getGameWorkingDirectory")) {
                        return GAME_DIR;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        FCLoaderBasic loader = (FCLoaderBasic) Proxy.newProxyInstance(FCLoaderBasic.class.getClassLoader(),
                new Class<?>[] { FCLoaderBasic.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getGameProvider" -> provider;
                    case "getModules" -> modules;
                    case "getModule" -> modules.stream().filter(m -> m.getName().equals(args[0])).findFirst().orElse(null);
                    case "getModuleLoadingMap" -> map;
                    case "getExecutionSide" -> ExecutionSide.CLIENT;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new ModQuery(loader);
    }
}
