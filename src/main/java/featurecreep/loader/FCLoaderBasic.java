package featurecreep.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.jboss.modules.ClassTransformer;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.FCFileSystemClassPathModuleFinder;
import org.jboss.modules.JLIClassTransformer;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleDependencySpecBuilder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.maven.MavenResolver;
import org.jboss.modules.xml.ModuleXmlParser;
import org.jboss.modules.xml.ModuleXmlParser.ResourceRootFactory;

import featurecreep.loader.eventviewer.EventViewer;

public interface FCLoaderBasic {

	public void setFCFile(File fc_file); // DONOT Use

	public File getFCFile(); // DONOT Use

	public void setDebugMode(boolean bool);

	public boolean getDebugMode();

	public Path[] getModulePKZipLocations();

	public Path[] getClassPathPKZipLocations();

	public FileTypes filetypes = new FileTypes();// Is ok to be static and final

	public ArrayList<String> known_nils();

	default Path[] getCombindedModulePKZipLocations() {
		ArrayList<Path> combined = new ArrayList<Path>();
		for (int m = 0; m < getModulePKZipLocations().length; m++) {
			combined.add(getModulePKZipLocations()[m]);
		}
		for (int c = 0; c < getClassPathPKZipLocations().length; c++) {
			combined.add(getClassPathPKZipLocations()[c]);
		}
		// https://stackoverflow.com/questions/18119494/why-cant-cast-object-to-string#18119737
		return combined.toArray(new Path[getModulePKZipLocations().length + getClassPathPKZipLocations().length]);
	}

	public Set<String> getNeededPackages();

	public default ModuleLoader getLoader() {
		return getBootModuleLoader();
	}

	public ArrayList<Module> getModules();

	public ArrayList<Module> getRunModules();

	public void loadMods();

	public void runMods();

	public void runModule(String name);

	public default void runModule(ModuleIdentifier id) {
		runModule(id.getName());
	}

	public ModuleLoader getBootModuleLoader();

	public default InputStream getModuleXMLFromJarAsInputStream(File location) throws IOException {
		JarFile jar;

		jar = new JarFile(location.toString());
		//StringBuilder contentBuilder = new StringBuilder();
		InputStream stream = jar.getInputStream(jar.getJarEntry("module.xml"));
		jar.close();
		return stream;

	}

	public default String getModuleXMLFromJarAsString(File location) throws IOException {
		String text = new BufferedReader(
				new InputStreamReader(getModuleXMLFromJarAsInputStream(location), StandardCharsets.UTF_8)).lines()
				.collect(Collectors.joining("\n"));
		return text;

		// TODO Auto-generated catch block
		// System.out.println(location.toString() + " Likely not a PkZip/Jar File");

	}

	public default ModuleSpec getModuleSpecFromXMLJar(File location) throws IOException, ModuleLoadException {

		return ModuleXmlParser.parseModuleXml(ResourceRootFactory.getDefault(), MavenResolver.createDefaultResolver(),
				location.toString(), getModuleXMLFromJarAsInputStream(location), location.toString(), getLoader(),
				location.toString());

		// TODO Auto-generated catch block

	}

	public static MethodType PREMAIN_METHOD_TYPE() {
		return MethodType.methodType(void.class, String.class, Instrumentation.class);
	}

	public static MethodType PREMAIN_EVENTVWR_METHOD_TYPE() {
		return MethodType.methodType(void.class, EventViewer.class);
	}

	public default boolean checkIfPKZipHasModuleXML(JarFile location) {
		try {
			JarEntry entry = location.getJarEntry("module.xml");
			if (entry != null) {
				InputStream stream = location.getInputStream(entry);
				return true;
			}
			return false;

		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (this.getDebugMode()) {
				System.out.println(location.toString()
						+ " Likely not a PkZip/Jar File or does not have module.xml, note that module.xml parsing is still new so expect issues");
			}
			return false;
			// e.printStackTrace();
		}

	}

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

	public void addNeededPackages(String[] packages_needed);

	public default File getFeatureCreepJar() {

		for (File file : getCombinedFiles()) {
			if (isFilePKZipCompatible(file)) {
				if (pkZipHasFile("featurecreep/loader/FCLoaderBasic.class", file)) {
					return file;

				}
			}
			System.out.println(file.toString());

		}
		return null; // TODO Auto-generated catch block
	}

	// We eventually need to make this check with byte[]
	public static boolean isFilePKZipCompatible(File file) {
		for (String end : filetypes.PKZIP_COMPATIBLES) {
			if (file.toString().endsWith(end)) {
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
					return true;
				}
			}
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

	public default boolean hasModule(ModuleIdentifier modid) {
		for (Module mod : this.getModules()) {
			if (mod.getIdentifier().equals(modid)) {
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

	public default Module getModule(ModuleIdentifier modid) {

		for (Module mod : this.getModules()) {
			if (mod.getIdentifier().equals(modid)) {
				return mod;
			}
		}
		return null;
	}

	public Module loadModule(String name, boolean runnable);

	// Not for Jar in Jars
	public Map<File, ModuleSpec> getCustomRootSpecs();

	public Map<Module, ArrayList<String>> getAgents();

	/**
	 * Solo usas para Agentes
	 * @param instrument
	 * @return
	 */
	public Instrumentation setInstrumentation(Instrumentation instrument);
	
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

	public default void runAgents() {
		for (Module agent : this.getAgents().keySet()) {
			this.runAgent(agent);
		}
	}

//Need to account for main and premain differences, i originally thought they could have been together
	public default void runAgent(Module agent) {
		for (String agent_clazz : this.getAgents().get(agent)) {
			System.out.println(agent_clazz);
			final ClassLoader oldClassLoader = FCFileSystemClassPathModuleFinder
					.setContextClassLoader(agent.getClassLoader());
			try {
				final Class<?> mainClass = Class.forName(agent_clazz, false, agent.getClassLoader());

				Class.forName(agent_clazz, true, agent.getClassLoader());

				final MethodHandles.Lookup lookup = MethodHandles.lookup();
				final MethodHandle methodHandle;
				final MethodHandle methodHandleEventVwr;
				final MethodHandle methodHandleMain;

				try {
					methodHandle = lookup.findStatic(mainClass, "premain", PREMAIN_METHOD_TYPE());
					methodHandle.invokeExact(new String(""), this.getInstrumentation());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				try {
					methodHandleEventVwr = lookup.findStatic(mainClass, "registerEarlyEventListeners",
							PREMAIN_EVENTVWR_METHOD_TYPE());
					methodHandleEventVwr.invokeExact(this.getEventViewer());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
				}

				try {
					methodHandleMain = lookup.findStatic(mainClass, "agentmain", PREMAIN_METHOD_TYPE());
//TODO: this is a hack to make sure that the instrumentation is set correctly. no args yet    
					methodHandleMain.invokeExact(new String(""), this.getInstrumentation());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
				}

			} catch (Throwable e) {
				// TODO Auto-generated catch block
				if (this.getDebugMode()) {
					e.printStackTrace();
				}
			} finally {
				FCFileSystemClassPathModuleFinder.setContextClassLoader(oldClassLoader);
			}

		}

	}

	public ClassTransformer getMainTransformer();

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
			FCFileSystemClassPathModuleFinder.setModuleDependencies(mod, this.getCombinedDepSpecs(mod));
		}
	}

	public boolean getModsLoaded();

	public ExecutionSide getExecutionSide();

}


