package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FCLoaderBasicR9IntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void publicModsSeeEachOtherAndKeepPrivateDependencyVersions() throws Exception {
        Path mods = Files.createDirectories(tempDir.resolve("mods"));
        Path work = Files.createDirectories(tempDir.resolve("work"));

        Path libA = compile(work.resolve("libA"), "lib.Version",
                "package lib; public final class Version { public static String value(){ return \"APRIV\"; } }");
        Path libB = compile(work.resolve("libB"), "lib.Version",
                "package lib; public final class Version { public static String value(){ return \"BPRIV\"; } }");
        Path modBClasses = compile(work.resolve("modB"), "b.Main",
                "package b; public final class Main { public static String ping(){ return \"B\"; }"
                        + " public static String test(){ return featurecreep.loader.testfixture.HostClass.value()+\":\"+lib.Version.value(); } }",
                libB);
        Path modAClasses = compile(work.resolve("modA"), "a.Main",
                "package a; public final class Main { public static String test(){ return "
                        + "featurecreep.loader.testfixture.HostClass.value()+\":\"+lib.Version.value()+\":\"+b.Main.ping(); } }",
                libA, modBClasses);

        Path privateA = work.resolve("private-a.jar");
        Path privateB = work.resolve("private-b.jar");
        jarDirectory(libA, privateA, null, null);
        jarDirectory(libB, privateB, null, null);

        String moduleAXml = moduleXml("mod.a");
        String moduleBXml = moduleXml("mod.b");
        jarDirectory(modAClasses, mods.resolve("mod.a.jar"), moduleAXml, privateA);
        jarDirectory(modBClasses, mods.resolve("mod.b.jar"), moduleBXml, privateB);

        FCLoaderBasicR9 loader = new FCLoaderBasicR9(new TestProvider(mods), 1);
        loader.loadMods();

        Module a = loader.getModule("mod.a");
        Module b = loader.getModule("mod.b");
        assertNotNull(a);
        assertNotNull(b);

        Method aTest = a.getClassLoader().loadClass("a.Main").getMethod("test");
        Method bTest = b.getClassLoader().loadClass("b.Main").getMethod("test");
        assertEquals("HOST:APRIV:B", aTest.invoke(null));
        assertEquals("HOST:BPRIV", bTest.invoke(null));

        Class<?> aPrivate = a.getClassLoader().loadClass("lib.Version");
        Class<?> bPrivate = b.getClassLoader().loadClass("lib.Version");
        assertNotSame(aPrivate, bPrivate);
    }

    @Test
    void superInjectionOwnershipMarkerKeepsJarInFeatureCreep() throws Exception {
        Path mods = Files.createDirectories(tempDir.resolve("ownership-mods"));
        Path marked = mods.resolve("marked.jar");
        jarDescriptorOnly(marked, moduleXml("marked.mod"), true);

        FCLoaderBasicR9 loader = new FCLoaderBasicR9(new TestProvider(mods, true), 1);
        loader.loadMods();

        assertNotNull(loader.getModule("marked.mod"),
                "SuperInjected compatibility metadata must not make FeatureCreep skip its own JAR");
    }

    @Test
    void unmarkedSuperloaderJarIsStillSkipped() throws Exception {
        Path mods = Files.createDirectories(tempDir.resolve("foreign-mods"));
        Path foreign = mods.resolve("foreign.jar");
        jarDescriptorOnly(foreign, moduleXml("foreign.mod"), false);

        FCLoaderBasicR9 loader = new FCLoaderBasicR9(new TestProvider(mods, true), 1);
        loader.loadMods();

        assertNull(loader.getModule("foreign.mod"));
    }

    private static String moduleXml(String name) {
        return "<module xmlns=\"urn:jboss:module:1.9\" name=\"" + name + "\">"
                + "<properties>"
                + "<property name=\"FeatureCreep-Mod-Id\" value=\"" + name + "\"/>"
                + "<property name=\"FeatureCreep-Visibility\" value=\"public\"/>"
                + "<property name=\"FeatureCreep-Nested-Jars\" value=\"private\"/>"
                + "<property name=\"FeatureCreep-Runnable\" value=\"false\"/>"
                + "</properties></module>";
    }

    private static void jarDescriptorOnly(Path output, String moduleXml, boolean ownershipMarker) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
            jar.putNextEntry(new JarEntry("module.xml"));
            jar.write(moduleXml.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            if (ownershipMarker) {
                jar.putNextEntry(new JarEntry(FCModuleProperties.SUPERINJECTION_OWNERSHIP_MARKER));
                jar.write("owner=featurecreep\nsuperloaderMetadata=compatibility-only\n"
                        .getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
    }

    private static Path compile(Path root, String className, String source, Path... extraClasspath) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Tests require a JDK, not a JRE");
        }
        Path sourceRoot = Files.createDirectories(root.resolve("src"));
        Path classes = Files.createDirectories(root.resolve("classes"));
        Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));

        StringBuilder cp = new StringBuilder(System.getProperty("java.class.path"));
        for (Path entry : extraClasspath) {
            cp.append(File.pathSeparator).append(entry);
        }
        List<String> options = Arrays.asList("--release", "11", "-classpath", cp.toString(), "-d", classes.toString());
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Boolean ok = compiler.getTask(null, files, null, options, null,
                    files.getJavaFileObjectsFromFiles(Collections.singletonList(sourceFile.toFile()))).call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IllegalStateException("Could not compile integration fixture " + className);
            }
        }
        return classes;
    }

    private static void jarDirectory(Path classes, Path output, String moduleXml, Path nestedJar) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
            List<Path> files = new ArrayList<Path>();
            Files.walk(classes).filter(Files::isRegularFile).forEach(files::add);
            for (Path file : files) {
                String name = classes.relativize(file).toString().replace(File.separatorChar, '/');
                jar.putNextEntry(new JarEntry(name));
                Files.copy(file, jar);
                jar.closeEntry();
            }
            if (moduleXml != null) {
                jar.putNextEntry(new JarEntry("module.xml"));
                jar.write(moduleXml.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
            if (nestedJar != null) {
                jar.putNextEntry(new JarEntry("META-INF/jars/private.jar"));
                Files.copy(nestedJar, jar);
                jar.closeEntry();
            }
        }
    }

    private static final class TestProvider implements GameProvider {
        private final Path mods;
        private final boolean classifyAsSuperloader;
        private final Set<String> packages = new LinkedHashSet<String>();

        TestProvider(Path mods) {
            this(mods, false);
        }

        TestProvider(Path mods, boolean classifyAsSuperloader) {
            this.mods = mods;
            this.classifyAsSuperloader = classifyAsSuperloader;
            packages.add("featurecreep/loader/testfixture");
        }

        @Override public boolean getDebugMode() { return true; }
        @Override public boolean setDebugMode(boolean value) { return value; }
        @Override public Path[] getModulePKZipLocations() { return new Path[] { mods }; }
        @Override public Path[] getClassPathPKZipLocations() { return new Path[0]; }
        @Override public Instrumentation getInstrumentation() { return null; }
        @Override public Instrumentation setInstrumentation(Instrumentation instrumentation) { return instrumentation; }
        @Override public Set<String> getNeededPackages() { return packages; }
        @Override public void addNeededPackage(String pkg) { if (pkg != null) packages.add(pkg); }
        @Override public List<String> getAvoidedModSuffixes() { return Collections.emptyList(); }
        @Override public ExecutionSide getExecutionSide() { return ExecutionSide.CLIENT; }
        @Override public List<ModuleFinder> getDefaultModuleFinders() { return Collections.emptyList(); }
        @Override public boolean isSuperLoaderModZip(File file) { return classifyAsSuperloader; }
        @Override public boolean isSuperLoaderModFolder(File folder) { return false; }
        @Override public ClassLoader getGameClassLoader() { return getClass().getClassLoader(); }
    }
}
