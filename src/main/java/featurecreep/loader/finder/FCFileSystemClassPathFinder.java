package featurecreep.loader.finder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.DelegatingModuleLoader;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.FileSystemClassPathModuleFinder;
import org.jboss.modules.LocalModuleFinder;
import org.jboss.modules.ModuleDependencySpecBuilder;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderModuleFinder;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.Version;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.modules.xml.PermissionsXmlParser;
import org.jboss.modules.xml.XmlPullParserException;

import featurecreep.loader.FCModuleDescriptor;
import featurecreep.loader.FCModuleProperties;
import featurecreep.loader.FCLoaderBasic;
import featurecreep.loader.GameClassLoaderLocalLoader;
import featurecreep.loader.finder.ModuleLoadingMap.ModuleLoadingMapEntry;

/**
 * Builds FeatureCreep ModuleSpecs exclusively through the public JBoss Modules
 * API.  No reflective access to ConcreteModuleSpec, Module.dependencies, or
 * ModuleLoader.finders is needed.
 */
public class FCFileSystemClassPathFinder extends FileSystemClassPathModuleFinder implements NeedsFCLoaderBasic {

    private FCLoaderBasic load;
    private final ModuleLoader baseModuleLoader;
    private final ModuleLoader extensionModuleLoader;

    public static final PathFilter NO_MODULES_DIR;

    static {
        MultiplePathFilterBuilder builder = PathFilters.multiplePathFilterBuilder(true);
        builder.addFilter(PathFilters.is("modules"), false);
        builder.addFilter(PathFilters.isChildOf("modules"), false);
        NO_MODULES_DIR = builder.create();
    }

    public FCFileSystemClassPathFinder(ModuleLoader baseModuleLoader, FCLoaderBasic load) {
        this(baseModuleLoader, new ModuleLoader(ModuleLoader.NO_FINDERS), load);
    }

    public FCFileSystemClassPathFinder(ModuleLoader baseModuleLoader, ModuleLoader extensionModuleLoader,
            FCLoaderBasic load) {
        super(baseModuleLoader);
        this.load = load;
        this.baseModuleLoader = baseModuleLoader;
        this.extensionModuleLoader = extensionModuleLoader;
    }

    public FCFileSystemClassPathFinder(ModuleLoader baseModuleLoader) {
        this(baseModuleLoader, new ModuleLoader(ModuleLoader.NO_FINDERS), null);
    }

    public FCFileSystemClassPathFinder(ModuleLoader baseModuleLoader, ModuleLoader extensionModuleLoader) {
        this(baseModuleLoader, extensionModuleLoader, null);
    }

    @Override
    public ModuleSpec findModule(String name, ModuleLoader delegateLoader) throws ModuleLoadException {
        if (load == null) {
            return null;
        }
        ModuleLoadingMapEntry entry = load.getModuleLoadingMap().get(name);
        if (entry == null || entry.getLoader() == null) {
            return null;
        }

        ResourceLoader resourceLoader = entry.getLoader();
        FCModuleDescriptor descriptor = entry.getDescriptor();
        if (descriptor == null) {
            try {
                descriptor = FCModuleDescriptor.read(resourceLoader);
                entry.setDescriptor(descriptor);
            } catch (IOException e) {
                throw new ModuleLoadException("Could not read module metadata for " + name, e);
            }
        }

        ModuleSpec.Builder builder = ModuleSpec.build(name);
        Set<String> dependencyNames = new LinkedHashSet<String>();
        try {
            builder.setClassFileTransformer(load.getMainTransformer());
            addProperties(builder, entry, descriptor);
            setMainClass(builder, descriptor);
            setVersion(builder, descriptor);
            addSelfContent(builder, resourceLoader, descriptor);
            builder.addDependency(DependencySpec.OWN_DEPENDENCY);
            addExtraResourceRoots(builder, resourceLoader, descriptor);

            ModuleLoader bundledModuleLoader = bundledModuleLoader(entry, resourceLoader);
            addDescriptorDependencies(builder, descriptor, bundledModuleLoader, dependencyNames);
            addLegacyManifestDependencies(builder, descriptor, bundledModuleLoader, dependencyNames);
            addPrivateDependencies(builder, entry, dependencyNames);
            addAutomaticPublicModDependencies(builder, entry, descriptor, dependencyNames);
            addExtensionDependencies(builder, descriptor, dependencyNames);
            addProvidedServices(builder, descriptor);
            addPermissions(builder, resourceLoader, delegateLoader);

            if (FCModuleProperties.hostFallback(descriptor.getProperties())) {
                builder.setFallbackLoader(new GameClassLoaderLocalLoader(load.getGameProvider().getHostClassLoaders()));
            }
            return builder.create();
        } catch (Throwable t) {
            if (t instanceof ModuleLoadException) {
                throw (ModuleLoadException) t;
            }
            throw new ModuleLoadException("Unable to create module specification for " + name, t);
        }
    }

