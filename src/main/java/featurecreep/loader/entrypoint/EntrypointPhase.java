package featurecreep.loader.entrypoint;

/**
 * Lifecycle phases in run order. Every module's preLaunch entrypoints run before any main,
 * and every main runs before any ready entrypoint.
 */
public enum EntrypointPhase {
    PRE_LAUNCH("fc:preLaunch"),
    MAIN("fc:main"),
    READY("fc:ready");

    private final String eventName;

    EntrypointPhase(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
