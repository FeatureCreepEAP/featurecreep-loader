package featurecreep.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the loader's own version from the Maven metadata packaged in its JAR. */
public final class FCLoaderVersion {

    private static final String POM_PROPERTIES = "/META-INF/maven/com.asbestosstar/featurecreep-loader/pom.properties";

    private FCLoaderVersion() {}

    /** The loader version, or {@code null} when the loader was not packaged with its Maven metadata. */
    public static String get() {
        try (InputStream in = FCLoaderVersion.class.getResourceAsStream(POM_PROPERTIES)) {
            if (in == null) return null;
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            return version == null || version.isBlank() ? null : version.trim();
        } catch (IOException e) {
            return null;
        }
    }
}
