package featurecreep.loader;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public interface GetPackagesFromClassLoader {

    /**
     * Scans the current ClassLoader for loaded packages, and recursively scans
     * all JARs and directories on the classpath for available packages.
     * 
     * @return An array of package names with '.' replaced by '/'.
     */
    public static String[] getPackageNamesInCurrentClassLoader() {
        Set<String> packageSet = new HashSet<>();

        // 1. Add packages currently loaded in memory by the ClassLoader
        for (Package pkg : Package.getPackages()) {
            packageSet.add(pkg.getName().replace('.', '/'));
        }

        // 2. Scan JARs and directories on the classpath
        String classPath = System.getProperty("java.class.path");
        if (classPath != null) {
            String[] pathElements = classPath.split(File.pathSeparator);
            for (String pathElement : pathElements) {
                File file = new File(pathElement);
                if (file.exists()) {
                    if (file.isDirectory()) {
                        scanDirectory(file, file, packageSet);
                    } else if (file.getName().toLowerCase().endsWith(".jar")) {
                        scanJar(file, packageSet);
                    }
                }
            }
        }

        return packageSet.toArray(new String[0]);
    }

    /**
     * Recursively scans a directory for .class files to determine package structure.
     */
    private static void scanDirectory(File root, File current, Set<String> packageSet) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, packageSet);
            } else if (file.getName().endsWith(".class")) {
                // Convert file path back to package path
                String fullPath = file.getAbsolutePath();
                String rootPath = root.getAbsolutePath();
                
                // Remove root path and the .class extension
                String relativePath = fullPath.substring(rootPath.length() + 1);
                relativePath = relativePath.substring(0, relativePath.length() - 6);
                
                // Normalize separators to forward slashes
                String packagePath = relativePath.replace(File.separatorChar, '/');
                
                // If there are subdirectories, it's a package
                if (packagePath.contains("/")) {
                    // Get the directory containing the class (the package)
                    String packageName = packagePath.substring(0, packagePath.lastIndexOf('/'));
                    packageSet.add(packageName);
                }
            }
        }
    }

    /**
     * Scans a JAR file for packages.
     */
    private static void scanJar(File jarFile, Set<String> packageSet) {
        // We use try-with-resources to ensure the FileSystem is closed
        try (JarFile jar = new JarFile(jarFile)) {
            
            // 1. Scan entries in the JAR
            // We use NIO FileSystems to easily iterate JAR contents like a directory
            URI jarUri = new URI("jar:" + jarFile.toURI().toString());
            try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
                Path rootPath = fs.getPath("/");
                try (Stream<Path> paths = Files.walk(rootPath)) {
                    paths.filter(Files::isRegularFile)
                         .filter(p -> p.toString().endsWith(".class"))
                         .forEach(p -> {
                             String pathStr = p.toString();
                             // JAR paths usually start with '/', remove it if present
                             if (pathStr.startsWith("/")) {
                                 pathStr = pathStr.substring(1);
                             }
                             
                             // Remove filename to get package
                             int lastSlash = pathStr.lastIndexOf('/');
                             if (lastSlash > 0) {
                                 packageSet.add(pathStr.substring(0, lastSlash));
                             }
                         });
                }
            }

            // 2. Check Manifest for "Class-Path" (JARs often reference other JARs here)
            // Note: These JARs are relative to the current JAR's location
            if (jar.getManifest() != null) {
                String classPath = jar.getManifest().getMainAttributes().getValue("Class-Path");
                if (classPath != null) {
                    String[] manifestPaths = classPath.split(" ");
                    for (String relativePath : manifestPaths) {
                        if (!relativePath.isEmpty()) {
                            File referencedJar = new File(jarFile.getParent(), relativePath);
                            if (referencedJar.exists() && referencedJar.getName().toLowerCase().endsWith(".jar")) {
                                scanJar(referencedJar, packageSet);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            // Silently ignore errors (invalid JAR, permissions, etc.) to allow scanning to continue
        }
    }
}