    private void addProperties(ModuleSpec.Builder builder, ModuleLoadingMapEntry entry, FCModuleDescriptor descriptor) {
        for (Map.Entry<String, String> property : descriptor.getProperties().entrySet()) {
            if (property.getKey() != null && property.getValue() != null) {
                builder.addProperty(property.getKey(), property.getValue());
            }
        }
        builder.addProperty(FCModuleProperties.MOD_ID, entry.getName());
        if (entry.getSource() != null) {
            builder.addProperty(FCModuleProperties.SOURCE, entry.getSource());
        }
        if (entry.getOwnerModule() != null) {
            builder.addProperty(FCModuleProperties.NESTED_OWNER, entry.getOwnerModule());
        }
        if (descriptor.getMainClass() != null) {
            builder.addProperty(FCModuleProperties.MAIN_CLASS, descriptor.getMainClass());
        }
    }

    private void setMainClass(ModuleSpec.Builder builder, FCModuleDescriptor descriptor) {
        String mainClass = descriptor.getMainClass();
        if (mainClass != null && !mainClass.trim().isEmpty()) {
            builder.setMainClass(mainClass.trim());
        }
    }

    private void setVersion(ModuleSpec.Builder builder, FCModuleDescriptor descriptor) {
        String version = descriptor.getVersion();
        if (version != null && !version.trim().isEmpty()) {
            try {
                builder.setVersion(Version.parse(version.trim()));
            } catch (IllegalArgumentException ignored) {
                if (load.getDebugMode()) {
                    System.out.println("Ignoring invalid JBoss module version '" + version + "'");
                }
            }
        }
    }

