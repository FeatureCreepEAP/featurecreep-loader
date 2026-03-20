package featurecreep.loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

import org.jboss.modules.ClassTransformer;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.JLIClassTransformer;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleDependencySpecBuilder;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PreMain;
import org.jboss.modules.Resource;

import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.filesystem.PhilKatzZip;
import featurecreep.loader.finder.FCFileSystemClassPathFinder;
import featurecreep.loader.finder.FileSystemResourceLoader;
import featurecreep.loader.finder.ModuleLoadingMap;
import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;
import featurecreep.loader.utils.ArrayCombiner;
import featurecreep.loader.utils.JBMUtilsAccessors;

public interface FCLoaderBasic {

	public GameProvider getGameProvider();

	public default boolean setDebugMode(boolean val) {
		return getGameProvider().setDebugMode(val);
	}

	public default boolean getDebugMode() {
		return getGameProvider().getDebugMode();
	}

	public default Path[] getModulePKZipLocations() {
		return getGameProvider().getModulePKZipLocations();
	}

	public default Path[] getClassPathPKZipLocations() {
		return getGameProvider().getClassPathPKZipLocations();
	}

	public FileTypes filetypes = new FileTypes();// Is ok to be static and final

	public ArrayList<String> known_nils();

	default Path[] getCombindedModulePKZipLocations() {
		ArrayCombiner<Path> combiner = new ArrayCombiner<Path>();
		return combiner.combineArrays(getModulePKZipLocations(), getClassPathPKZipLocations());

	}

	public default Set<String> getNeededPackages() {
		return this.getGameProvider().getNeededPackages();
	}

	public default ModuleLoader getLoader() {
		return getBootModuleLoader();
	}

	public ArrayList<Module> getModules();

	public ArrayList<Module> getRunModules();

	public void loadMods();

	public void runMods();

	public void runModule(String name);

	public static ModuleLoader getBootModuleLoader() {
		// TODO Auto-generated method stub
		return AccessController.doPrivileged(new PrivilegedAction<ModuleLoader>() {
			public ModuleLoader run() {
				final String loaderClass = System.getProperty("boot.module.loader", LocalModuleLoader.class.getName());
				try {
					return Class.forName(loaderClass, true, FCLoaderBasic.class.getClassLoader())
							.asSubclass(ModuleLoader.class).getConstructor().newInstance();
					// return Class.forName(LocalModuleLoader.class.getName(), true,
					// FCLoaderBasicR4.class.getClassLoader()).asSubclass(ModuleLoader.class).getConstructor().newInstance();
					// return Class.forName(LocalModuleLoader.class.getName(), true,
					// FCLoaderBasicR4.class.getClassLoader()).asSubclass(ModuleLoader.class).getConstructor().newInstance();

				} catch (InstantiationException e) {
					throw new InstantiationError(e.getMessage());
				} catch (IllegalAccessException e) {
					throw new IllegalAccessError(e.getMessage());
				} catch (InvocationTargetException e) {
					try {
						throw e.getCause();
					} catch (RuntimeException cause) {
						throw cause;
					} catch (Error cause) {
						throw cause;
					} catch (Throwable t) {
						throw new Error(t);
					}
				} catch (NoSuchMethodException e) {
					throw new NoSuchMethodError(e.getMessage());
				} catch (ClassNotFoundException e) {
					throw new NoClassDefFoundError(e.getMessage());
				}
			}
		});
	}

	public ModuleLoadingMap getModuleLoadingMap();

//	public static InputStream getModuleXMLFromJarAsInputStream(File location) throws IOException {
//		JarFile jar;
//
//		jar = new JarFile(location.toString());
//		//StringBuilder contentBuilder = new StringBuilder();
//		InputStream stream = jar.getInputStream(jar.getJarEntry("module.xml"));
//		jar.close();
//		return stream;
//
//	}

//	public default String getModuleXMLFromJarAsString(File location) throws IOException {
//		String text = new BufferedReader(
//				new InputStreamReader(getModuleXMLFromJarAsInputStream(location), StandardCharsets.UTF_8)).lines()
//				.collect(Collectors.joining("\n"));
//		return text;
//
//		// TODO Auto-generated catch block
//		// System.out.println(location.toString() + " Likely not a PkZip/Jar File");
//
//	}

//	public static ModuleSpec getModuleSpecFromXMLJar(File location, ModuleLoader loader) throws IOException, ModuleLoadException {
//
//		return ModuleXmlParser.parseModuleXml(ResourceRootFactory.getDefault(), MavenResolver.createDefaultResolver(),
//				location.toString(), getModuleXMLFromJarAsInputStream(location), location.toString(), loader,
//				location.toString());
//
//		// TODO Auto-generated catch block
//
//	}

