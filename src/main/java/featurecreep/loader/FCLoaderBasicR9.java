package featurecreep.loader;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.jboss.modules.ClassTransformer;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;

import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.filesystem.PhilKatzZip;
import featurecreep.loader.finder.FileSystemResourceLoader;
import featurecreep.loader.finder.ModuleLoadingMap;
import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;
import featurecreep.loader.finder.NeedsFCLoaderBasic;
import featurecreep.loader.finder.PathResourceLoader;

/**
 * FeatureCreep's JBoss Modules loader.
 *
 * R9 now uses a two-phase discovery model: all top-level mods (and their nested
 * private libraries) are registered before JBoss Modules resolves the first
 * module.  This allows normal FeatureCreep mods to see one another by default
 * without reflectively changing a Module after it has already been linked.
 */
public final class FCLoaderBasicR9 extends ModuleLoader implements FCLoaderBasic {

    public final ArrayList<ClassTransformer> transformers = new ArrayList<ClassTransformer>();
    public final ArrayList<Module> run_only_modules = new ArrayList<Module>();
    public final ArrayList<Module> modules = new ArrayList<Module>();
    public int threads;
    public final Map<File, ModuleSpec> custom_root_specs = new HashMap<File, ModuleSpec>();
    public final Instrumentation instrumentation;
    public ClassTransformer main_transformer;
    public final EventViewer eventvwr = new EventViewer();
    public final ArrayList<String> known_nulls = new ArrayList<String>();
    public boolean mods_loaded = false;
    public final ModuleLoadingMap module_loading_map = new ModuleLoadingMap();
    public final ModuleFinder[] finders;
    public GameProvider provider;

    private boolean hotswapNeeded = false;

    public FCLoaderBasicR9(GameProvider provider, int threads) {
        this(FCLoaderBasic.getFinders(provider), provider, threads);
    }

    private FCLoaderBasicR9(ModuleFinder[] combinedModuleFinders, GameProvider provider, int threads) {
        super(combinedModuleFinders);
        this.finders = combinedModuleFinders;
        this.provider = provider;
        this.threads = threads;
        this.instrumentation = new FCInstrumentation(this);
        this.main_transformer = new FCTransformer(this);

        for (ModuleFinder finder : finders) {
            if (finder instanceof NeedsFCLoaderBasic) {
                ((NeedsFCLoaderBasic) finder).setFCLoaderBasic(this);
            }
        }

        if (!getDebugMode()) {
            System.out.println("Debug mode is off");
        }
    }

    @Override
    public Set<String> getNeededPackages() {
        Set<String> paths = new LinkedHashSet<String>();
        // JDK/platform classes should never be shadowed by a mod.
        paths.add("java");
        paths.add("javax");
        paths.add("jdk");
        paths.add("sun");
        paths.add("com/sun");
        paths.add("org/jboss/modules");
        // Keep the loader/API identity shared without reserving the entire
        // featurecreep.* namespace; existing mods legitimately use packages such
        // as featurecreep.content.*.
        paths.add("featurecreep/loader");
        paths.add("featurecreep/api");
        if (provider != null && provider.getNeededPackages() != null) {
            paths.addAll(provider.getNeededPackages());
        }
        // Deliberately do NOT copy every package visible to the game class loader
        // into this host-first set. Doing that would force shared copies of common
        // libraries (Gson, Guava, etc.) and defeat private per-mod dependencies.
        // Packages not listed here remain available through the late host fallback.
        return paths;
    }

    @Override
    public ArrayList<Module> getModules() {
        return modules;
    }

    @Override
    public void loadMods() {
        if (mods_loaded) {
            return;
        }

        System.out.println("Discovering FeatureCreep classpath modules");
        discoverFiles(getClassPathFiles(), false);

        System.out.println("Discovering FeatureCreep runnable modules");
        discoverFiles(getRunOnlyFiles(), true);

        // All public modules are now known.  Only now allow JBoss to resolve them.
        for (ModuleLoadingMapEntry entry : module_loading_map.snapshot()) {
            if (!entry.isNested()) {
                loadModule(entry.getName(), entry.isRunnable());
            }
        }

        loadDeclaredTransformers();
        mods_loaded = true;
    }