    /**
     * Add the module's own content while reserving host-owned namespaces for the
     * real game/launcher class loader. JBoss LocalDependency loader paths are
     * exact package paths (not recursive prefixes), so trying to represent
     * "net/minecraft/**" with a single loader path is incorrect. Excluding those
     * namespaces from every FeatureCreep resource root and using the module's
     * fallback loader gives the desired result without enumerating every game
     * package or forcing ordinary libraries such as Gson/Guava to be shared.
     */
    private void addSelfContent(ModuleSpec.Builder builder, ResourceLoader resourceLoader,
            FCModuleDescriptor descriptor) {
        builder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader,
                moduleContentFilter(descriptor)));
    }

    private PathFilter moduleContentFilter(FCModuleDescriptor descriptor) {
        MultiplePathFilterBuilder builder = PathFilters.multiplePathFilterBuilder(true);
        builder.addFilter(PathFilters.is("modules"), false);
        builder.addFilter(PathFilters.isChildOf("modules"), false);

        if (FCModuleProperties.hostFallback(descriptor.getProperties())) {
            Set<String> hostPaths = load.getNeededPackages();
            if (hostPaths != null) {
                for (String rawPath : hostPaths) {
                    if (rawPath == null) continue;
                    String path = rawPath.replace('.', '/').replaceAll("^/+|/+$", "");
                    if (path.isEmpty()) continue;
                    builder.addFilter(PathFilters.is(path), false);
                    builder.addFilter(PathFilters.isChildOf(path), false);
                }
            }
        }
        return builder.create();
    }

    private void addExtraResourceRoots(ModuleSpec.Builder builder, ResourceLoader resourceLoader,
            FCModuleDescriptor descriptor) throws IOException {
        for (String root : descriptor.getResourceRoots()) {
            ResourceLoader subloader = resourceLoader.createSubloader(root, root);
            if (subloader != null) {
                builder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(subloader, moduleContentFilter(descriptor)));
            } else if (load.getDebugMode()) {
                System.out.println("Resource root '" + root + "' is not supported by " + resourceLoader);
            }
        }
    }

    private ModuleLoader bundledModuleLoader(ModuleLoadingMapEntry entry, ResourceLoader resourceLoader) {
        File file = entry.getSourceFile();
        if (file != null && file.isDirectory()) {
            File modules = new File(file, "modules");
            if (modules.isDirectory()) {
                return new DelegatingModuleLoader(baseModuleLoader, new LocalModuleFinder(new File[] { modules }));
            }
        }
        return new DelegatingModuleLoader(baseModuleLoader, new ResourceLoaderModuleFinder(resourceLoader));
    }

    private void addDescriptorDependencies(ModuleSpec.Builder builder, FCModuleDescriptor descriptor,
            ModuleLoader bundledModuleLoader, Set<String> dependencyNames) {
        for (FCModuleDescriptor.Dependency dependency : descriptor.getDependencies()) {
            addNamedDependency(builder, dependency.getName(), dependency.isOptional(), dependency.isExport(),
                    dependency.isServices(), bundledModuleLoader, dependencyNames);
        }
    }

    /** Backwards compatibility with the JBoss-style MANIFEST.MF Dependencies key. */
    private void addLegacyManifestDependencies(ModuleSpec.Builder builder, FCModuleDescriptor descriptor,
            ModuleLoader bundledModuleLoader, Set<String> dependencyNames) {
        String dependencies = FCModuleProperties.get(descriptor.getProperties(), "Dependencies");
        if (dependencies == null || dependencies.trim().isEmpty()) {
            return;
        }
        for (String dependencyEntry : dependencies.split("\\s*,\\s*")) {
            boolean optional = false;
            boolean export = false;
            boolean services = false;
            String[] fields = dependencyEntry.trim().split("\\s+");
            if (fields.length == 0 || fields[0].isEmpty()) continue;
            for (int i = 1; i < fields.length; i++) {
                if ("optional".equals(fields[i])) optional = true;
                else if ("export".equals(fields[i])) export = true;
                else if ("services".equals(fields[i])) services = true;
            }
            addNamedDependency(builder, fields[0], optional, export, services, bundledModuleLoader, dependencyNames);
        }
    }

    private void addNamedDependency(ModuleSpec.Builder builder, String moduleName, boolean optional, boolean export,
            boolean services, ModuleLoader bundledModuleLoader, Set<String> dependencyNames) {
        if (moduleName == null || moduleName.isEmpty() || !dependencyNames.add(moduleName)) {
            return;
        }
        ModuleLoader dependencyLoader = load.getModuleLoadingMap().containsKey(moduleName)
                ? load.getLoader() : bundledModuleLoader;
        builder.addDependency(new ModuleDependencySpecBuilder()
                .setModuleLoader(dependencyLoader)
                .setName(moduleName)
                .setOptional(optional)
                .setExport(export)
                .setImportServices(services)
                .build());
    }

    private void addPrivateDependencies(ModuleSpec.Builder builder, ModuleLoadingMapEntry entry,
            Set<String> dependencyNames) {
        for (String privateModule : entry.getPrivateDependencies()) {
            if (dependencyNames.add(privateModule)) {
                builder.addDependency(new ModuleDependencySpecBuilder()
                        .setModuleLoader(load.getLoader())
                        .setName(privateModule)
                        .setExport(false)
                        .build());
            }
        }
    }

    private void addAutomaticPublicModDependencies(ModuleSpec.Builder builder, ModuleLoadingMapEntry entry,
            FCModuleDescriptor descriptor, Set<String> dependencyNames) {
        if (!entry.isPublicVisible()
                || !FCModuleProperties.autoDependencies(descriptor.getProperties(), entry.isPublicVisible())) {
            return;
        }
        for (ModuleLoadingMapEntry other : load.getModuleLoadingMap().snapshot()) {
            if (other == entry || !other.isPublicVisible()) {
                continue;
            }
            String otherName = other.getName();
            if (dependencyNames.add(otherName)) {
                builder.addDependency(new ModuleDependencySpecBuilder()
                        .setModuleLoader(load.getLoader())
                        .setName(otherName)
                        .setOptional(true)
                        .setExport(false)
                        .build());
            }
        }
    }

    private void addExtensionDependencies(ModuleSpec.Builder builder, FCModuleDescriptor descriptor,
            Set<String> dependencyNames) {
        String extensionList = FCModuleProperties.get(descriptor.getProperties(), "Extension-List");
        if (extensionList == null) return;
        for (String entry : extensionList.split("\\s+")) {
            if (!entry.isEmpty() && dependencyNames.add(entry)) {
                builder.addDependency(new ModuleDependencySpecBuilder()
                        .setModuleLoader(extensionModuleLoader)
                        .setName(entry)
                        .setOptional(true)
                        .build());
            }
        }
    }

    private void addProvidedServices(ModuleSpec.Builder builder, FCModuleDescriptor descriptor) {
        for (Map.Entry<String, List<String>> service : descriptor.getProvidedServices().entrySet()) {
            for (String implementation : service.getValue()) {
                builder.addProvide(service.getKey(), implementation);
            }
        }
    }

    private void addPermissions(ModuleSpec.Builder builder, ResourceLoader resourceLoader, ModuleLoader moduleLoader) {
        Resource resource = resourceLoader.getResource("META-INF/permissions.xml");
        if (resource == null) return;
        try (InputStream stream = resource.openStream()) {
            builder.setPermissionCollection(
                    PermissionsXmlParser.parsePermissionsXml(stream, moduleLoader, builder.getName()));
        } catch (XmlPullParserException | IOException ignored) {
            if (load.getDebugMode()) {
                System.out.println("Unable to parse META-INF/permissions.xml for " + builder.getName());
            }
        }
    }

    @Override
    public void setFCLoaderBasic(FCLoaderBasic fcloader) {
        this.load = fcloader;
    }
}
