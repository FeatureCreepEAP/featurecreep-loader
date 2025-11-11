package featurecreep.loader;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.jboss.modules.ModuleFinder;


/**
 * This is the App/GameProvider used for giving certain information to the imlpementation of FCLoaderBasic
 */
public interface GameProvider {

	/**
	 * Enhanced Logging
	 * @return
	 */
	public boolean getDebugMode();
	
	/**
	 * Enhanced Logging
	 * @return
	 */
	public boolean setDebugMode(boolean val);


	/**
	 * Folders for mods
	 * @return
	 */
	public Path[] getModulePKZipLocations();

	/**
	 * Folders to look for mods which are only going to be added to classpath, but not run
	 * @return
	 */
	public Path[] getClassPathPKZipLocations();

	/**
	 * This is for replacing the instrumentation if it exists It should only  be the one from a HotSwappable Agent, if not it should be null, the implementation of FCLoaderBasic should make a new FCInstrumentation if this is null. 
	 * @param instrument
	 * @return
	 */
	public Instrumentation getInstrumentation();

	/**
	 * This is for replacing the instrumentation if it exists It should only  be the one from a HotSwappable Agent, if not it should be null, the implementation of FCLoaderBasic should make a new FCInstrumentation if this is null. 
	 * @param instrument
	 * @return
	 */
	public Instrumentation setInstrumentation(Instrumentation instrument);

	
	/**
	 * These are the packages that will be accessible from the current classloader to the Modules Loaded from JBoss Modules. If the packages are not defined here a new instance of them will be made if they are loaded by JBoss Modules, if they are not loaded by JBoss Modules the contents of these packages will not be accessible by the modules in JBoss Modules.
	 * @return
	 */
	public Set<String> getNeededPackages();
	
	
	/**
	 * These are the packages that will be accessible from the current classloader to the Modules Loaded from JBoss Modules. If the packages are not defined here a new instance of them will be made if they are loaded by JBoss Modules, if they are not loaded by JBoss Modules the contents of these packages will not be accessible by the modules in JBoss Modules.
	 * @return
	 */
	public void addNeededPackage();
	
	
	/**
	 * Similar to .nil.jar some Apps have certain extentions to indicate they are supposed to be disabled. This can also be impacted by different launchers
	 * @return
	 */
	public List<String> getAvoidedModSuffixes();

	/**
	 * Execution Side can be things like Client or Server depending on where it is being run from
	 * @return
	 */
	public ExecutionSide getExecutionSide();
	
	/**
	 * This should be the default module finders to use for looking for mods. It should not include those from mods or FCFileSystemClassPathFinder as both of these need to be added by the FCLoaderBasicImplementation
	 * @return
	 */
	public List<ModuleFinder> getDefaultModuleFinders();

	
}
