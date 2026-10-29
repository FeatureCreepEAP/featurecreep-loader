package featurecreep.loader.query;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jboss.modules.Module;

import featurecreep.loader.ExecutionSide;
import featurecreep.loader.FCLoaderBasic;
import featurecreep.loader.FCModuleDescriptor;
import featurecreep.loader.FCModuleProperties;
import featurecreep.loader.config.FCConfigRegistry;
import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;

/**
 * Read-only view of a running loader for mods: which modules are loaded, where the
 * game and config directories are, and whether this looks like a development setup.
 */
public final class ModQuery {

    /** Setting this system property to any value other than {@code false} marks the environment as development. */
    public static final String DEVELOPMENT_PROPERTY = "featurecreep.dev";

    private static final String DEFAULT_SIDE = "both";

    private final FCLoaderBasic loader;

    public ModQuery(FCLoaderBasic loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public boolean isModLoaded(String modId) {
        return modId != null && loader.getModule(modId) != null;
    }

    /** Description of a loaded module, or empty when no module with that identifier is loaded. */
    public Optional<ModContainer> getModContainer(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        Module module = loader.getModule(modId);
        if (module == null) {
            return Optional.empty();
        }
        ModuleLoadingMapEntry entry = loader.getModuleLoadingMap().get(modId);
        FCModuleDescriptor descriptor = entry == null ? null : entry.getDescriptor();

        String declaredName = descriptor == null ? null : descriptor.getDeclaredName();
        String name = declaredName == null || declaredName.isBlank() ? modId : declaredName;
        String version = descriptor == null ? null : descriptor.getVersion();
        String side = descriptor == null ? DEFAULT_SIDE : sideOf(descriptor);
        Path path = entry == null || entry.getSourceFile() == null ? null : entry.getSourceFile().toPath();

        List<String> dependencies = new ArrayList<>();
        if (descriptor != null) {
            for (FCModuleDescriptor.Dependency dependency : descriptor.getDependencies()) {
                dependencies.add(dependency.getName());
            }
        }
        return Optional.of(new ModContainer(modId, version, name, path, side, dependencies));
    }

    public Path getGameDir() {
        return loader.getGameProvider().getGameWorkingDirectory();
    }

    public Path getConfigDir() {
        return FCConfigRegistry.configDirectory().toAbsolutePath().normalize();
    }

    public ExecutionSide getEnvironmentType() {
        return loader.getExecutionSide();
    }

    /** True when the {@value #DEVELOPMENT_PROPERTY} property is set, or a loaded module comes from a directory. */
    public boolean isDevelopmentEnvironment() {
        String development = System.getProperty(DEVELOPMENT_PROPERTY);
        if (development != null && !development.trim().equalsIgnoreCase("false")) {
            return true;
        }
        for (Module module : loader.getModules()) {
            ModuleLoadingMapEntry entry = loader.getModuleLoadingMap().get(module.getName());
            if (entry != null && entry.getSourceFile() != null && entry.getSourceFile().isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private static String sideOf(FCModuleDescriptor descriptor) {
        String declared = descriptor.getProperty(FCModuleProperties.SIDE);
        if (declared == null || declared.isBlank()) {
            return DEFAULT_SIDE;
        }
        return declared.trim().toLowerCase(Locale.ROOT);
    }
}
