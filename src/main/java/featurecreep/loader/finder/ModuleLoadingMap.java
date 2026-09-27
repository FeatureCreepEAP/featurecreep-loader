package featurecreep.loader.finder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ResourceLoader;

import featurecreep.loader.FCModuleDescriptor;

/**
 * Registry of module sources discovered before JBoss Modules starts resolving
 * them.  The two-phase discovery is what allows FeatureCreep to add default
 * mod-to-mod dependencies without mutating already-loaded modules.
 */
public class ModuleLoadingMap implements Map<String, ModuleLoadingMap.ModuleLoadingMapEntry> {

    public static class ModuleLoadingMapEntry {
        private String name;
        private String source;
        private ResourceLoader loader;
        private FCModuleDescriptor descriptor;
        private File sourceFile;
        private boolean runnable;
        private boolean publicVisible;
        private boolean nested;
        private String ownerModule;
        private final List<String> privateDependencies = new ArrayList<String>();

        /** Legacy constructor retained for source compatibility. */
        public ModuleLoadingMapEntry(String name, ResourceLoader loader, ModuleFinder finder) {
            this(name, name, loader, null, null, false, true, false, null);
        }

        /** Legacy constructor retained for source compatibility. */
        public ModuleLoadingMapEntry(String name, ResourceLoader loader) {
            this(name, name, loader, null, null, false, true, false, null);
        }

        public ModuleLoadingMapEntry(String name, String source, ResourceLoader loader,
                FCModuleDescriptor descriptor, File sourceFile, boolean runnable,
                boolean publicVisible, boolean nested, String ownerModule) {
            this.name = name;
            this.source = source;
            this.loader = loader;
            this.descriptor = descriptor;
            this.sourceFile = sourceFile;
            this.runnable = runnable;
            this.publicVisible = publicVisible;
            this.nested = nested;
            this.ownerModule = ownerModule;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public ResourceLoader getLoader() { return loader; }
        public void setLoader(ResourceLoader loader) { this.loader = loader; }
        public FCModuleDescriptor getDescriptor() { return descriptor; }
        public void setDescriptor(FCModuleDescriptor descriptor) { this.descriptor = descriptor; }
        public File getSourceFile() { return sourceFile; }
        public void setSourceFile(File sourceFile) { this.sourceFile = sourceFile; }
        public boolean isRunnable() { return runnable; }
        public void setRunnable(boolean runnable) { this.runnable = runnable; }
        public boolean isPublicVisible() { return publicVisible; }
        public void setPublicVisible(boolean publicVisible) { this.publicVisible = publicVisible; }
        public boolean isNested() { return nested; }
        public void setNested(boolean nested) { this.nested = nested; }
        public String getOwnerModule() { return ownerModule; }
        public void setOwnerModule(String ownerModule) { this.ownerModule = ownerModule; }
        public List<String> getPrivateDependencies() { return privateDependencies; }

        public void addPrivateDependency(String moduleName) {
            if (moduleName != null && !moduleName.isEmpty() && !privateDependencies.contains(moduleName)) {
                privateDependencies.add(moduleName);
            }
        }
    }

    private final LinkedHashMap<String, ModuleLoadingMapEntry> entries = new LinkedHashMap<String, ModuleLoadingMapEntry>();

    public ModuleLoadingMapEntry getBySource(String source) {
        if (source == null) return null;
        for (ModuleLoadingMapEntry entry : entries.values()) {
            if (source.equals(entry.getSource())) {
                return entry;
            }
        }
        return null;
    }

    public ModuleLoadingMapEntry getByFile(File file) {
        if (file == null) return null;
        try {
            String canonical = file.getCanonicalPath();
            for (ModuleLoadingMapEntry entry : entries.values()) {
                File sourceFile = entry.getSourceFile();
                if (sourceFile != null && canonical.equals(sourceFile.getCanonicalPath())) {
                    return entry;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<ModuleLoadingMapEntry> snapshot() {
        return new ArrayList<ModuleLoadingMapEntry>(entries.values());
    }

    @Override public int size() { return entries.size(); }
    @Override public boolean isEmpty() { return entries.isEmpty(); }
    @Override public boolean containsKey(Object key) { return entries.containsKey(key); }
    @Override public boolean containsValue(Object value) { return entries.containsValue(value); }
    @Override public ModuleLoadingMapEntry get(Object key) { return entries.get(key); }

    @Override
    public ModuleLoadingMapEntry put(String key, ModuleLoadingMapEntry value) {
        if (value == null) return entries.remove(key);
        value.setName(key);
        return entries.put(key, value);
    }

    @Override public ModuleLoadingMapEntry remove(Object key) { return entries.remove(key); }
    public ModuleLoadingMapEntry removebyName(String name) { return entries.remove(name); }
    @Override public void putAll(Map<? extends String, ? extends ModuleLoadingMapEntry> m) { entries.putAll(m); }
    @Override public void clear() { entries.clear(); }
    @Override public Set<String> keySet() { return new LinkedHashSet<String>(entries.keySet()); }
    @Override public Collection<ModuleLoadingMapEntry> values() { return entries.values(); }
    @Override public Set<Entry<String, ModuleLoadingMapEntry>> entrySet() { return entries.entrySet(); }
}
