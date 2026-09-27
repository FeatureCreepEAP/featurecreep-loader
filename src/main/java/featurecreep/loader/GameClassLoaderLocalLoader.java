package featurecreep.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.modules.LocalLoader;
import org.jboss.modules.Resource;

/**
 * Bridges the already-running game/application class loader into JBoss Modules.
 * This is intentionally a one-way bridge: the host loaders must not delegate
 * back into FeatureCreep's ModuleLoader, which avoids class-loader cycles.
 */
public final class GameClassLoaderLocalLoader implements LocalLoader {

    private final List<ClassLoader> classLoaders;

    public GameClassLoaderLocalLoader(List<ClassLoader> classLoaders) {
        Set<ClassLoader> unique = new LinkedHashSet<ClassLoader>();
        if (classLoaders != null) {
            for (ClassLoader loader : classLoaders) {
                if (loader != null && !(loader instanceof org.jboss.modules.ModuleClassLoader)) {
                    unique.add(loader);
                }
            }
        }
        this.classLoaders = Collections.unmodifiableList(new ArrayList<ClassLoader>(unique));
    }

    @Override
    public Class<?> loadClassLocal(String name, boolean resolve) {
        for (ClassLoader loader : classLoaders) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        return null;
    }

    @Override
    public Package loadPackageLocal(String name) {
        for (ClassLoader loader : classLoaders) {
            try {
                Package pkg = loader.getDefinedPackage(name);
                if (pkg != null) {
                    return pkg;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public List<Resource> loadResourceLocal(String name) {
        List<Resource> result = new ArrayList<Resource>();
        Set<String> seen = new LinkedHashSet<String>();
        for (ClassLoader loader : classLoaders) {
            try {
                Enumeration<URL> resources = loader.getResources(name);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    if (seen.add(url.toExternalForm())) {
                        result.add(new URLBackedResource(name, url));
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return result;
    }

    private static final class URLBackedResource implements Resource {
        private final String name;
        private final URL url;

        URLBackedResource(String name, URL url) {
            this.name = name;
            this.url = url;
        }

        @Override public String getName() { return name; }
        @Override public URL getURL() { return url; }
        @Override public InputStream openStream() throws IOException { return url.openStream(); }

        @Override
        public long getSize() {
            try {
                URLConnection connection = url.openConnection();
                long length = connection.getContentLengthLong();
                return length < 0 ? 0 : length;
            } catch (IOException e) {
                return 0;
            }
        }
    }
}
