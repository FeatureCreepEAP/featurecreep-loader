package featurecreep.loader;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.modules.ModuleFinder;

/**
 * Supplies game/application-specific information to FeatureCreep's JBoss
 * Modules loader.  The default methods keep older providers source compatible
 * while giving the loader enough information to bridge into modern game class
 * loaders (Fabric/Knot, Forge/ModLauncher, NeoForge, vanilla, etc.).
 */
public interface GameProvider {

    boolean getDebugMode();

    boolean setDebugMode(boolean val);

    /** Folders containing normal runnable FeatureCreep modules. */
    Path[] getModulePKZipLocations();

    /** Folders containing modules that should be loaded but not run. */
    Path[] getClassPathPKZipLocations();

    Instrumentation getInstrumentation();

    Instrumentation setInstrumentation(Instrumentation instrument);

    /**
     * Package paths which must always resolve from the host/game class loader.
     * Paths use '/' separators as expected by JBoss Modules.
     */
    Set<String> getNeededPackages();

    void addNeededPackage(String pkg);

    List<String> getAvoidedModSuffixes();

    ExecutionSide getExecutionSide();

    List<ModuleFinder> getDefaultModuleFinders();

    boolean isSuperLoaderModZip(File zip);

    boolean isSuperLoaderModFolder(File folder);

    /**
     * Returns the class loader that owns the running game classes.  Providers
     * should override this when the game uses a launcher-specific loader.
     */
    default ClassLoader getGameClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : getClass().getClassLoader();
    }

    /**
     * Host class loaders that FeatureCreep modules may fall back to.  Ordering is
     * significant: the game loader is tried first.
     */
    default List<ClassLoader> getHostClassLoaders() {
        LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<ClassLoader>();
        ClassLoader game = getGameClassLoader();
        if (game != null) loaders.add(game);
        ClassLoader providerLoader = getClass().getClassLoader();
        if (providerLoader != null) loaders.add(providerLoader);
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) loaders.add(context);
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if (system != null) loaders.add(system);
        return new ArrayList<ClassLoader>(loaders);
    }

    /**
     * Path from which the game was launched.  For launchers that can identify a
     * concrete game JAR/classes directory, override this and return that path.
     */
    default Path getGameLaunchPath() {
        return getGameWorkingDirectory();
    }

    /** Current game working directory. */
    default Path getGameWorkingDirectory() {
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    /** Java executable used for the current process, when available. */
    default Path getJavaLaunchPath() {
        String command = ProcessHandle.current().info().command().orElse(null);
        return command == null ? null : Paths.get(command).toAbsolutePath().normalize();
    }

    /** Command-line arguments visible to the current Java process. */
    default String[] getLaunchArguments() {
        return ProcessHandle.current().info().arguments().orElse(new String[0]);
    }

    /**
     * FeatureCreep's compatibility-first default: normal top-level mods are
     * allowed to see other normal top-level mods automatically.
     */
    default boolean exposeModsToEachOtherByDefault() {
        return true;
    }
}
