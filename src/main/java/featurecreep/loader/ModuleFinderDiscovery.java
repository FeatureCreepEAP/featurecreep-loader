package featurecreep.loader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoader;

import featurecreep.loader.filesystem.PhilKatzZip;

public class ModuleFinderDiscovery {

	static void collectFinderSources(Path[] paths, List<File> finderSources) {
		if (paths == null)
			return;

		for (Path path : paths) {
			try {
				if (Files.isDirectory(path)) {
					// Check subdirectories for those containing "mf" in their name
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
						for (Path entry : stream) {
							if (Files.isDirectory(entry)
									&& entry.getFileName().toString().toLowerCase().contains("mf")) {
								finderSources.add(entry.toFile());
							}
						}
					}
				} else if (FCLoaderBasic.isFilePKZipCompatible(path.toFile())) {
					finderSources.add(path.toFile());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static void loadFindersFromSource(File source, List<ModuleFinder> finders, ModuleLoader bootModuleLoader) {
		try {
			Manifest manifest = getManifest(source);
			if (manifest != null) {
				processManifest(manifest, source, finders, bootModuleLoader);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static Manifest getManifest(File source) throws IOException {
		if (source.isDirectory()) {
			File manifestFile = new File(source, "META-INF/MANIFEST.MF");
			if (manifestFile.exists()) {
				try (FileInputStream fis = new FileInputStream(manifestFile)) {
					return new Manifest(fis);
				}
			}
		} else {
			// It's a JAR file - properly close ZIP resources
			PhilKatzZip zip = new PhilKatzZip(source.getAbsolutePath());
			try (InputStream is = zip.getStream("META-INF/MANIFEST.MF")) {
				if (is != null) {
					return new Manifest(is);
				}
			}
		}
		return null;
	}

	private static void processManifest(Manifest manifest, File source, List<ModuleFinder> finders,
			ModuleLoader bootModuleLoader) {
		Attributes attributes = manifest.getMainAttributes();
		String finderClassNames = attributes.getValue("JBOSS_MODULE_FINDER");
		if (finderClassNames != null && !finderClassNames.trim().isEmpty()) {
			String[] classNames = finderClassNames.split(",");
			ClassLoader sourceClassLoader = createSourceClassLoader(source);
			for (String className : classNames) {
				className = className.trim();
				try {
					Class<?> clazz = Class.forName(className, true, sourceClassLoader);
					if (ModuleFinder.class.isAssignableFrom(clazz)) {
						// Handle both constructor types
						try {
							// Try constructor with ModuleLoader first
							Constructor<?> constructor = clazz.getConstructor(ModuleLoader.class);
							ModuleFinder finder = (ModuleFinder) constructor.newInstance(bootModuleLoader);
							finders.add(finder);
						} catch (NoSuchMethodException e) {
							// Fall back to no-arg constructor
							Constructor<?> constructor = clazz.getConstructor();
							ModuleFinder finder = (ModuleFinder) constructor.newInstance();
							finders.add(finder);
						}
					}
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
						| NoSuchMethodException | InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static ClassLoader createSourceClassLoader(final File source) {
		return new ClassLoader(FCLoaderBasic.class.getClassLoader()) {
			// CACHE ZIP INSTANCE FOR JAR SOURCES
			private final PhilKatzZip zip;

			{
				if (source.isFile()) {
					try {
						this.zip = new PhilKatzZip(source.getAbsolutePath());
					} catch (Exception e) {
						throw new RuntimeException("Failed to initialise ZIP for " + source, e);
					}
				} else {
					this.zip = null;
				}
			}

			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				try {
					String path = name.replace('.', '/') + ".class";
					byte[] classData = loadClassData(path);
					if (classData != null) {
						return defineClass(name, classData, 0, classData.length);
					}
				} catch (IOException e) {
					// Fall through
				}
				throw new ClassNotFoundException(name);
			}

			private byte[] loadClassData(String path) throws IOException {
				if (source.isDirectory()) {
					File classFile = new File(source, path);
					if (classFile.exists()) {
						return readFile(classFile);
					}
				} else {
					// USE CACHED ZIP INSTANCE
					try (InputStream is = zip.getStream(path)) {
						if (is != null) {
							return readStream(is);
						}
					}
				}
				return null;
			}

			private byte[] readFile(File file) throws IOException {
				try (FileInputStream fis = new FileInputStream(file)) {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int len;
					while ((len = fis.read(buffer)) != -1) {
						bos.write(buffer, 0, len);
					}
					return bos.toByteArray();
				}
			}

			private byte[] readStream(InputStream is) throws IOException {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = is.read(buffer)) != -1) {
					bos.write(buffer, 0, len);
				}
				return bos.toByteArray();
			}

		};
	}
}