package featurecreep.loader;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.ClassTransformer;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;

import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.filesystem.PhilKatzZip;
import featurecreep.loader.finder.ModuleLoadingMap;
import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;
import featurecreep.loader.finder.NeedsFCLoaderBasic;
import featurecreep.loader.finder.FileSystemResourceLoader;
import featurecreep.loader.finder.PathResourceLoader;
import featurecreep.loader.utils.JBMUtilsAccessors;

public class FCLoaderBasicR8 extends ModuleLoader implements FCLoaderBasic  {

	public URL fc_file;
	public boolean debug_mode;
	public Path[] mod_locations;
	public Path[] classpath_locations;
	public Set<String> current_packages_exported = new HashSet<String>();;
	public ArrayList<ClassTransformer> transformers = new ArrayList<ClassTransformer>();
	public ArrayList<Module> run_only_modules = new ArrayList<Module>();
	public ArrayList<Module> modules = new ArrayList<Module>();
	public int threads;
	public Map<File, ModuleSpec> custom_root_specs = new HashMap<File, ModuleSpec>();
	//public Map<Module, ArrayList<String>> agents = new HashMap<Module, ArrayList<String>>();
	public Instrumentation instrumentation = new FCInstrumentation(this);
	public ClassTransformer main_transformer = new FCTransformer(this);
	public EventViewer eventvwr = new EventViewer();
	public ArrayList<String> known_nulls = new ArrayList<String>();
	public boolean mods_loaded = false;
	public ExecutionSide side;
	public ModuleLoadingMap module_loading_map = new ModuleLoadingMap();
	public ModuleFinder[] finders;
	
	/**
	 * Define a new instance of FCLoaderBasic
	 * @param mod_locations Folder for Mods to be ran
	 * @param classpath_locations Folders to search for mods which will not be run
	 * @param current_packages_exported Packages to Export from ClassPath
	 * @param threads  How many threads to use
	 * @param debug_mode   Does more Logging
	 * @param side Weather its server or client side or some other side
	 * @param finders The modulefinders. They should implement NeedsFCLoaderBasic if they need access to this
	 */
	public FCLoaderBasicR8(Path[] mod_locations, Path[] classpath_locations, String[] current_packages_exported,
			int threads, boolean debug_mode, ExecutionSide side, ModuleFinder[] finders) { // We will probably add more variables son
		this(FCLoaderBasic.appendFinders(finders, mod_locations, classpath_locations));
		this.mod_locations = mod_locations;
		this.classpath_locations = classpath_locations;
		this.current_packages_exported.addAll(Arrays.asList(current_packages_exported));
		this.threads = threads;
		this.debug_mode = debug_mode;
		if (!this.getDebugMode()) {
			System.out.println("Debug mode is off");
		}
		this.side = side;
		


		
		
		
		/*
		 * try { FileSystemClassPathModuleFinder.class.getDeclaredMethod(
		 * "addSystemDependencies", ModuleSpec.Builder.class).setAccessible(true); }
		 * catch (NoSuchMethodException | SecurityException e) { // TODO Auto-generated
		 * catch block e.printStackTrace(); }
		 */
		// this.known_nulls.add(this.getFeatureCreepJar().getAbsolutePath()); // Until
		// we find out why the LINKAGE error exists
	}

