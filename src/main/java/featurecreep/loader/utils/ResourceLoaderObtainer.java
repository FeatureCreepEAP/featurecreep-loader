package featurecreep.loader.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;

/**
 * Public-API replacement for the old reflective ModuleClassLoader
 * getResourceLoaders() access.  Consumers historically expect ResourceLoader[];
 * expose a lightweight iterable view of the module's local resources instead.
 */
public final class ResourceLoaderObtainer {

    private ResourceLoaderObtainer() {
    }

    public static ResourceLoader[] getResourceLoaders(Module mod) {
        if (mod == null) {
            return new ResourceLoader[0];
        }
        return new ResourceLoader[] { new ModuleClassLoaderResourceView(mod.getClassLoader()) };
    }

    private static final class ModuleClassLoaderResourceView extends AbstractResourceLoader
            implements IterableResourceLoader {

        private final ModuleClassLoader loader;

        ModuleClassLoaderResourceView(ModuleClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public String getRootName() {
            return loader.getName();
        }

        @Override
        public Resource getResource(String name) {
            List<Resource> resources = loader.loadResourceLocal(name);
            return resources == null || resources.isEmpty() ? null : resources.get(0);
        }

        @Override
        public Collection<String> getPaths() {
            return loader.getLocalPaths();
        }

        @Override
        public Iterator<Resource> iterateResources(String startPath, boolean recursive) {
            return loader.iterateResources(startPath == null ? "" : startPath, recursive);
        }

        @Override
        public URI getLocation() {
            return null;
        }

        @Override
        public void close() {
            // The ModuleClassLoader owns its resource roots; this is only a view.
        }

        // JBoss 1.x compatibility hook; 2.x's two-argument default may delegate here.
        public ResourceLoader createSubloader(String relativePath) {
            return null;
        }

        public ResourceLoader createSubloader(String relativePath, String loaderName) {
            return null;
        }
    }
}