    private void discoverFiles(Iterable<File> files, boolean defaultRunnable) {
        for (File file : files) {
            if (file == null || shouldIgnore(file)) {
                continue;
            }
            try {
                registerModuleFromFile(file, defaultRunnable);
            } catch (Throwable t) {
                if (getDebugMode()) {
                    System.err.println("FeatureCreep could not discover " + file);
                    t.printStackTrace();
                } else {
                    System.err.println("FeatureCreep could not discover " + file + ": " + t.getMessage());
                }
            }
        }
    }

    private boolean shouldIgnore(File file) {
        String path = file.toString();
        if (known_nils().contains(path)) {
            return true;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".nil.jar") || lower.endsWith(".nil") || lower.endsWith(".deactivation")
                || lower.endsWith(".disabled") || lower.endsWith(".rpm")) {
            return true;
        }
        if (provider != null && provider.getAvoidedModSuffixes() != null) {
            for (String suffix : provider.getAvoidedModSuffixes()) {
                if (suffix != null && !suffix.isEmpty() && lower.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * SuperInjection deliberately adds Fabric/Forge/NeoForge/Sponge discovery
     * metadata to a FeatureCreep-owned JAR. The marker prevents that compatibility
     * metadata from making GameProvider classify the JAR out of FeatureCreep.
     */
    private boolean hasFeatureCreepOwnershipMarker(File file) {
        if (file == null || !file.isFile() || !FCLoaderBasic.doesFileEndInPKZipExtension(file.getName())) {
            return false;
        }
        try (JarFile jar = new JarFile(file)) {
            boolean marked = jar.getJarEntry(FCModuleProperties.SUPERINJECTION_OWNERSHIP_MARKER) != null;
            if (marked && getDebugMode()) {
                System.out.println(file.getName() + " contains the FeatureCreep ownership marker; "
                        + "superloader metadata will be treated as compatibility-only");
            }
            return marked;
        } catch (IOException e) {
            if (getDebugMode()) {
                System.err.println("Could not inspect FeatureCreep ownership marker in " + file + ": " + e.getMessage());
            }
            return false;
        }
    }

    private ModuleLoadingMapEntry registerModuleFromFile(File file, boolean defaultRunnable) throws IOException {
        ModuleLoadingMapEntry already = module_loading_map.getByFile(file);
        if (already != null) {
            // Runnable wins if a source was discovered in both classpath and mod folders.
            if (defaultRunnable) {
                already.setRunnable(FCModuleProperties.isRunnable(already.getDescriptor().getProperties(), true));
            }
            return already;
        }

        if (!file.exists()) {
            known_nils().add(file.toString());
            return null;
        }

        if (file.isFile() && provider != null && provider.isSuperLoaderModZip(file)
                && !hasFeatureCreepOwnershipMarker(file)) {
            if (getDebugMode()) {
                System.out.println(file.getName() + " belongs to the game's primary mod loader; skipping JBoss load");
            }
            return null;
        }
        if (file.isDirectory() && provider != null && provider.isSuperLoaderModFolder(file)) {
            return null;
        }

        final ResourceLoader resourceLoader;
        if (file.isDirectory()) {
            resourceLoader = new PathResourceLoader(file.getCanonicalPath(), file.toPath());
        } else if (FCLoaderBasic.doesFileEndInPKZipExtension(file.getName())) {
            resourceLoader = new FileSystemResourceLoader(new PhilKatzZip(file.getCanonicalPath()));
        } else {
            return null;
        }

        FCModuleDescriptor descriptor = FCModuleDescriptor.read(resourceLoader);
        if (!FCModuleProperties.supportsSide(descriptor.getProperties(), getExecutionSide())) {
            if (getDebugMode()) {
                System.out.println("Skipping " + file + " because FeatureCreep-Side does not match " + getExecutionSide());
            }
            closeQuietly(resourceLoader);
            return null;
        }

        String moduleName = chooseModuleName(descriptor, file.getName());
        moduleName = uniquifyModuleName(moduleName, file);
        boolean publicVisible = provider == null || provider.exposeModsToEachOtherByDefault();
        publicVisible = publicVisible && FCModuleProperties.isPublic(descriptor.getProperties(), false);
        boolean runnable = FCModuleProperties.isRunnable(descriptor.getProperties(), defaultRunnable);

        ModuleLoadingMapEntry entry = new ModuleLoadingMapEntry(moduleName, file.toURI().toString(), resourceLoader,
                descriptor, file, runnable, publicVisible, false, null);
        module_loading_map.put(moduleName, entry);
        checkHotswapProperties(descriptor.getProperties(), file.getName());

        if (resourceLoader instanceof IterableResourceLoader) {
            discoverNestedLibraries(entry, (IterableResourceLoader) resourceLoader, 0);
        }
        return entry;
    }

    private void discoverNestedLibraries(ModuleLoadingMapEntry owner, IterableResourceLoader resourceLoader, int depth) {
        if (owner == null || owner.getDescriptor() == null || depth > 8) {
            return;
        }
        String mode = FCModuleProperties.nestedJarMode(owner.getDescriptor().getProperties());
        if ("disabled".equals(mode) || "none".equals(mode) || "false".equals(mode)) {
            return;
        }

        Set<String> explicit = new HashSet<String>(FCModuleProperties.splitList(
                FCModuleProperties.get(owner.getDescriptor().getProperties(), FCModuleProperties.NESTED_JAR_PATHS)));
        java.util.Iterator<Resource> iterator = resourceLoader.iterateResources("", true);
        while (iterator.hasNext()) {
            Resource resource = iterator.next();
            String name = resource.getName();
            if (!isNestedLibraryPath(name, explicit, mode)) {
                continue;
            }
            try {
                registerNestedLibrary(owner, resource, mode, depth + 1);
            } catch (Throwable t) {
                if (getDebugMode()) {
                    System.err.println("Unable to load nested dependency " + name + " from " + owner.getName());
                    t.printStackTrace();
                }
            }
        }
    }

    private boolean isNestedLibraryPath(String name, Set<String> explicit, String mode) {
        if (name == null || !FCLoaderBasic.doesFileEndInPKZipExtension(name)) {
            return false;
        }
        if (explicit.contains(name)) {
            return true;
        }
        // Compatibility with the old JIJ behavior: private/public scans all nested
        // JARs. Use FeatureCreep-Nested-Jars=declared to restrict scanning to the
        // FeatureCreep-Nested-Jar-Paths list.
        if ("declared".equals(mode) || "explicit".equals(mode)) {
            return false;
        }
        return true;
    }

    private void registerNestedLibrary(ModuleLoadingMapEntry owner, Resource resource, String ownerMode, int depth)
            throws IOException, java.net.URISyntaxException {
        URI uri = resource.getURL().toURI();
        FileSystemResourceLoader nestedLoader = new FileSystemResourceLoader(new PhilKatzZip(resource.openStream(), uri));
        FCModuleDescriptor descriptor = FCModuleDescriptor.read(nestedLoader);
        boolean requestedPublic = "public".equals(ownerMode)
                || FCModuleProperties.isPublic(descriptor.getProperties(), true);

        String rawName = chooseModuleName(descriptor, basename(resource.getName()));
        String moduleName;
        if (requestedPublic) {
            moduleName = uniquifyModuleName(rawName, null);
        } else {
            // Namespace private dependencies by owner so two mods can carry different
            // versions of the same library without colliding.
            moduleName = uniquifyModuleName(owner.getName() + ".private." + sanitizeModuleName(rawName), null);
        }

        ModuleLoadingMapEntry nested = new ModuleLoadingMapEntry(moduleName, resource.getURL().toString(), nestedLoader,
                descriptor, null, false, requestedPublic, true, owner.getName());
        module_loading_map.put(moduleName, nested);
        owner.addPrivateDependency(moduleName);
        checkHotswapProperties(descriptor.getProperties(), resource.getName());

        if (nestedLoader instanceof IterableResourceLoader) {
            discoverNestedLibraries(nested, nestedLoader, depth);
        }
    }

    private String chooseModuleName(FCModuleDescriptor descriptor, String fallbackName) {
        String id = FCModuleProperties.get(descriptor.getProperties(), FCModuleProperties.MOD_ID);
        if (id == null || id.trim().isEmpty()) {
            id = descriptor.getDeclaredName();
        }
        if (id == null || id.trim().isEmpty()) {
            id = stripExtension(fallbackName);
        }
        return sanitizeModuleName(id);
    }

    private String uniquifyModuleName(String requested, File sourceFile) {
        String base = requested == null || requested.isEmpty() ? "featurecreep.mod" : requested;
        if (!module_loading_map.containsKey(base)) {
            return base;
        }
        ModuleLoadingMapEntry existing = module_loading_map.get(base);
        if (sourceFile != null && existing != null && existing.getSourceFile() != null) {
            try {
                if (sourceFile.getCanonicalFile().equals(existing.getSourceFile().getCanonicalFile())) {
                    return base;
                }
            } catch (IOException ignored) {
            }
        }
        int suffix = 2;
        while (module_loading_map.containsKey(base + "." + suffix)) {
            suffix++;
        }
        String unique = base + "." + suffix;
        System.err.println("FeatureCreep module id '" + base + "' is duplicated; using '" + unique + "'");
        return unique;
    }

    private static String sanitizeModuleName(String name) {
        String value = name == null ? "featurecreep.mod" : name.trim();
        value = value.replaceAll("[^A-Za-z0-9_.-]+", ".");
        value = value.replaceAll("\\.{2,}", ".");
        value = value.replaceAll("^[.-]+|[.-]+$", "");
        return value.isEmpty() ? "featurecreep.mod" : value;
    }

    private static String stripExtension(String name) {
        String base = basename(name);
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static String basename(String path) {
        if (path == null) return "module";
        String n = path.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        return slash >= 0 ? n.substring(slash + 1) : n;
    }

    private static void closeQuietly(ResourceLoader loader) {
        try {
            loader.close();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public ModuleLoader getLoader() {
        return this;
    }

    @Override
    public void runMods() {
        for (Module mod : new ArrayList<Module>(getRunModules())) {
            String main = mod.getProperty(FCModuleProperties.MAIN_CLASS);
            if (main != null && !main.isEmpty()) {
                try {
                    mod.run(new String[0]);
                } catch (NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            } else if (getDebugMode()) {
                System.out.println(mod.getName() + " has no main class; loaded as a library/module only");
            }
        }
    }

    @Override
    public void runModule(String name) {
        try {
            Module mod = getModule(name);
            if (mod != null) {
                mod.run(new String[0]);
            }
        } catch (NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
            if (getDebugMode()) e.printStackTrace();
        }
    }

    @Override public ArrayList<ClassTransformer> getTransformers() { return transformers; }
    @Override public ArrayList<Module> getRunModules() { return run_only_modules; }

    @Override
    public Module loadModule(String name, boolean runnable) {
        Module mod = null;
        try {
            if (module_loading_map.containsKey(name)) {
                mod = super.loadModule(name);
            } else if (getDebugMode()) {
                System.out.println("Module is not in discovery map: " + name);
            }
        } catch (ModuleLoadException e) {
            System.err.println("FeatureCreep failed to load module " + name + ": " + e.getMessage());
            if (getDebugMode()) e.printStackTrace();
        }

        if (mod != null) {
            if (!modules.contains(mod)) modules.add(mod);
            if (runnable && !run_only_modules.contains(mod)) run_only_modules.add(mod);
        } else if (!known_nils().contains(name)) {
            known_nils().add(name);
        }
        return mod;
    }

    @Override public Map<File, ModuleSpec> getCustomRootSpecs() { return custom_root_specs; }
    @Override public Instrumentation getInstrumentationForAgent() { return instrumentation; }
    @Override public ClassTransformer getMainTransformer() { return main_transformer; }
    @Override public EventViewer getEventViewer() { return eventvwr; }
    @Override public ArrayList<String> known_nils() { return known_nulls; }
    @Override public boolean getModsLoaded() { return mods_loaded; }
    @Override public ExecutionSide getExecutionSide() { return provider.getExecutionSide(); }
    @Override public ClassTransformer setMainTransformer(ClassTransformer transformer) { main_transformer = transformer; return transformer; }
    @Override public ModuleLoadingMap getModuleLoadingMap() { return module_loading_map; }

    @Override
    public Module loadModuleFromFile(File file, boolean runnable) {
        try {
            ModuleLoadingMapEntry entry = registerModuleFromFile(file, runnable);
            return entry == null ? null : loadModule(entry.getName(), entry.isRunnable());
        } catch (IOException e) {
            if (getDebugMode()) e.printStackTrace();
            return null;
        }
    }

    @Override
    public Module loadModuleFromResource(Resource resource) {
        if (resource == null) return null;
        try {
            FileSystemResourceLoader loader = new FileSystemResourceLoader(
                    new PhilKatzZip(resource.openStream(), resource.getURL().toURI()));
            FCModuleDescriptor descriptor = FCModuleDescriptor.read(loader);
            String name = uniquifyModuleName(chooseModuleName(descriptor, basename(resource.getName())), null);
            boolean publicVisible = FCModuleProperties.isPublic(descriptor.getProperties(), true);
            ModuleLoadingMapEntry entry = new ModuleLoadingMapEntry(name, resource.getURL().toString(), loader,
                    descriptor, null, false, publicVisible, true, null);
            module_loading_map.put(name, entry);
            return loadModule(name, false);
        } catch (Exception e) {
            if (getDebugMode()) e.printStackTrace();
            return null;
        }
    }

    private void loadDeclaredTransformers() {
        for (Module mod : new ArrayList<Module>(modules)) {
            for (String className : FCModuleProperties.getTransformerClasses(mod)) {
                try {
                    Class<?> clazz = Class.forName(className, true, mod.getClassLoader());
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    if (instance instanceof ClassTransformer) {
                        addTransformer((ClassTransformer) instance);
                    } else if (instance instanceof ClassFileTransformer) {
                        addTransformer((ClassFileTransformer) instance);
                    } else {
                        System.err.println("FeatureCreep transformer " + className
                                + " implements neither JBoss ClassTransformer nor ClassFileTransformer");
                    }
                } catch (Throwable t) {
                    System.err.println("Unable to initialize FeatureCreep transformer " + className
                            + " from " + mod.getName());
                    if (getDebugMode()) t.printStackTrace();
                }
            }
        }
    }

    private void checkHotswapProperties(Map<String, String> properties, String source) {
        boolean redefine = FCModuleProperties.getBoolean(properties, FCModuleProperties.CAN_REDEFINE_CLASSES, false);
        boolean retransform = FCModuleProperties.getBoolean(properties, FCModuleProperties.CAN_RETRANSFORM_CLASSES, false);
        String scope = FCModuleProperties.get(properties, FCModuleProperties.TRANSFORMER_SCOPE);
        boolean gameTransformer = !FCModuleProperties.getTransformerClasses(properties).isEmpty()
                && (scope == null || !"module".equalsIgnoreCase(scope.trim()));
        boolean agentEntryPoint = FCModuleProperties.get(properties, FCModuleProperties.PREMAIN_CLASS) != null
                || FCModuleProperties.get(properties, FCModuleProperties.AGENT_CLASS) != null;
        if (redefine || retransform || gameTransformer || agentEntryPoint) {
            if (getDebugMode()) {
                System.out.println("FeatureCreep module requests class redefinition/retransformation: " + source);
            }
            hotswapNeeded = true;
        }
    }

    @Override public boolean isHotswapNeeded() { return hotswapNeeded; }
    @Override public GameProvider getGameProvider() { return provider; }

    @Override
    public Instrumentation getInstrumentation() {
        Instrumentation supplied = provider == null ? null : provider.getInstrumentation();
        return supplied != null ? supplied : instrumentation;
    }
}
