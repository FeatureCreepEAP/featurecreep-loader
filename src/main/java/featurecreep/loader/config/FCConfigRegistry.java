package featurecreep.loader.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;

import org.jboss.modules.Module;

/**
 * Thread-safe in-memory {@link FCConfigApi} that also persists module values under
 * {@link #configDirectory()}.
 *
 * <p>Failures raised by providers or screen factories are printed to stderr and
 * swallowed so that one bad third-party module cannot stop the game. Only
 * {@link VirtualMachineError}s are rethrown.
 */
public class FCConfigRegistry implements FCConfigApi {

    /** System property that overrides the directory holding module value files. */
    public static final String CONFIG_DIR_PROPERTY = "featurecreep.config.dir";

    private static final FCConfigRegistry ABSENT = new FCConfigRegistry(null, true);

    private final Path configDir;
    private final boolean absent;
    private final Map<String, FCConfigEntry> entries = new LinkedHashMap<String, FCConfigEntry>();
    private final Map<String, FCConfigValues> values = new HashMap<String, FCConfigValues>();
    private final Set<String> scannedModules = new HashSet<String>();
    private final Set<String> providerClassNames = new HashSet<String>();

    public FCConfigRegistry() {
        this(configDirectory());
    }

    public FCConfigRegistry(Path configDir) {
        this(Objects.requireNonNull(configDir, "configDir"), false);
    }

    private FCConfigRegistry(Path configDir, boolean absent) {
        this.configDir = configDir;
        this.absent = absent;
    }

    private static final class Shared {
        static final FCConfigRegistry INSTANCE = new FCConfigRegistry(configDirectory());
    }

    /** Process-wide registry, created on first use with {@link #configDirectory()}. */
    public static FCConfigRegistry shared() {
        return Shared.INSTANCE;
    }

    /** The {@value #CONFIG_DIR_PROPERTY} system property, or {@code ./etc} when unset. */
    public static Path configDirectory() {
        String configured = System.getProperty(CONFIG_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Paths.get("./etc");
        }
        return Paths.get(configured.trim());
    }

    /** Shared read-only instance used when a host provides no registry. */
    static FCConfigApi absent() {
        return ABSENT;
    }

    @Override
    public synchronized FCConfigValues getValues(String modId) {
        String id = FCConfigValues.checkModId(modId);
        return values.computeIfAbsent(id, key -> FCConfigValues.open(key, configDir));
    }

    @Override
    public synchronized boolean register(FCConfigEntry entry) {
        if (absent || entry == null) {
            return false;
        }
        if (entries.containsKey(entry.getModId())) {
            System.err.println("FeatureCreep config entry for '" + entry.getModId()
                    + "' is already registered; ignoring the duplicate");
            return false;
        }
        entries.put(entry.getModId(), entry);
        return true;
    }

    @Override
    public boolean registerProvider(FCConfigProvider provider) {
        if (absent || provider == null) {
            return false;
        }
        FCConfigEntry entry;
        try {
            entry = provider.createConfigEntry();
        } catch (Throwable t) {
            rethrowIfFatal(t);
            System.err.println("FeatureCreep config provider " + provider.getClass().getName() + " failed: " + t);
            return false;
        }
        return register(entry);
    }

    @Override
    public synchronized Optional<FCConfigEntry> getEntry(String modId) {
        return modId == null ? Optional.<FCConfigEntry>empty() : Optional.ofNullable(entries.get(modId.trim()));
    }

    @Override
    public synchronized List<FCConfigEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<FCConfigEntry>(entries.values()));
    }

    @Override
    public Optional<Object> createScreen(String modId, Object parent) {
        FCConfigEntry entry = getEntry(modId).orElse(null);
        if (entry == null || entry.getScreenFactory() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(entry.getScreenFactory().createScreen(parent));
        } catch (Throwable t) {
            rethrowIfFatal(t);
            System.err.println("FeatureCreep config screen for '" + modId + "' failed: " + t);
            return Optional.empty();
        }
    }

    @Override
    public synchronized int discoverProviders(Iterable<? extends Module> modules) {
        if (absent || modules == null) {
            return 0;
        }
        int registered = 0;
        for (Module module : modules) {
            if (module == null || !scannedModules.add(module.getName())) {
                continue;
            }
            registered += discoverFrom(module);
        }
        return registered;
    }

    private int discoverFrom(Module module) {
        int registered = 0;
        try {
            Iterator<FCConfigProvider> providers = module.loadService(FCConfigProvider.class).iterator();
            while (providers.hasNext()) {
                FCConfigProvider provider = providers.next();
                if (!providerClassNames.add(provider.getClass().getName())) {
                    continue;
                }
                if (registerProvider(provider)) {
                    registered++;
                }
            }
        } catch (ServiceConfigurationError e) {
            System.err.println("FeatureCreep could not load config providers from " + module.getName() + ": " + e);
        } catch (Throwable t) {
            rethrowIfFatal(t);
            System.err.println("FeatureCreep could not scan config providers in " + module.getName() + ": " + t);
        }
        return registered;
    }

    private static void rethrowIfFatal(Throwable t) {
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
    }
}
