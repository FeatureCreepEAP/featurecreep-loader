package featurecreep.loader.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import featurecreep.loader.mapping.MappingRules.ClassAccessRules;

class MappingRulesTest {

    private static MappingExRecord access(String original, String flags, String methodName) {
        return new MappingExRecord(MappingExRecord.Type.ACCESS_FLAG, original, flags, methodName, null, null);
    }

    @Test
    void indexesClassLevelRuleByInternalName() {
        ClassAccessRules rules = MappingRules.of(List.of(access("net.Foo", "1", null))).forClass("net/Foo");

        assertEquals(1, rules.classFlags());
    }

    @Test
    void nestedClassNameKeepsDollarSign() {
        ClassAccessRules rules = MappingRules.of(List.of(access("net.Foo$Bar", "1", null))).forClass("net/Foo$Bar");

        assertNotNull(rules);
        assertEquals(1, rules.classFlags());
    }

    @Test
    void indexesFieldRuleByNameAndDescriptor() {
        ClassAccessRules rules = MappingRules.of(List.of(access("net.Foo.bar:I", "2", null))).forClass("net/Foo");

        assertEquals(2, rules.fieldFlags("bar", "I"));
        assertNull(rules.fieldFlags("bar", "J"));
        assertNull(rules.classFlags());
    }

    @Test
    void indexesMethodRuleByNameAndDescriptor() {
        ClassAccessRules rules = MappingRules.of(List.of(access("net.Foo.baz(I)V", "1", "baz"))).forClass("net/Foo");

        assertEquals(1, rules.methodFlags("baz", "(I)V"));
        assertNull(rules.methodFlags("baz", "()V"));
    }

    @Test
    void methodNameMustMatchPath() {
        assertThrows(IllegalArgumentException.class,
                () -> MappingRules.of(List.of(access("net.Foo.baz(I)V", "1", "other"))));
    }

    @Test
    void ignoresNonAccessFlagRecords() {
        MappingRules rules = MappingRules.of(List.of(
                new MappingExRecord(MappingExRecord.Type.CLASS, "net.Foo", "net.Bar", null, null, null)));

        assertTrue(rules.isEmpty());
        assertNull(rules.forClass("net/Foo"));
    }

    @Test
    void emptyRulesReturnNoClass() {
        assertTrue(MappingRules.empty().isEmpty());
        assertNull(MappingRules.empty().forClass("net/Foo"));
    }
}
