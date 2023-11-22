package featurecreep.loader;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.ClassTransformer;
import org.jboss.modules.FCFileSystemClassPathModuleFinder;
import org.jboss.modules.FileSystemClassPathModuleFinder;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

import featurecreep.loader.eventviewer.EventViewer;

public class FCLoaderBasicR7 implements FCLoaderBasic {

  public File fc_file;
  public boolean debug_mode;
  public Path[] mod_locations;
  public Path[] classpath_locations;
  public Set < String > current_packages_exported = new HashSet < String > ();;
  public ArrayList < ClassTransformer > transformers = new ArrayList < ClassTransformer > ();
  public ArrayList < Module > run_only_modules = new ArrayList < Module > ();
  public ArrayList < Module > modules = new ArrayList < Module > ();
  public int threads;
  public ModuleLoader loader = new ModuleLoader(new FCFileSystemClassPathModuleFinder(getBootModuleLoader(), this));

  public FCLoaderBasicR7(Path[] mod_locations, Path[] classpath_locations, String[] current_packages_exported, int threads, boolean debug_mode) { //We will probably add more variables son
    this.mod_locations = mod_locations;
    this.classpath_locations = classpath_locations;
    this.current_packages_exported.addAll(Arrays.asList(current_packages_exported));
    this.threads = threads;
    this.debug_mode = debug_mode;
    try {
      FileSystemClassPathModuleFinder.class.getDeclaredMethod("addSystemDependencies", ModuleSpec.Builder.class).setAccessible(true);
    } catch (NoSuchMethodException | SecurityException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override //DONOT Use
  public void setFCFile(File fc_file) {
    // TODO Auto-generated method stub
    this.fc_file = fc_file;
  }

  @Override
  public File getFCFile() { //DONOT Use
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
  public Set < String > getNeededPackages() {
    // TODO Auto-generated method stub
    Set < String > hash_Set = new HashSet < String > ();

    hash_Set.addAll(FCFileSystemClassPathModuleFinder.jdk_paths);
    hash_Set.addAll(current_packages_exported);

    return hash_Set;
  }

  @Override
  public ArrayList < Module > getModules() {
    // TODO Auto-generated method stub
    return modules;
  }

  @Override
  public void loadMods() {
    // TODO Auto-generated method stub
    System.out.println("Loading Classpath Mods");
    for (int c = 0; c < getClassPathFiles().size(); c++) { // Soon I need to do with XML
      Module mod = null;
      try {
        mod = getLoader().loadModule(getClassPathFiles().get(c).toString());

      } catch (ModuleLoadException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      modules.add(mod);
    }
    System.out.println("Loading Runabble Mods");
    for (int c = 0; c < getRunOnlyFiles().size(); c++) { // Soon I need to do with XML
      Module mod = null;

      try {
        mod = getLoader().loadModule(getRunOnlyFiles().get(c).toString());
      } catch (ModuleLoadException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      modules.add(mod);
      run_only_modules.add(mod);
    }

  }

  @Override public ModuleLoader getLoader() {
    return loader;
  }

  @Override
  public void runMods() {
    // TODO Auto-generated method stub

    for (int m = 0; m < getRunModules().size(); m++) {

      String main = FCFileSystemClassPathModuleFinder.getMainClass(getRunModules().get(m));
      if (main != null) {
System.out.println(main);
      if (!main.equals("net.coderbot.iris.LaunchWarn")) { //Bandaid Solution, need to remove soon
        try {

          getRunModules().get(m).run(new String[] {
            ""
          });
        } catch (NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }

    }else {System.out.println(getRunModules().get(m).getName() +" has a non-existant classname in META-INF, this could be expected or not.");}
      
    }

  }

  @Override
  public void runModule(String name) {
    // TODO Auto-generated method stub
    try {
      getLoader().loadModule(name).run(new String[] {
        ""
      });
    } catch (ModuleLoadException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public ModuleLoader getBootModuleLoader() {
    // TODO Auto-generated method stub
    return AccessController.doPrivileged(new PrivilegedAction < ModuleLoader > () {
      public ModuleLoader run() {
        final String loaderClass = System.getProperty("boot.module.loader", LocalModuleLoader.class.getName());
        try {
          return Class.forName(loaderClass, true, FCLoaderBasicR7.class.getClassLoader()).asSubclass(ModuleLoader.class).getConstructor().newInstance();
          //  return Class.forName(LocalModuleLoader.class.getName(), true, FCLoaderBasicR4.class.getClassLoader()).asSubclass(ModuleLoader.class).getConstructor().newInstance();
          //      return Class.forName(LocalModuleLoader.class.getName(), true, FCLoaderBasicR4.class.getClassLoader()).asSubclass(ModuleLoader.class).getConstructor().newInstance();

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

  @Override
  public ArrayList < ClassTransformer > getTransformers() {
    // TODO Auto-generated method stub
    return transformers;
  }

  @Override
  public ArrayList < Module > getRunModules() {
    // TODO Auto-generated method stub
    return run_only_modules;
  }

  @Override
  public void addNeededPackages(String[] packages_needed) {
    // TODO Auto-generated method stub
    current_packages_exported.addAll(Arrays.asList(packages_needed));
  }

  //UNSUPPORTED IN R7
@Override
public Module loadModule(String name, boolean runnable) {
	// TODO Auto-generated method stub
	return null;
}

@Override
public Map<File, ModuleSpec> getCustomRootSpecs() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public Map<Module, ArrayList<String>> getAgents() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public Instrumentation getInstrumentation() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public void runAgents() {
	// TODO Auto-generated method stub
	
}

@Override
public ClassTransformer getMainTransformer() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public EventViewer getEventViewer() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public ArrayList<String> known_nils() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public boolean getModsLoaded() {
	// TODO Auto-generated method stub
	return false;
}

}
