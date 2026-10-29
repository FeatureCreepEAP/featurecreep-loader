package featurecreep.loader.query;

import java.nio.file.Path;
import java.util.List;

/**
 * Read-only description of a loaded FeatureCreep module.
 *
 * @param id           module identifier
 * @param version      declared version, or {@code null} when none is declared
 * @param name         declared name, falling back to the identifier
 * @param path         source file or directory, or {@code null} when the module has none
 * @param side         {@code client}, {@code server} or {@code both}
 * @param dependencies identifiers of the module's declared dependencies
 */
public record ModContainer(String id, String version, String name, Path path, String side, List<String> dependencies) {

    public ModContainer {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
