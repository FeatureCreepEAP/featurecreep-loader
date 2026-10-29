package featurecreep.loader.share;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store that lets modules pass objects to each other without a compile-time
 * dependency on one another.
 *
 * <p>Entries are addressed by module identifier and key and stored as {@code modId:key}.
 * Module identifiers may not contain {@code ':'}, so the two parts always split
 * unambiguously. Keys may contain {@code ':'}.
 */
public final class ObjectShare {

    private static final char SEPARATOR = ':';

    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();

    public void put(String modId, String key, Object value) {
        values.put(composite(modId, key), Objects.requireNonNull(value, "value"));
    }

    /** @return {@code true} if the value was stored, {@code false} if the key was already present */
    public boolean putIfAbsent(String modId, String key, Object value) {
        return values.putIfAbsent(composite(modId, key), Objects.requireNonNull(value, "value")) == null;
    }

    /**
     * @return the stored value, or empty when the key is absent
     * @throws ClassCastException if the stored value is not an instance of {@code type}
     */
    public <T> Optional<T> get(String modId, String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = values.get(composite(modId, key));
        return value == null ? Optional.empty() : Optional.of(type.cast(value));
    }

    /** @return {@code true} if a value was removed */
    public boolean remove(String modId, String key) {
        return values.remove(composite(modId, key)) != null;
    }

    /** Keys stored under {@code modId}, without the module prefix, in sorted order. */
    public Set<String> keys(String modId) {
        String prefix = checkModId(modId) + SEPARATOR;
        Set<String> keys = new TreeSet<>();
        for (String composite : values.keySet()) {
            if (composite.startsWith(prefix)) {
                keys.add(composite.substring(prefix.length()));
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    private static String composite(String modId, String key) {
        return checkModId(modId) + SEPARATOR + checkKey(key);
    }

    private static String checkModId(String modId) {
        if (modId == null || modId.isEmpty() || modId.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException("Invalid module id for ObjectShare: " + modId);
        }
        return modId;
    }

    private static String checkKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("ObjectShare key must not be empty");
        }
        return key;
    }
}
