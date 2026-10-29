package featurecreep.loader.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import featurecreep.loader.mapping.MappingExRecord.Type;

class MappingExParserTest {

    private static List<MappingExRecord> parse(String text) throws IOException {
        return MappingExParser.parse(new StringReader(text));
    }

    @Test
    void skipsHeaderAndBlankLines() throws IOException {
        List<MappingExRecord> records = parse("Tipo¶Original\n\nAccessFlag¶net.Foo¶1¶nil¶nil¶nil\n");

        assertEquals(List.of(new MappingExRecord(Type.ACCESS_FLAG, "net.Foo", "1", null, null, null)), records);
    }

    @Test
    void nilAndEmptyCellsBecomeNull() throws IOException {
        List<MappingExRecord> records = parse("h\nClass¶net.Foo¶¶nil¶¶\n");

        assertEquals(List.of(new MappingExRecord(Type.CLASS, "net.Foo", null, null, null, null)), records);
    }

    @Test
    void acceptsSpanishRecordTypes() throws IOException {
        List<MappingExRecord> records = parse("h\nIncluir¶x¶nil¶nil¶nil¶nil\n"
                + "BanderaDeAcceso¶net.Foo¶0x0001¶nil¶nil¶nil\n");

        assertEquals(Type.INCLUDE, records.get(0).type());
        assertEquals(Type.ACCESS_FLAG, records.get(1).type());
    }

    @Test
    void skipsUnknownRecordTypes() throws IOException {
        assertTrue(parse("h\nComentario¶x¶y\n").isEmpty());
    }

    @Test
    void rejectsInvalidAccessFlagWithLineNumber() {
        IOException e = assertThrows(IOException.class,
                () -> parse("h\nAccessFlag¶net.Foo¶zz¶nil¶nil¶nil\n"));

        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void rejectsAccessFlagWithoutValue() {
        assertThrows(IOException.class, () -> parse("h\nAccessFlag¶net.Foo¶nil¶nil¶nil¶nil\n"));
    }

    @Test
    void parsesDecimalAndHexFlags() {
        assertEquals(1, MappingExParser.parseFlags("1"));
        assertEquals(16, MappingExParser.parseFlags("0x0010"));
        assertEquals(16, MappingExParser.parseFlags("0X10"));
        assertEquals(0xFFFF, MappingExParser.parseFlags("0xFFFF"));
    }

    @Test
    void leadingZeroDecimalIsNotOctal() {
        assertEquals(10, MappingExParser.parseFlags("010"));
    }

    @Test
    void rejectsOutOfRangeAndMalformedFlags() {
        assertThrows(IllegalArgumentException.class, () -> MappingExParser.parseFlags("65536"));
        assertThrows(IllegalArgumentException.class, () -> MappingExParser.parseFlags("0x10000"));
        assertThrows(IllegalArgumentException.class, () -> MappingExParser.parseFlags("-1"));
        assertThrows(IllegalArgumentException.class, () -> MappingExParser.parseFlags("abc"));
        assertThrows(IllegalArgumentException.class, () -> MappingExParser.parseFlags("0x"));
    }

    @Test
    void typeLookupIsCaseInsensitiveAndUnknownIsNull() {
        assertEquals(Type.CLASS, Type.parse("CLASS"));
        assertNull(Type.parse("Nope"));
    }
}
