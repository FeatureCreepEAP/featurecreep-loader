package featurecreep.loader.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import featurecreep.loader.FCLoaderBasic;

public class ClassPathUtils {

	public static List<String> getClassPath() {
		String classpath = System.getProperty("java.class.path");
		String[] classpathEntries = classpath.split(File.pathSeparator);
		List<String> classpathList = new ArrayList<>();
		for (String entry : classpathEntries) {
			classpathList.add(entry);
		}
		return classpathList;
	}

	// THIS DOES NOT YET HONOUR THE MODULE SYSTEM YET
	public static List<String> getClassPath(FCLoaderBasic loader) {
		List<String> origin = new ArrayList<String>();
		origin.addAll(getClassPath());
		for (File fil : loader.getCombinedFiles()) {
			origin.add(fil.getAbsolutePath());
		}
		return origin;
	}

	public static List<String> getAllInternalFilesFromClassPath() {
		List<String> classpathEntries = getClassPath();

		List<String> allFiles = new ArrayList<>();
		for (String entry : classpathEntries) {
			File file = new File(entry);

			if (file.isFile() && !file.getName().endsWith(".class")) {

				allFiles.addAll(getFilesFromJar(file));
			} else if (file.isDirectory()) {
				// Recursively add files from directories (not just JARs)
				allFiles.addAll(getAllFilesFromDirectory(file));
			} else if (file.getName().endsWith(".class")) {
				allFiles.add(file.getName());
			}
		}
		return allFiles;
	}

	public static List<String> getAllInternalFilesFromClassPath(FCLoaderBasic loader) {
		List<String> classpathEntries = getClassPath(loader);

		List<String> allFiles = new ArrayList<>();
		for (String entry : classpathEntries) {
			File file = new File(entry);

			if (file.isFile() && !file.getName().endsWith(".class")) {
				allFiles.addAll(getFilesFromJar(file));
			} else if (file.isDirectory()) {
				// Recursively add files from directories (not just JARs)
				allFiles.addAll(getAllFilesFromDirectory(file));
			} else if (file.getName().endsWith(".class")) {
				allFiles.add(file.getName().replace("/", "."));// Soon need to make this actually get official package
			}
		}
		return allFiles;
	}

	// ONLY for .class java classes ATM
	public static List<String> getClasses() {
		List<String> classFiles = new ArrayList<>();

		for (String fileName : getAllInternalFilesFromClassPath()) {
			if (fileName.endsWith(".class")) {
				// Remove ".class" from the end of the file name before adding it to the list
				classFiles.add(fileName.substring(0, fileName.length() - ".class".length()));
			}
		}

		return classFiles;
	}

	// ONLY for .class java classes ATM
	public static List<String> getClasses(FCLoaderBasic loader) {
		List<String> classFiles = new ArrayList<>();

		for (String fileName : getAllInternalFilesFromClassPath(loader)) {
			if (fileName.endsWith(".class")) {
				// Remove ".class" from the end of the file name before adding it to the list
				classFiles.add(fileName.substring(0, fileName.length() - ".class".length()));
			}
		}

		return classFiles;
	}

	public static List<String> getFilesFromJar(File jarFile) {
		List<String> jarEntries = new ArrayList<>();
		try (JarFile jar = new JarFile(jarFile)) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				jarEntries.add(entry.getName().replace("/", "."));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jarEntries;
	}

	public static List<String> getAllFilesFromDirectory(File directory) {
		List<String> files = new ArrayList<>();
		File[] filesInDir = directory.listFiles();
		if (filesInDir != null) {
			for (File file : filesInDir) {
				if (file.isFile()) {
					files.add(file.getAbsolutePath());
				} else if (file.isDirectory()) {
					files.addAll(getAllFilesFromDirectory(file)); // Recursive call
				}
			}
		}
		return files;
	}

	public static List<String> findJarFilesWithPackage(List<String> classpathLocations, String packageName) {
		List<String> jarFilesWithPackage = new ArrayList<>();

		for (String location : classpathLocations) {
			Path path = Paths.get(location);
			if (Files.isRegularFile(path) && FCLoaderBasic.isFilePKZipCompatible(new File(location))) {
				try (JarFile jarFile = new JarFile(path.toFile())) {
					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						String name = entry.getName().replace("/", ".");
						if (name.startsWith(packageName) && name.endsWith(".class")) {
							// Found a class in the desired package
							jarFilesWithPackage.add(path.toString());
							break; // No need to check further entries in this JAR
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return jarFilesWithPackage;
	}

}