	/**
	 * Internal Use Only, if you really want to use this you can, but is highly condemned
	 * @param combinedmodulefinders Should be ALL the module finders
	 */
	public FCLoaderBasicR8(ModuleFinder[] combinedmodulefinders) { // We will probably add more variables son
		super(combinedmodulefinders);
		this.finders=combinedmodulefinders;
		for(ModuleFinder finder:finders) {
			
			if(finder instanceof  NeedsFCLoaderBasic) {
				NeedsFCLoaderBasic needsfcloaderbasic = (NeedsFCLoaderBasic)finder;
				needsfcloaderbasic.setFCLoaderBasic(this);
			}
			
		}
		try {
			Field supfinder = ModuleLoader.class.getDeclaredField("finders");
			supfinder.setAccessible(true);
			supfinder.set(this, finders);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
	}
	
	public FCLoaderBasicR8(Path[] mod_locations, Path[] classpath_locations, String[] current_packages_exported,
			int threads, boolean debug_mode, ExecutionSide side) { // We will probably add more variables son
			this(mod_locations,classpath_locations,current_packages_exported,threads,debug_mode,side,ModuleLoader.NO_FINDERS);
	}
	
	
	
	@Override // DONOT Use
	public void setFCFile(URL fc_file) {
		// TODO Auto-generated method stub
		this.fc_file = fc_file;
	}

	@Override
	public URL getFCFile() { // DONOT Use
		// TODO Auto-generated method stub
		return fc_file;
	}

	@Override
	public void setDebugMode(boolean bool) {
		// TODO Auto-generated method stub
		this.debug_mode = bool;
	}

	@Override
	public boolean getDebugMode() {
		// TODO Auto-generated method stub
		return debug_mode;
	}

	@Override
	public Path[] getModulePKZipLocations() {
		// TODO Auto-generated method stub
		return mod_locations;
	}

	@Override
	public Path[] getClassPathPKZipLocations() {
		// TODO Auto-generated method stub
		return classpath_locations;
	}

	@Override
	public Set<String> getNeededPackages() {
		// TODO Auto-generated method stub
		Set<String> hash_Set = new HashSet<String>();

		hash_Set.addAll(JBMUtilsAccessors.getJDKPaths());
		hash_Set.addAll(current_packages_exported);

		return hash_Set;
	}

	@Override
	public ArrayList<Module> getModules() {
		// TODO Auto-generated method stub
		return modules;
	}

	@Override
	public void loadMods() {
		// TODO Auto-generated method stub
		System.out.println("Loading Classpath Mods");

		for (int c = 0; c < getClassPathFiles().size(); c++) { // Soon I need to do with XML
			if (!this.known_nils().contains(getClassPathFiles().get(c).toString())) {
				
				File archivo = getClassPathFiles().get(c);
				this.loadModuleFromFile(archivo, false);
				
				
				
			}
		}
		System.out.println("Loading Runabble Mods");
		for (int c = 0; c < getRunOnlyFiles().size(); c++) { // Soon I need to do with XML
			if (!this.known_nils().contains(getRunOnlyFiles().get(c).toString())) {
				if (getRunOnlyFiles().get(c).isFile()) {// Temporary until we get folder modules

					File archivo = getRunOnlyFiles().get(c);
					this.loadModuleFromFile(archivo, true);
				}
			}
		}
		this.combineModuleDepSpecs();// Temp
		this.mods_loaded = true;
	}

	@Override
	public ModuleLoader getLoader() {
		return this;
	}

	@Override
	public void runMods() {
		// TODO Auto-generated method stub

		for (int m = 0; m < getRunModules().size(); m++) {

			String main = JBMUtilsAccessors.getMainClass(getRunModules().get(m));
			if (main != null && main.length() > 0) {
				System.out.println(main);
					try {
						getRunModules().get(m).run(new String[] { "" });

					} catch (NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
						// TODO Auto-generated catch block
						if (this.getDebugMode()) {
							e.printStackTrace();
						}
					}
				

			} else {
				if (this.getDebugMode()) {
					System.out.println(getRunModules().get(m).getName()
							+ " has a non-existant classname in META-INF, this could be expected or not.");
				}
			}

		}

	}

	// Need to add this to interface soon
	@Override
	public void runModule(String name) {
		// TODO Auto-generated method stub
		try {
			Module mod = this.getModule(name);
			if (mod != null) {
				mod.run(new String[] { "" });
			}
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			if (this.getDebugMode()) {
				e.printStackTrace();
			}
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			if (this.getDebugMode()) {
				e.printStackTrace();
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			if (this.getDebugMode()) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public ArrayList<ClassTransformer> getTransformers() {
		// TODO Auto-generated method stub
		return transformers;
	}

	@Override
	public ArrayList<Module> getRunModules() {
		// TODO Auto-generated method stub
		return run_only_modules;
	}

	@Override
	public void addNeededPackages(String[] packages_needed) {
		// TODO Auto-generated method stub
		current_packages_exported.addAll(Arrays.asList(packages_needed));
	}

	@Override
	public Module loadModule(String name, boolean runnable) {
		// TODO Auto-generated method stub

		Module mod = null;
		
		
		//File file = new File(name);

//		try {
//			JarFile jar = new JarFile(file);
////			if (this.checkIfPKZipHasModuleXML(jar)) {
////				this.custom_root_specs.put(file, FCLoaderBasic.getModuleSpecFromXMLJar(file, getLoader()));
////			}
//			if (jar.getManifest() != null) {
//
//				
//				
//

//
//			}
//
//			jar.close();
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			if (this.getDebugMode()) {
//				e1.printStackTrace();
//			}
//		}

		try {
			mod = getLoader().loadModule(name);
//			if (agent != null) {
//				this.agents.put(mod, agent);
//			}

		} catch (ModuleLoadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (mod != null) {
			modules.add(mod);

			if (runnable) {
				run_only_modules.add(mod);
			}
		} else {
			this.known_nils().add(name);
		}

		return mod;

	}

	@Override
	public Map<File, ModuleSpec> getCustomRootSpecs() {
		// TODO Auto-generated method stub
		return this.custom_root_specs;
	}

//	@Override
//	public Map<Module, ArrayList<String>> getAgents() {
//		// TODO Auto-generated method stub
//		return this.agents;
//	}

	@Override
	public Instrumentation getInstrumentation() {
		// TODO Auto-generated method stub
		return this.instrumentation;
	}

	@Override
	public ClassTransformer getMainTransformer() {
		// TODO Auto-generated method stub
		return main_transformer;
	}

	@Override
	public EventViewer getEventViewer() {
		// TODO Auto-generated method stub
		return this.eventvwr;
	}

	@Override
	public ArrayList<String> known_nils() {
		// TODO Auto-generated method stub
		return known_nulls;
	}

	@Override
	public boolean getModsLoaded() {
		// TODO Auto-generated method stub
		return mods_loaded;
	}

	@Override
	public ExecutionSide getExecutionSide() {
		// TODO Auto-generated method stub
		return side;
	}

	@Override
	public ClassTransformer setMainTransformer(ClassTransformer transformer) {
		// TODO Auto-generated method stub
		this.main_transformer=transformer;
		return transformer;
	}

	@Override
	public Instrumentation setInstrumentation(Instrumentation instrument) {
		// TODO Auto-generated method stub
		this.instrumentation=instrument;
		return instrument;
	}

	@Override
	public ModuleLoadingMap getModuleLoadingMap() {
		// TODO Auto-generated method stub
		return module_loading_map;
	}

	@Override
	public Module loadModuleFromFile(File file, boolean runnable) {
		// TODO Auto-generated method stub
		
		try {
			URL url = file.toURI().toURL();
			String url_as_string = url.toString();
			//TODO allow for other resource detecters
			if (file.isFile()) {
				
					ResourceLoader rl = new FileSystemResourceLoader(new PhilKatzZip(file.getCanonicalPath()));
					this.getModuleLoadingMap().put(url_as_string, new ModuleLoadingMapEntry(url_as_string,rl));
					
			}else {
				//Directory
				
				ResourceLoader rl = new PathResourceLoader(file.getCanonicalPath(), file.toPath(), this.getContext());
				this.getModuleLoadingMap().put(url_as_string,new ModuleLoadingMapEntry(url_as_string,rl));
				
			}
		return 	this.loadModule(url_as_string, runnable);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		
	}

	


}



