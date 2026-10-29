package featurecreep.loader.testfixture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class JarFixtures {

    private JarFixtures() {
    }

    /** Compiles the sources (class name to source text) under {@code root} and returns the class output directory. */
    public static Path compile(Path root, Map<String, String> sourcesByClassName, Path... extraClasspath) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Tests require a JDK, not a JRE");
        }
        Path sourceRoot = Files.createDirectories(root.resolve("src"));
        Path classes = Files.createDirectories(root.resolve("classes"));
        List<File> sources = new ArrayList<File>();
        for (Map.Entry<String, String> unit : sourcesByClassName.entrySet()) {
            Path file = sourceRoot.resolve(unit.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.write(file, unit.getValue().getBytes(StandardCharsets.UTF_8));
            sources.add(file.toFile());
        }
        StringBuilder cp = new StringBuilder(System.getProperty("java.class.path"));
        for (Path entry : extraClasspath) {
            cp.append(File.pathSeparator).append(entry);
        }
        List<String> options = Arrays.asList("--release", "11", "-classpath", cp.toString(), "-d", classes.toString());
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Boolean ok = compiler.getTask(null, files, null, options, null,
                    files.getJavaFileObjectsFromFiles(sources)).call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IllegalStateException("Could not compile fixture sources " + sourcesByClassName.keySet());
            }
        }
        return classes;
    }

    /** Writes a jar of every file under {@code classes} with directory entries, as Maven and Gradle do, plus {@code module.xml} when given. */
    public static void jarClasses(Path classes, Path output, String moduleXml) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classes)) {
            files = walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
            Set<String> directories = new HashSet<String>();
            for (Path file : files) {
                String entry = classes.relativize(file).toString().replace(File.separatorChar, '/');
                writeDirectoryEntries(jar, entry, directories);
                jar.putNextEntry(new JarEntry(entry));
                Files.copy(file, jar);
                jar.closeEntry();
            }
            if (moduleXml != null) {
                jar.putNextEntry(new JarEntry("module.xml"));
                jar.write(moduleXml.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
    }

    private static void writeDirectoryEntries(JarOutputStream jar, String entry, Set<String> written) throws IOException {
        for (int slash = entry.indexOf('/'); slash >= 0; slash = entry.indexOf('/', slash + 1)) {
            String directory = entry.substring(0, slash + 1);
            if (written.add(directory)) {
                jar.putNextEntry(new JarEntry(directory));
                jar.closeEntry();
            }
        }
    }

    public static void writeService(Path classes, String service, String implementation) throws IOException {
        Path file = Files.createDirectories(classes.resolve("META-INF/services")).resolve(service);
        Files.write(file, (implementation + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** Public module descriptor with the given {@code <module>} dependencies (no export). */
    public static String moduleXml(String name, String... dependencies) {
        StringBuilder deps = new StringBuilder();
        for (String dependency : dependencies) {
            deps.append("<module name=\"").append(dependency).append("\"/>");
        }
        return "<module xmlns=\"urn:jboss:module:1.9\" name=\"" + name + "\">"
                + "<properties>"
                + "<property name=\"FeatureCreep-Mod-Id\" value=\"" + name + "\"/>"
                + "<property name=\"FeatureCreep-Visibility\" value=\"public\"/>"
                + "<property name=\"FeatureCreep-Runnable\" value=\"false\"/>"
                + "</properties>"
                + "<dependencies>" + deps + "</dependencies>"
                + "</module>";
    }
}
