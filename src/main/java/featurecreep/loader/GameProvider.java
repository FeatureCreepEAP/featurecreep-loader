package featurecreep.loader;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.jboss.modules.ModuleFinder;

/**
 * This is the App/GameProvider used for giving certain information to the
 * implementation of FCLoaderBasic.
 */
public interface GameProvider {

	/**
	 * Enhanced Logging
	 * 
	 * @return {@code true} if debug mode is enabled, {@code false} otherwise
	 */
	boolean getDebugMode();

	/**
	 * Enables or disables enhanced logging.
	 * 
	 * @param val {@code true} to enable debug mode, {@code false} to disable
	 * @return the previous debug mode value
	 */
	boolean setDebugMode(boolean val);

	/**
	 * Folders containing mods that should be loaded as modules.
	 * 
	 * @return an array of paths to module-compatible mod JARs or directories
	 */
	Path[] getModulePKZipLocations();

	/**
	 * Folders to look for mods which are only added to the classpath but not run as
	 * modules.
	 * 
	 * @return an array of paths to classpath-only mod JARs or directories
	 */
	Path[] getClassPathPKZipLocations();

	/**
	 * Returns the instrumentation instance, if available. This should only be
	 * non-null if provided by a hot-swappable Java agent. If null, the
	 * FCLoaderBasic implementation should create a default FCInstrumentation.
	 * 
	 * @return the instrumentation instance, or {@code null} if not available
	 */
	Instrumentation getInstrumentation();

	/**
	 * Sets the instrumentation instance. This should only be used by a
	 * hot-swappable Java agent.
	 * 
	 * @param instrument the instrumentation to set
	 * @return the previous instrumentation instance
	 */
	Instrumentation setInstrumentation(Instrumentation instrument);

	/**
	 * Returns the set of packages that must be shared between the system class
	 * loader and JBoss Modules. Packages not listed here may be duplicated or
	 * become inaccessible to loaded modules.
	 * 
	 * @return a set of fully qualified package names to export
	 */
	Set<String> getNeededPackages();

	/**
	 * Adds a package to the set of shared packages accessible to JBoss Modules.
	 * 
	 * @param pkg the fully qualified package name to add
	 */
	void addNeededPackage(String pkg);

	/**
	 * Returns a list of file suffixes that indicate a mod should be skipped.
	 * Examples: {@code ".nil", ".disabled", ".deactivation"}. This allows launchers
	 * or environments to disable mods without deletion.
	 * 
	 * @return a list of suffix strings to avoid during mod loading
	 */
	List<String> getAvoidedModSuffixes();

	/**
	 * Returns the execution side (e.g., client or server).
	 * 
	 * @return the current execution side enum value
	 */
	ExecutionSide getExecutionSide();

	/**
	 * Returns the default module finders used to discover mods. This should not
	 * include finders for FCFileSystemClassPathFinder or mod-provided finders, as
	 * those are managed by FCLoaderBasic.
	 * 
	 * @return a list of module finders for core mod discovery
	 */
	List<ModuleFinder> getDefaultModuleFinders();

	/**
	 * Checks whether the given JAR file is a special "super loader" mod that should
	 * be skipped.
	 * 
	 * @param zip the JAR file to check
	 * @return {@code true} if the JAR is a super loader mod, {@code false}
	 *         otherwise
	 */
	boolean isSuperLoaderModZip(File zip);

	/**
	 * Checks whether the given folder is a special "super loader" mod that should
	 * be skipped.
	 * 
	 * @param folder the directory to check
	 * @return {@code true} if the folder is a super loader mod, {@code false}
	 *         otherwise
	 */
	boolean isSuperLoaderModFolder(File folder);
}