	public static MethodType PREMAIN_METHOD_TYPE() {
		return MethodType.methodType(void.class, String.class, Instrumentation.class);
	}

	public static MethodType PREMAIN_EVENTVWR_METHOD_TYPE() {
		return MethodType.methodType(void.class, EventViewer.class);
	}

//	public default boolean checkIfPKZipHasModuleXML(JarFile location) {
//		try {
//			JarEntry entry = location.getJarEntry("module.xml");
//			if (entry != null) {
//				InputStream stream = location.getInputStream(entry);
//				return true;
//			}
//			return false;
//
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			if (this.getDebugMode()) {
//				System.out.println(location.toString()
//						+ " Likely not a PkZip/Jar File or does not have module.xml, note that module.xml parsing is still new so expect issues");
//			}
//			return false;
//			// e.printStackTrace();
//		}
//
//	}

	public ArrayList<ClassTransformer> getTransformers();

	public default ArrayList<File> getRunOnlyFiles() {
		ArrayList<File> fils = new ArrayList<File>();
		for (int m = 0; m < getModulePKZipLocations().length; m++) {
			if (getModulePKZipLocations()[m].toFile().listFiles() != null) {
				for (int f = 0; f < getModulePKZipLocations()[m].toFile().listFiles().length; f++) {
					fils.add(getModulePKZipLocations()[m].toFile().listFiles()[f]);
				}
			}
		}
		return fils;
	}

	public default ArrayList<File> getClassPathFiles() {
		ArrayList<File> fils = new ArrayList<File>();
		for (int r = 0; r < getClassPathPKZipLocations().length; r++) {
			if (getClassPathPKZipLocations()[r].toFile().listFiles() != null) {
				for (int f = 0; f < getClassPathPKZipLocations()[r].toFile().listFiles().length; f++) {
					fils.add(getClassPathPKZipLocations()[r].toFile().listFiles()[f]);

				}
			}
		}
		return fils;
	}

	public default ArrayList<File> getCombinedFiles() {
		ArrayList<File> fils = new ArrayList<File>();
		for (int r = 0; r < getClassPathPKZipLocations().length; r++) {
			if (getClassPathPKZipLocations()[r].toFile().listFiles() != null) {
				for (int f = 0; f < getClassPathPKZipLocations()[r].toFile().listFiles().length; f++) {
					fils.add(getClassPathPKZipLocations()[r].toFile().listFiles()[f]);
				}
			}
		}
		for (int m = 0; m < getModulePKZipLocations().length; m++) {
			if (getModulePKZipLocations()[m].toFile().listFiles() != null) {
				for (int f = 0; f < getModulePKZipLocations()[m].toFile().listFiles().length; f++) {
					fils.add(getModulePKZipLocations()[m].toFile().listFiles()[f]);
				}
			}
		}

		return fils;
	}

	@Deprecated // use game providers
	public void addNeededPackages(String[] packages_needed);

	@Deprecated // Do not use
	public default File getFeatureCreepJar() {

		for (File file : getCombinedFiles()) {
			if (isFilePKZipCompatible(file)) {
				if (pkZipHasFile("featurecreep/loader/FCLoaderBasic.class", file)) {
					return file;

				}
			}

		}
		return null; // TODO Auto-generated catch block
	}

	public static boolean isFilePKZipCompatible(File file) {
		try {
			return isFilePKZipCompatible(file.toURI().toURL());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	// Maybe make it read byte[] instead? Though that can be different for some pk
	// zips
	public static boolean isFilePKZipCompatible(URL url) {

		File fil = new File(url.getFile());
		if (fil.exists() && fil.isDirectory()) {
			return false;
		}

		InputStream stream;
		try {
			stream = url.openStream();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		ZipInputStream zip = new ZipInputStream(stream);
		try {
			zip.getNextEntry();
			zip.close();
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			return false;
		}
	}

	public static boolean doesFileEndInPKZipExtension(String url) {
		for (String end : FileTypes.PKZIP_COMPATIBLES) {
			if (url.endsWith(end)) {
				return true;
			}

		}
		return false;
	}

	public static boolean pkZipHasFile(String filename, File file) {
		try {
			JarFile jar = new JarFile(file);
			for (JarEntry entry : Collections.list(jar.entries())) {
				if (entry.getName().equals(filename)) {
					jar.close();
					return true;
				}
			}
			jar.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	// Maybe making these check the getModule for null would be better?
	public default boolean hasModule(String name) {

		for (Module mod : this.getModules()) {
			if (mod.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public default Module getModule(String name) {

		for (Module mod : this.getModules()) {
			if (mod.getName().equals(name)) {
				return mod;
			}
		}
		return null;
	}

	public Module loadModule(String name, boolean runnable);

	public Module loadModuleFromFile(File file, boolean runnable);

	public default Module loadModuleFromResource(Resource res) {
		// TODO Auto-generated method stub
		URL url = res.getURL();
		String string = url.toString();
		try {
			this.getModuleLoadingMap().put(string, new ModuleLoadingMapEntry(string,
					new FileSystemResourceLoader(new PhilKatzZip(res.openStream(), res.getURL().toURI()))));
		} catch (IOException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.loadModule(string, true);
	}

	// Not for Jar in Jars
	public Map<File, ModuleSpec> getCustomRootSpecs();

	// public Map<Module, ArrayList<String>> getAgents();

	/**
	 * Solo usas para Agentes. Usa en GameProvider
	 * 
	 * @param instrument
	 * @return
	 */
	@Deprecated
	public default Instrumentation setInstrumentation(Instrumentation instrument) {
		return this.getGameProvider().setInstrumentation(instrument);
	}

	/**
	 * This should be the instrumenations for HotSwapper from loading as an agent.
	 * 
	 * @return
	 */
	public default Instrumentation getInstrumentationForAgent() {
		return this.getGameProvider().getInstrumentation();
	}

	/**
	 * Actual Instrumentation. When implemented this should return
	 * getInstrumentationForAgent() if it is not null, if not it should create an
	 * implementation of FCInstrumentation
	 * 
	 * @return
	 */
	public Instrumentation getInstrumentation();

	public static ClassTransformer fromClassFileTransformer(ClassFileTransformer transformer) {
		return new JLIClassTransformer(transformer);
	}

	public static ClassFileTransformer fromClassTransformer(ClassTransformer transformer) {
		class Returned implements ClassFileTransformer {
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
					ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

				ByteBuffer buff = transformer.transform(loader, className, protectionDomain,
						ByteBuffer.wrap(classfileBuffer));
				if (buff != null) {
					return buff.array();
				}

				return classfileBuffer;
			}

		}
		Returned ret = new Returned();
		return ret;

	}

	public default void addTransformer(ClassTransformer transformer) {
		this.getTransformers().add(transformer);
	}

	public default void addTransformer(ClassFileTransformer transformer) {
		this.getTransformers().add(new JLIClassTransformer(transformer));
	}

	public default void PremainAgents() {
		for (Module agent : this.getRunModules()) {
			this.runAgentPremain(agent);
		}
	}

//Need to account for main and premain differences, i originally thought they could have been together
	public default void runAgentPremain(Module agent) {

		String preagent_class = agent.getProperty("Premain-Class");
		String early_listener_class = agent.getProperty("EarlyListener-Class");

		ArrayList<String> preagent = new ArrayList<String>();
		ArrayList<String> early_listeners = new ArrayList<String>();

		if (preagent_class != null) {
			preagent.addAll(Arrays.asList(preagent_class.split(",")));
		}

		if (early_listener_class != null) {
			early_listeners.addAll(Arrays.asList(early_listener_class.split(",")));
		}

		final ClassLoader oldClassLoader = JBMUtilsAccessors.setContextClassLoader(agent.getClassLoader());
		try {

			for (String agent_clazz : preagent) {

				final Class<?> mainClass = Class.forName(agent_clazz, false, agent.getClassLoader());

				Class.forName(agent_clazz, true, agent.getClassLoader());

				final MethodHandles.Lookup lookup = MethodHandles.lookup();
				final MethodHandle methodHandle;
				// final MethodHandle methodHandleEventVwr;
				// final MethodHandle methodHandleMain;

				try {
					methodHandle = lookup.findStatic(mainClass, "premain", PREMAIN_METHOD_TYPE());
					methodHandle.invokeExact(new String(""), this.getInstrumentation());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

			ServiceLoader<PreMain> premains = agent.loadService(PreMain.class);
			for (PreMain premain : premains) {
				premain.run(new ArrayList<String>());// No Args ATM
			}

			for (String agent_clazz : early_listeners) {

				final Class<?> mainClass = Class.forName(agent_clazz, false, agent.getClassLoader());

				Class.forName(agent_clazz, true, agent.getClassLoader());

				final MethodHandles.Lookup lookup = MethodHandles.lookup();
				final MethodHandle methodHandleEventVwr;

				try {
					methodHandleEventVwr = lookup.findStatic(mainClass, "registerEarlyEventListeners",
							PREMAIN_EVENTVWR_METHOD_TYPE());
					methodHandleEventVwr.invokeExact(this.getEventViewer());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
				}

			}

		} catch (Throwable e) {
			// TODO Auto-generated catch block
			if (this.getDebugMode()) {
				e.printStackTrace();
			}
		} finally {
			JBMUtilsAccessors.setContextClassLoader(oldClassLoader);
		}

	}

	// Need to account for main and premain differences, i originally thought they
	// could have been together
	public default void runAgent(Module agent) {

		String agent_class = agent.getProperty("Agent-Class");

		ArrayList<String> agents = new ArrayList<String>();

		if (agent_class != null) {
			agents.addAll(Arrays.asList(agent_class.split(",")));
		}

		try {

			for (String agent_clazz : agents) {

				final Class<?> mainClass = Class.forName(agent_clazz, false, agent.getClassLoader());

				Class.forName(agent_clazz, true, agent.getClassLoader());

				final MethodHandles.Lookup lookup = MethodHandles.lookup();
				final MethodHandle methodHandleMain;

				try {
					methodHandleMain = lookup.findStatic(mainClass, "agentmain", PREMAIN_METHOD_TYPE());
					// TODO: this is a hack to make sure that the instrumentation is set correctly.
					// no args yet
					methodHandleMain.invokeExact(new String(""), this.getInstrumentation());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
				}

			}

		} catch (Throwable e) {
			// TODO Auto-generated catch block
			if (this.getDebugMode()) {
				e.printStackTrace();
			}
		}

	}

	public ClassTransformer getMainTransformer();

	/**
	 * Internal, for constructor. You MUST implement NeedsFCLoaderBasic if you need
	 * access to FCLoaderBasic
	 * 
	 * @return
	 */
	public static ModuleFinder[] getFinders(GameProvider prov) {
		// TODO

		List<ModuleFinder> finders = new LinkedList<>();
		Path[] mod_locations = prov.getModulePKZipLocations();
		Path[] classpath_locations = prov.getClassPathPKZipLocations();

		finders.addAll(prov.getDefaultModuleFinders());

		// Collect all potential finder sources
		List<File> finderSources = new ArrayList<>();
		ModuleFinderDiscovery.collectFinderSources(mod_locations, finderSources);
		ModuleFinderDiscovery.collectFinderSources(classpath_locations, finderSources);

		// For each source, try to load finders from it
		for (File source : finderSources) {
			ModuleFinderDiscovery.loadFindersFromSource(source, finders, getBootModuleLoader());
		}

		// Add the default finder
		finders.add(new FCFileSystemClassPathFinder(getBootModuleLoader()));

		return finders.toArray(new ModuleFinder[0]);

	}

	/*
	 * This should be run BEFORE any transformers are added
	 */
	public ClassTransformer setMainTransformer(ClassTransformer transformer);

	public EventViewer getEventViewer();

	public default List<DependencySpec> getCombinedDepSpecs(Module mod) {
		List<DependencySpec> output = new ArrayList<DependencySpec>();
		for (DependencySpec dep_spec : mod.getDependencies()) {
			output.add(dep_spec);
		}

		for (Module module : this.getModules()) {
			DependencySpec spec = new ModuleDependencySpecBuilder()
					// .setModuleLoader(agentModule.getModuleLoader())
					.setName(module.getName()).build();
			if (!output.contains(spec)) {
				output.add(spec);
			}

		}

		return output;
	}

	public default void combineModuleDepSpecs() {
		for (Module mod : this.getModules()) {
			JBMUtilsAccessors.setModuleDependencies(mod, this.getCombinedDepSpecs(mod));
		}
	}

	public boolean getModsLoaded();

	public default ExecutionSide getExecutionSide() {
		return this.getGameProvider().getExecutionSide();
	}

	@SuppressWarnings({ "removal", "deprecation" })
	public default AccessControlContext getContext() {
		return AccessController.getContext();
	}

}
