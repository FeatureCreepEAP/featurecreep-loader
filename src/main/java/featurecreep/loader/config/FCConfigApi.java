package featurecreep.loader.config;

import java.util.List;
import java.util.Optional;

import org.jboss.modules.Module;


/**
 * Loader-neutral registry of per-module configuration entries.
 *
 * <p>This is the contract exposed through {@code GameProvider#getConfigApi()} and
 * {@code FCLoaderBasic#getConfigApi()}. It contains no Minecraft or toolkit types.
 * Use {@link #none()} for the no-op implementation returned by providers that
 * do not supply a registry.
 */
public interface FCConfigApi {

    /**
     * Registers an entry under its module identifier.
     *
     * @return {@code true} if the entry was added; {@code false} for a {@code null}
     *         entry, a duplicate module identifier, or an absent API
     */
    boolean register(FCConfigEntry entry);

    /**
     * Asks a provider for its entry and registers it. Exceptions thrown by the
     * provider are reported and isolated.
     *
     * @return {@code true} if an entry was registered
     */
    boolean registerProvider(FCConfigProvider provider);

    /** Entry for a module identifier, if one is registered. */
    Optional<FCConfigEntry> getEntry(String modId);

    /** Snapshot of all registered entries in registration order. */
    List<FCConfigEntry> getEntries();

    /**
     * Creates the configuration screen for a module. Returns empty when the module
     * has no entry, no screen factory, the factory returns {@code null}, or the
     * factory throws (the failure is reported and isolated).
     *
     * @param modId  module identifier
     * @param parent opaque host UI parent, passed through to the factory
     */
    Optional<Object> createScreen(String modId, Object parent);

    /**
     * Registers config providers declared as {@code ServiceLoader} services by the
     * given loaded modules. Each module is scanned at most once per API instance.
     *
     * @return the number of entries registered
     */
    default int discoverProviders(Iterable<? extends Module> modules) {
        return 0;
    }

    /**
     * Persisted values for a module, read from {@code <configDir>/<modId>.properties}
     * on first use. Every call with the same identifier returns the same instance.
     *
     * @throws IllegalArgumentException if {@code modId} is not a safe file-name segment
     */
    FCConfigValues getValues(String modId);

    /** No-op API for hosts that provide no configuration registry. */
    static FCConfigApi none() {
        return FCConfigRegistry.absent();
    }

    /** Process-wide registry, shared by every caller that does not supply its own. */
    static FCConfigApi shared() {
        return FCConfigRegistry.shared();
    }
}
