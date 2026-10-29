package featurecreep.loader.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Persisted settings for one module, stored as {@code <configDir>/<modId>.properties}.
 *
 * <p>Reads never throw. A missing file yields the caller's defaults silently. A corrupt
 * file or a value that does not parse yields the default, and each such problem is
 * reported once. A corrupt file is never overwritten: {@link #save()} fails until the
 * file is repaired.
 *
 * <p>{@link #save()} writes a temporary file beside the target and moves it into place,
 * so a crash leaves either the previous or the new contents. Instances come from
 * {@link FCConfigApi#getValues(String)} and are thread-safe.
 */
public final class FCConfigValues {

    static final String FILE_SUFFIX = ".properties";

    private static final Pattern MODULE_ID = Pattern.compile("[A-Za-z0-9._+\\-]{1,128}");

    private final String modId;
    private final Path file;
    private final Properties properties = new Properties();
    private final Set<String> reported = new HashSet<String>();
    private boolean loaded;
    private boolean corrupt;

    private FCConfigValues(String modId, Path file) {
        this.modId = modId;
        this.file = file;
    }

    /** A values handle for {@code configDir}, or a no-op handle when {@code configDir} is {@code null}. */
    static FCConfigValues open(String modId, Path configDir) {
        return new FCConfigValues(modId, configDir == null ? null : configDir.resolve(modId + FILE_SUFFIX));
    }

    static String checkModId(String modId) {
        if (modId == null || !MODULE_ID.matcher(modId).matches() || modId.chars().allMatch(c -> c == '.')) {
            throw new IllegalArgumentException("Config module id must be 1-128 letters, digits, '.', '_', '+' or '-': " + modId);
        }
        return modId;
    }

    public String getModId() {
        return modId;
    }

    public synchronized boolean getBoolean(String key, boolean defaultValue) {
        String value = raw(key);
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        return invalid(key, value, "boolean", defaultValue);
    }

    public synchronized int getInt(String key, int defaultValue) {
        String value = raw(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return invalid(key, value, "int", defaultValue);
        }
    }

    public synchronized long getLong(String key, long defaultValue) {
        String value = raw(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return invalid(key, value, "long", defaultValue);
        }
    }

    public synchronized String getString(String key, String defaultValue) {
        String value = raw(key);
        return value == null ? defaultValue : value;
    }

    /** Sets a value in memory. It reaches disk on the next {@link #save()}. */
    public synchronized void set(String key, String value) {
        Objects.requireNonNull(value, "value");
        ensureLoaded();
        if (file != null) {
            properties.setProperty(requireKey(key), value);
        }
    }

    /**
     * Writes all values atomically. Does nothing for a no-op handle.
     *
     * @throws IOException if the file cannot be written, or the existing file is corrupt
     */
    public synchronized void save() throws IOException {
        if (file == null) {
            return;
        }
        ensureLoaded();
        if (corrupt) {
            throw new IOException("Refusing to overwrite corrupt config file " + file);
        }
        Path dir = file.getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "FeatureCreep configuration for " + modId);
            }
            moveIntoPlace(temp);
            moved = true;
        } finally {
            if (!moved) {
                deleteQuietly(temp);
            }
        }
    }

    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // Leaving a stray temp file is harmless; the original failure is the one to report.
        }
    }

    private String raw(String key) {
        ensureLoaded();
        return properties.getProperty(requireKey(key));
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            properties.clear();
            corrupt = true;
            report("file", "file " + file + " is unreadable (" + e.getMessage() + "); using defaults");
        }
    }

    private <T> T invalid(String key, String value, String type, T defaultValue) {
        report("key " + key, "key '" + key + "' has value '" + value + "', which is not a valid " + type
                + "; using the default");
        return defaultValue;
    }

    private void report(String what, String message) {
        if (reported.add(what)) {
            System.err.println("FeatureCreep config '" + modId + "': " + message);
        }
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Config key must not be empty");
        }
        return key;
    }
}
