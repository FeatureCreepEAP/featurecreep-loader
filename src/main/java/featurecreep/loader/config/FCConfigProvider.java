package featurecreep.loader.config;

/**
 * Describes a module's configuration entry.
 *
 * <p>Modules can expose one either by registering it directly through
 * {@link FCConfigApi#register(FCConfigEntry)} or by declaring an implementation of
 * this interface as a {@code java.util.ServiceLoader} service
 * ({@code META-INF/services/featurecreep.loader.config.FCConfigProvider}). Service
 * providers are discovered by {@link FCConfigApi#discoverProviders} once the module
 * has been loaded, and must have a public no-argument constructor.
 */
@FunctionalInterface
public interface FCConfigProvider {

    /**
     * @return the entry to register, or {@code null} to contribute nothing
     * @throws Exception any failure while describing the entry; the registry isolates it
     */
    FCConfigEntry createConfigEntry() throws Exception;
}
