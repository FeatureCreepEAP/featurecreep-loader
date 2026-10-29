package featurecreep.loader.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ObjectShareTest {

    @Test
    void storesAndReadsTypedValues() {
        ObjectShare share = new ObjectShare();
        List<String> list = List.of("a", "b");
        share.put("mod.a", "list", list);
        share.put("mod.a", "count", 3);

        assertEquals(Optional.of(list), share.get("mod.a", "list", List.class));
        assertEquals(Optional.of(3), share.get("mod.a", "count", Integer.class));
        assertFalse(share.get("mod.a", "missing", String.class).isPresent());
    }

    @Test
    void typeMismatchIsRejected() {
        ObjectShare share = new ObjectShare();
        share.put("mod.a", "count", 3);

        assertThrows(ClassCastException.class, () -> share.get("mod.a", "count", String.class));
    }

    @Test
    void valuesAreScopedByModule() {
        ObjectShare share = new ObjectShare();
        share.put("mod.a", "shared", "from-a");
        share.put("mod.b", "shared", "from-b");

        assertEquals(Optional.of("from-a"), share.get("mod.a", "shared", String.class));
        assertEquals(Optional.of("from-b"), share.get("mod.b", "shared", String.class));
    }

    @Test
    void colonsCannotMakeKeysAmbiguous() {
        ObjectShare share = new ObjectShare();
        share.put("mod.a", "b:c", "wanted");

        assertThrows(IllegalArgumentException.class, () -> share.get("mod.a:b", "c", String.class));
        assertThrows(IllegalArgumentException.class, () -> share.put("mod:a", "key", "x"));
        assertEquals(Optional.of("wanted"), share.get("mod.a", "b:c", String.class));
    }

    @Test
    void putIfAbsentKeepsTheFirstValue() {
        ObjectShare share = new ObjectShare();
        Object first = new Object();

        assertTrue(share.putIfAbsent("mod.a", "slot", first));
        assertFalse(share.putIfAbsent("mod.a", "slot", new Object()));
        assertSame(first, share.get("mod.a", "slot", Object.class).orElseThrow());
    }

    @Test
    void removeAndKeysReflectContents() {
        ObjectShare share = new ObjectShare();
        share.put("mod.a", "zeta", 1);
        share.put("mod.a", "alpha", 2);
        share.put("mod.b", "other", 3);

        assertEquals(List.of("alpha", "zeta"), List.copyOf(share.keys("mod.a")));
        assertTrue(share.remove("mod.a", "zeta"));
        assertFalse(share.remove("mod.a", "zeta"));
        assertEquals(List.of("alpha"), List.copyOf(share.keys("mod.a")));
        assertEquals(List.of("other"), List.copyOf(share.keys("mod.b")));
        assertThrows(UnsupportedOperationException.class, () -> share.keys("mod.a").clear());
    }

    @Test
    void invalidArgumentsAreRejected() {
        ObjectShare share = new ObjectShare();
        assertThrows(NullPointerException.class, () -> share.put("mod.a", "k", null));
        assertThrows(IllegalArgumentException.class, () -> share.put("", "k", "v"));
        assertThrows(IllegalArgumentException.class, () -> share.put(null, "k", "v"));
        assertThrows(IllegalArgumentException.class, () -> share.put("mod.a", "", "v"));
        assertThrows(NullPointerException.class, () -> share.get("mod.a", "k", null));
    }
}
