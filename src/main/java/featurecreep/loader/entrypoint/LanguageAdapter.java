package featurecreep.loader.entrypoint;

/**
 * Runs an entrypoint written in a language other than Java. Implementations are discovered
 * with {@link java.util.ServiceLoader} from the classpath or from any loaded module.
 */
public interface LanguageAdapter {

    String name();

    void invoke(Entrypoint entrypoint, ClassLoader moduleClassLoader, String[] args) throws Exception;
}
