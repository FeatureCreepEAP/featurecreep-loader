package featurecreep.loader.entrypoint;

import featurecreep.loader.ExecutionSide;

/**
 * A class a module asks the loader to run, declared as {@code <entrypoint>} in module.xml.
 */
public record Entrypoint(String type, String className, String adapter) {

    public static final String JAVA_ADAPTER = "java";
    public static final String MAIN = "main";
    public static final String PRE_LAUNCH = "preLaunch";
    public static final String CLIENT = "client";
    public static final String SERVER = "server";

    public Entrypoint {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("entrypoint type is required");
        if (className == null || className.isBlank()) throw new IllegalArgumentException("entrypoint class is required");
        adapter = adapter == null || adapter.isBlank() ? JAVA_ADAPTER : adapter.trim();
        type = type.trim();
        className = className.trim();
    }

    public EntrypointPhase phase() {
        if (PRE_LAUNCH.equals(type)) return EntrypointPhase.PRE_LAUNCH;
        if (MAIN.equals(type)) return EntrypointPhase.MAIN;
        return EntrypointPhase.READY;
    }

    public boolean runsOn(ExecutionSide side) {
        if (CLIENT.equals(type)) return side == ExecutionSide.CLIENT;
        if (SERVER.equals(type)) return side == ExecutionSide.SERVER;
        return true;
    }
}
