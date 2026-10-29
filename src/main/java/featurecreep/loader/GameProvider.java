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

import featurecreep.loader.config.FCConfigApi;
import featurecreep.loader.mixin.MixinSupport;

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

    /**
     * Stable identifier of the game this provider runs, for example
     * {@code minecraft}. Matched against {@code FeatureCreep-Target-Game} module
     * metadata. {@code null} means unknown, which skips that check.
     */
    default String getGameId() {
        return null;
    }

    /**
     * Version of the running game, for example {@code 1.21.1}. Matched against
     * {@code FeatureCreep-Target-Game-Version} module ranges. {@code null} means
     * unknown; ranges are then not enforced.
     */
    default String getGameVersion() {
        return null;
    }

    /**
     * Version of the FeatureCreep loader itself. Matched against
     * {@code FeatureCreep-Loader-Version} module ranges. Defaults to the version in the
     * loader JAR's Maven metadata, or {@code null} when it is not recorded.
     */
    default String getLoaderVersion() {
        return FCLoaderVersion.get();
    }

    /**
     * Loader-neutral configuration registry. The default is the shared
     * file-backed registry under {@code ./etc/}, or {@code featurecreep.config.dir}.
     */
    default FCConfigApi getConfigApi() {
        return FCConfigApi.shared();
    }

    /**
     * Applies mixin configs declared by modules. Only hosts that run Sponge Mixin
     * override this; the loader itself never depends on mixins.
     */
    default MixinSupport getMixinSupport() {
        return MixinSupport.NONE;
    }

    /**
     * Whether MappingEx access widening may be applied to classes this host loads.
     * Hosts whose class loading FeatureCreep cannot route through its transformers
     * return false, and module MappingEx rules are then skipped.
     */
    default boolean canApplyAccessWidening() {
        return true;
    }
}
