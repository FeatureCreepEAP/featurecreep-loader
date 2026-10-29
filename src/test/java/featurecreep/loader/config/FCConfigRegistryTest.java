package featurecreep.loader.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class FCConfigRegistryTest {

    private static FCConfigEntry entry(String modId) {
        return FCConfigEntry.builder(modId).displayName(modId + " name").build();
    }

    @Test
    void registersAndEnumeratesEntriesInOrder() {
        FCConfigApi api = new FCConfigRegistry();
        FCConfigEntry first = entry("mod.first");
        FCConfigEntry second = entry("mod.second");

        assertTrue(api.register(first));
        assertTrue(api.register(second));

        assertSame(first, api.getEntry("mod.first").orElse(null));
        assertSame(second, api.getEntry(" mod.second ").orElse(null));
        assertEquals(Arrays.asList(first, second), api.getEntries());
        assertFalse(api.getEntry("missing").isPresent());
        assertFalse(api.getEntry(null).isPresent());
    }

    @Test
    void duplicateModIdsAreRejectedAndKeepTheFirstEntry() {
        FCConfigApi api = new FCConfigRegistry();
        FCConfigEntry original = entry("mod.dup");

        assertTrue(api.register(original));
        assertFalse(api.register(entry("mod.dup")));
        assertSame(original, api.getEntry("mod.dup").orElse(null));
        assertEquals(1, api.getEntries().size());
    }

    @Test
    void nullEntriesAndBlankIdsAreRejected() {
        FCConfigApi api = new FCConfigRegistry();
        assertFalse(api.register(null));
        assertThrows(IllegalArgumentException.class, () -> FCConfigEntry.builder("  "));
        assertThrows(IllegalArgumentException.class, () -> FCConfigEntry.builder(null));
    }

    @Test
    void entriesHaveSafeDefaultsAndOptionalFields() {
        FCConfigEntry bare = FCConfigEntry.builder("mod.bare").build();
        assertEquals("mod.bare", bare.getDisplayName());
        assertNull(bare.getIconPath());
        assertFalse(bare.hasScreen());

        FCConfigEntry full = FCConfigEntry.builder("mod.full")
                .displayName("  Full  ")
                .iconPath("assets/icon.png")
                .screenFactory(parent -> "screen")
                .build();
        assertEquals("Full", full.getDisplayName());
        assertEquals("assets/icon.png", full.getIconPath());
        assertTrue(full.hasScreen());
    }

    @Test
    void createScreenPassesParentThroughToFactory() {
        Object parent = new Object();
        FCConfigApi api = new FCConfigRegistry();
        api.register(FCConfigEntry.builder("mod.screen").screenFactory(p -> p == parent ? "screen" : "wrong").build());

        assertEquals(Optional.of("screen"), api.createScreen("mod.screen", parent));
    }

    @Test
    void createScreenIsEmptyForMissingOrScreenlessEntries() {
        FCConfigApi api = new FCConfigRegistry();
        api.register(entry("mod.no-screen"));
        assertFalse(api.createScreen("mod.no-screen", null).isPresent());
        assertFalse(api.createScreen("absent", null).isPresent());
    }

    @Test
    void throwingScreenFactoryIsIsolated() {
        FCConfigApi api = new FCConfigRegistry();
        api.register(FCConfigEntry.builder("mod.broken").screenFactory(parent -> {
            throw new IllegalStateException("boom");
        }).build());
        api.register(FCConfigEntry.builder("mod.nullscreen").screenFactory(parent -> null).build());

        assertFalse(api.createScreen("mod.broken", null).isPresent());
        assertFalse(api.createScreen("mod.nullscreen", null).isPresent());
        assertEquals(2, api.getEntries().size());
    }

    @Test
    void providersRegisterEntriesAndFailuresAreIsolated() {
        FCConfigApi api = new FCConfigRegistry();
        assertTrue(api.registerProvider(() -> entry("mod.good")));
        assertFalse(api.registerProvider(() -> {
            throw new RuntimeException("provider failure");
        }));
        assertFalse(api.registerProvider(() -> null));
        assertFalse(api.registerProvider(null));

        assertEquals(Collections.singletonList(api.getEntry("mod.good").orElse(null)), api.getEntries());
    }

    @Test
    void absentApiIsANoOp() {
        FCConfigApi none = FCConfigApi.none();
        assertFalse(none.register(entry("mod.any")));
        assertFalse(none.registerProvider(() -> entry("mod.any")));
        assertTrue(none.getEntries().isEmpty());
        assertFalse(none.getEntry("mod.any").isPresent());
        assertFalse(none.createScreen("mod.any", null).isPresent());
        assertEquals(0, none.discoverProviders(Collections.emptyList()));
    }

    @Test
    void entriesSnapshotIsUnmodifiable() {
        FCConfigApi api = new FCConfigRegistry();
        api.register(entry("mod.one"));
        List<FCConfigEntry> snapshot = api.getEntries();
        assertNotNull(snapshot);
        try {
            snapshot.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("entries snapshot must be unmodifiable");
    }
}
