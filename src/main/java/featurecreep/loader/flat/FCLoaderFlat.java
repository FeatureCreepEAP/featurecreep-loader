package featurecreep.loader.flat;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import featurecreep.loader.GameProvider;
import featurecreep.loader.eventviewer.EventViewer;

public interface FCLoaderFlat {

    public GameProvider getGameProvider();

    public default boolean setDebugMode(boolean val) {
        return getGameProvider().setDebugMode(val);
    }

    public default boolean getDebugMode() {
        return getGameProvider().getDebugMode();
    }

    /**
     * Returns the ClassLoader being used by this FlatLoader.
     * Usually a URLClassLoader with all mods added to the classpath.
     * @return the ClassLoader instance
     */
    public ClassLoader getFlatClassLoader();

    public void loadMods();

    public void runMods();

    /**
     * Adds a transformer to the global transformer list.
     * @param transformer the transformer to add
     */
    public void addTransformer(ClassFileTransformer transformer);

    /**
     * Retrieves the list of active transformers.
     * @return the list of transformers
     */
    public ArrayList<ClassFileTransformer> getTransformers();

    /**
     * Returns the EventViewer instance.
     * @return the EventViewer
     */
    public EventViewer getEventViewer();

    /**
     * Returns the location of mod files.
     * @return an array of Files representing mod locations
     */
    public default File[] getModLocations() {
        return getGameProvider().getModulePKZipLocations()[0].toFile().listFiles();
    }

    /**
     * Returns the metadata map for loaded mods (ModID -&gt; Properties).
     * @return a map of mod IDs to their metadata
     */
    public Map<String, FlatModMetadata> getModMetadataMap();

    /**
     * Checks if mods have been loaded.
     * @return true if mods are loaded
     */
    public boolean getModsLoaded();
    
    /**
     * Specific to FlatLoader: Checks if Hotswap is needed based on manifest attributes.
     * @return true if hotswap is required
     */
    public boolean isHotswapNeeded();

    /**
     * Returns a collection of resource paths found in the loaded flat mods starting with the given prefix.
     * Useful for resource pack implementations.
     * @param prefix The path prefix to search for (e.g., "assets/mymod/textures").
     * @return A collection of matching resource paths.
     */
    public Collection<String> getEntries(String prefix);
}