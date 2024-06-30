package featurecreep.loader.utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
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
	
	public static List<String> getFilesFromJarWithoutSwitchingFromSlashToDot(File jarFile) {
		List<String> jarEntries = new ArrayList<>();
		try (JarFile jar = new JarFile(jarFile)) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				jarEntries.add(entry.getName());
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
	
	
	
	
	
	
	// 辅助方法，用于获取指定前缀和后缀的所有资源名称  
	public static List<String> getResourceNames(ClassLoader classLoader, String prefix, String suffix) throws IOException {  
	    List<String> resourceNames = new ArrayList<>();  
	    Enumeration<URL> resources = classLoader.getResources(prefix + "*");  
	    while (resources.hasMoreElements()) {  
	        URL resourceUrl = resources.nextElement();  
	        if (resourceUrl.getProtocol().equals("file")) {  
	            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(resourceUrl.toURI()), path -> path.toString().endsWith(suffix))) {  
	                for (Path file : stream) {  
	                    resourceNames.add(prefix + file.getFileName().toString());  
	                }  
	            } catch (URISyntaxException | NotDirectoryException e) {  
	                // 忽略非文件协议或不是目录的情况  
	            }  
	        } else {  
	            // 对于非文件协议（如JAR），我们需要使用其他方法来获取资源名称  
	            // 但由于JAR中的资源名称通常不是通过路径来访问的，我们只需要检查资源的URL字符串  
	            String urlString = resourceUrl.toString();  
	            if (urlString.endsWith("!/")) {  
	                // 移除JAR文件路径和"!/"，只保留资源路径部分  
	                String resourcePath = urlString.substring(urlString.indexOf("!/") + 2);  
	                // 假设资源直接位于fci目录下，没有子目录  
	                if (resourcePath.startsWith(prefix) && resourcePath.endsWith(suffix)) {  
	                    resourceNames.add(resourcePath);  
	                }  
	            }  
	        }  
	    }  
	    return resourceNames;  
	}
	
	
	

}


