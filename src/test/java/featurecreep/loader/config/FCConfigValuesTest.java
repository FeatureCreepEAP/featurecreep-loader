package featurecreep.loader.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FCConfigValuesTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileYieldsDefaultsAndNothingIsCreatedUntilSave() throws IOException {
        Path dir = tempDir.resolve("etc");
        FCConfigValues values = new FCConfigRegistry(dir).getValues("mod.fresh");

        assertTrue(values.getBoolean("flag", true));
        assertEquals(7, values.getInt("count", 7));
        assertEquals(8L, values.getLong("big", 8L));
        assertEquals("x", values.getString("name", "x"));
        assertFalse(Files.exists(dir));

        values.save();
        assertTrue(Files.isRegularFile(dir.resolve("mod.fresh.properties")));
    }

    @Test
    void valuesRoundTripThroughANewRegistry() throws IOException {
        Path dir = tempDir.resolve("etc");
        FCConfigValues values = new FCConfigRegistry(dir).getValues("mod.round");
        values.set("enabled", "false");
        values.set("count", " 42 ");
        values.set("big", "9000000000");
        values.set("text", "a=b:c #tag \u00e9 \\ end");
        values.save();

        FCConfigValues reread = new FCConfigRegistry(dir).getValues("mod.round");
        assertFalse(reread.getBoolean("enabled", true));
        assertEquals(42, reread.getInt("count", 0));
        assertEquals(9_000_000_000L, reread.getLong("big", 0L));
        assertEquals("a=b:c #tag \u00e9 \\ end", reread.getString("text", null));
    }

    @Test
    void saveReplacesExistingFileAndLeavesNoTemporaryFiles() throws IOException {
        Path dir = tempDir.resolve("etc");
        FCConfigValues values = new FCConfigRegistry(dir).getValues("mod.atomic");
        values.set("n", "1");
        values.save();
        values.set("n", "2");
        values.save();

        assertEquals("2", new FCConfigRegistry(dir).getValues("mod.atomic").getString("n", null));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("mod.atomic.properties"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void unparseableValuesFallBackAndReportOnce() throws IOException {
        Path dir = tempDir.resolve("etc");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("mod.badvalue.properties"), "count=lots\nflag=maybe\n", StandardCharsets.UTF_8);
        FCConfigValues values = new FCConfigRegistry(dir).getValues("mod.badvalue");

        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            assertEquals(5, values.getInt("count", 5));
            assertEquals(5, values.getInt("count", 5));
            assertTrue(values.getBoolean("flag", true));
        } finally {
            System.setErr(original);
        }
        assertEquals(2, captured.toString(StandardCharsets.UTF_8).lines().count());
    }

    @Test
    void corruptEscapeSequenceYieldsDefaultsAndSaveRefusesToOverwrite() throws IOException {
        Path dir = tempDir.resolve("etc");
        Files.createDirectories(dir);
        Path file = dir.resolve("mod.escape.properties");
        String original = "count=\\uZZZZ\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);

        PrintStream previous = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        FCConfigValues values;
        try {
            values = new FCConfigRegistry(dir).getValues("mod.escape");
            assertEquals(3, values.getInt("count", 3));
        } finally {
            System.setErr(previous);
        }

        values.set("count", "4");
        assertThrows(IOException.class, values::save);
        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void invalidUtf8YieldsDefaultsAndSaveRefusesToOverwrite() throws IOException {
        Path dir = tempDir.resolve("etc");
        Files.createDirectories(dir);
        Path file = dir.resolve("mod.bytes.properties");
        byte[] original = { 'n', '=', (byte) 0xC3, '(' };
        Files.write(file, original);

        PrintStream previous = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        FCConfigValues values;
        try {
            values = new FCConfigRegistry(dir).getValues("mod.bytes");
            assertEquals("fallback", values.getString("n", "fallback"));
        } finally {
            System.setErr(previous);
        }

        values.set("n", "x");
        assertThrows(IOException.class, values::save);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void sameIdentifierReturnsTheSameHandle() {
        FCConfigApi api = new FCConfigRegistry(tempDir);
        assertSame(api.getValues("mod.same"), api.getValues("mod.same"));
    }

    @Test
    void unsafeModIdentifiersAreRejected() {
        FCConfigApi api = new FCConfigRegistry(tempDir);
        for (String bad : new String[] { null, "", ".", "..", "../escape", "a/b", "a\\b", "a:b", "a b", "mod\u00e9" }) {
            assertThrows(IllegalArgumentException.class, () -> api.getValues(bad), bad);
        }
    }

    @Test
    void absentApiGivesDefaultsAndNeverWrites() throws IOException {
        FCConfigValues values = FCConfigApi.none().getValues("mod.absent");
        values.set("flag", "true");
        assertFalse(values.getBoolean("flag", false));
        values.save();
        assertEquals("d", values.getString("x", "d"));
    }

    @Test
    void emptyKeysAreRejected() {
        FCConfigValues values = new FCConfigRegistry(tempDir).getValues("mod.keys");
        assertThrows(IllegalArgumentException.class, () -> values.set("", "v"));
        assertThrows(IllegalArgumentException.class, () -> values.getString("", "d"));
    }
}
