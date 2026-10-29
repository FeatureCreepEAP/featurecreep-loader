package featurecreep.loader.finder;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;

/**
 * Serves a jar's resources to its module while leaving its classes to the application class loader.
 */
final class AppPathResourceLoader implements ResourceLoader {

    private final ResourceLoader delegate;

    AppPathResourceLoader(ResourceLoader delegate) {
        this.delegate = delegate;
    }

    @Override
    public ClassSpec getClassSpec(String fileName) {
        return null;
    }

    @Override
    public PackageSpec getPackageSpec(String name) throws IOException {
        return delegate.getPackageSpec(name);
    }

    @Override
    public Resource getResource(String name) {
        return delegate.getResource(name);
    }

    @Override
    public String getLibrary(String name) {
        return delegate.getLibrary(name);
    }

    @Override
    public Collection<String> getPaths() {
        return delegate.getPaths();
    }

    @Override
    public String getRootName() {
        return delegate.getRootName();
    }

    @Override
    public URI getLocation() {
        return delegate.getLocation();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
