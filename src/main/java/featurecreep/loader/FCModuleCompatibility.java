package featurecreep.loader;

import java.util.Map;

/**
 * Checks a module's declared target game and loader requirements against the
 * running host before the module is registered for JBoss resolution.
 *
 * <p>A declared requirement the host cannot satisfy is a mismatch and the module
 * is skipped. A requirement whose host value is unknown ({@code null}) is not a
 * mismatch. A malformed range is always a mismatch, so it is never treated as
 * compatible.
 */
public final class FCModuleCompatibility {

    private FCModuleCompatibility() {
    }

    /**
     * @return a human-readable reason the module is incompatible, or {@code null}
     *         when it is compatible
     */
    public static String findMismatch(Map<String, String> properties, GameProvider provider) {
        if (provider == null) {
            return findMismatch(properties, null, null, null);
        }
        return findMismatch(properties, provider.getGameId(), provider.getGameVersion(), provider.getLoaderVersion());
    }

    /**
     * Core check with explicit host values; {@code null} host values are unknown.
     *
     * @return a reason for the mismatch, or {@code null} when compatible
     */
    public static String findMismatch(Map<String, String> properties, String hostGameId,
            String hostGameVersion, String hostLoaderVersion) {
        String targetGame = trimmedOrNull(FCModuleProperties.get(properties, FCModuleProperties.TARGET_GAME));
        if (targetGame != null && hostGameId != null && !targetGame.equalsIgnoreCase(hostGameId.trim())) {
            return "targets game '" + targetGame + "' but the running game is '" + hostGameId + "'";
        }

        String targetVersion = FCModuleProperties.get(properties, FCModuleProperties.TARGET_GAME_VERSION);
        if (targetVersion == null) {
            targetVersion = FCModuleProperties.get(properties, FCModuleProperties.TARGET_VERSION_RANGE);
        }
        String reason = rangeMismatch(FCModuleProperties.TARGET_GAME_VERSION,
                targetVersion, "game version", hostGameVersion);
        if (reason != null) {
            return reason;
        }
        String loaderVersion = FCModuleProperties.get(properties, FCModuleProperties.LOADER_VERSION);
        if (loaderVersion == null) {
            loaderVersion = FCModuleProperties.get(properties, FCModuleProperties.LOADER_VERSION_RANGE);
        }
        return rangeMismatch(FCModuleProperties.LOADER_VERSION,
                loaderVersion, "FeatureCreep loader version", hostLoaderVersion);
    }

    private static String rangeMismatch(String key, String declared, String what, String hostVersion) {
        String spec = trimmedOrNull(declared);
        if (spec == null) {
            return null;
        }
        FCVersionRange range;
        try {
            range = FCVersionRange.parse(spec);
        } catch (IllegalArgumentException e) {
            return key + " '" + spec + "' is malformed: " + e.getMessage();
        }
        if (hostVersion == null) {
            return null;
        }
        try {
            if (range.matches(hostVersion)) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            return "the running " + what + " '" + hostVersion + "' is not a valid version: " + e.getMessage();
        }
        return "requires " + what + " " + spec + " but the running version is " + hostVersion;
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
