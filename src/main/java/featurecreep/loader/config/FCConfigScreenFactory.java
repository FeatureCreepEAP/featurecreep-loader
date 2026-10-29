package featurecreep.loader.config;

/**
 * Creates a host-side configuration screen for a module.
 *
 * <p>The loader does not know the UI toolkit. The parent and the returned object
 * are opaque: a Minecraft target passes its current {@code Screen} as the parent
 * and returns the screen it wants to display. Implementations may throw; the
 * registry isolates the failure and reports it instead of propagating it.
 */
@FunctionalInterface
public interface FCConfigScreenFactory {

    /**
     * @param parent host UI object the screen should return to; may be {@code null}
     * @return the host UI object to display, or {@code null} when nothing can be shown
     * @throws Exception any failure while building the screen
     */
    Object createScreen(Object parent) throws Exception;
}
