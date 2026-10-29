package featurecreep.loader.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappingExTransformerTest {

    private static final int PUBLIC = 0x0001;
    private static final int PRIVATE = 0x0002;
    private static final int STATIC = 0x0008;
    private static final int FINAL = 0x0010;

    private static final String FIXTURE = """
            package fixture;

            class Target {
                private int hidden;
                protected static final String KEEP = "x";
                private final long finalField = 1L;
                void pkg() {}
                private void priv() {}
                public final void pub() {}
                private static class Inner {}
            }
            """;

    @TempDir
    Path dir;

    private byte[] read(String binaryName) throws IOException {
        Path source = dir.resolve("fixture/Target.java");
        if (!Files.exists(source)) {
            Files.createDirectories(source.getParent());
            Files.writeString(source, FIXTURE);
            int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-proc:none", "-d", dir.toString(), source.toString());
            assertEquals(0, rc, "fixture failed to compile");
        }
        return Files.readAllBytes(dir.resolve(binaryName.replace('.', '/') + ".class"));
    }

    private static MappingRules rules(MappingExRecord... records) {
        return MappingRules.of(List.of(records));
    }

    private static MappingExRecord access(String original, String flags, String methodName) {
        return new MappingExRecord(MappingExRecord.Type.ACCESS_FLAG, original, flags, methodName, null, null);
    }

    private static ByteBuffer apply(MappingRules rules, String className, byte[] bytes) {
        return new MappingExTransformer(rules).transform(null, className, null, ByteBuffer.wrap(bytes));
    }

    private static ClassModel model(ByteBuffer buffer) {
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return ClassFile.of().parse(out);
    }

    private static int fieldFlags(ClassModel model, String name) {
        return model.fields().stream()
                .filter(f -> f.fieldName().stringValue().equals(name))
                .findFirst().orElseThrow().flags().flagsMask();
    }

    private static int methodFlags(ClassModel model, String name) {
        return model.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name))
                .findFirst().orElseThrow().flags().flagsMask();
    }

    private static int innerFlags(ClassModel model, String internalName) {
        for (ClassElement element : model) {
            if (element instanceof InnerClassesAttribute attribute) {
                for (InnerClassInfo info : attribute.classes()) {
                    if (info.innerClass().asInternalName().equals(internalName)) {
                        return info.flagsMask();
                    }
                }
            }
        }
        throw new AssertionError("no InnerClasses entry for " + internalName);
    }

    @Test
    void widensPrivateFieldAndMethodToPublic() throws IOException {
        MappingRules rules = rules(
                access("fixture.Target.hidden:I", "1", null),
                access("fixture.Target.priv()V", "1", "priv"));

        ClassModel out = model(apply(rules, "fixture.Target", read("fixture.Target")));

        assertEquals(PUBLIC, fieldFlags(out, "hidden"));
        assertEquals(PUBLIC, methodFlags(out, "priv"));
    }

    @Test
    void clearsFinalWhenRuleOmitsIt() throws IOException {
        MappingRules rules = rules(access("fixture.Target.finalField:J", "1", null));

        ClassModel out = model(apply(rules, "fixture.Target", read("fixture.Target")));

        assertEquals(PUBLIC, fieldFlags(out, "finalField"));
    }

    @Test
    void keepsFinalWhenRuleSetsIt() throws IOException {
        MappingRules rules = rules(access("fixture.Target.finalField:J", "0x11", null));

        ClassModel out = model(apply(rules, "fixture.Target", read("fixture.Target")));

        assertEquals(PUBLIC | FINAL, fieldFlags(out, "finalField"));
    }

    @Test
    void narrowingIsSkippedButFinalIsStillCleared() throws IOException {
        MappingRules rules = rules(access("fixture.Target.pub()V", "2", "pub"));

        ClassModel out = model(apply(rules, "fixture.Target", read("fixture.Target")));

        assertEquals(PUBLIC, methodFlags(out, "pub"));
    }

    @Test
    void returnsNullWhenNoFlagChanges() throws IOException {
        MappingRules rules = rules(access("fixture.Target.pkg()V", "0", "pkg"));

        assertNull(apply(rules, "fixture.Target", read("fixture.Target")));
    }

    @Test
    void returnsNullForUnmappedClass() throws IOException {
        MappingRules rules = rules(access("other.Thing", "1", null));

        assertNull(apply(rules, "fixture.Target", read("fixture.Target")));
    }

    @Test
    void widensTopLevelClassHeader() throws IOException {
        MappingRules rules = rules(access("fixture.Target", "1", null));

        ClassModel out = model(apply(rules, "fixture.Target", read("fixture.Target")));

        assertNotEquals(0, out.flags().flagsMask() & PUBLIC);
    }

    @Test
    void widensNestedClassInnerClassesEntry() throws IOException {
        MappingRules rules = rules(access("fixture.Target$Inner", "1", null));

        ClassModel out = model(apply(rules, "fixture.Target$Inner", read("fixture.Target$Inner")));

        assertEquals(PUBLIC | STATIC, innerFlags(out, "fixture/Target$Inner"));
        assertNotEquals(0, out.flags().flagsMask() & PUBLIC);
        assertEquals(0, innerFlags(out, "fixture/Target$Inner") & PRIVATE);
    }

    @Test
    void unparseableBytesAreLeftAlone() {
        assertNull(apply(rules(access("fixture.Target", "1", null)), "fixture.Target", new byte[] {1, 2, 3}));
    }

    @Test
    void methodNameMismatchFailsAtLoad() {
        assertThrows(IllegalArgumentException.class, () -> rules(access("fixture.Target.priv()V", "1", "pub")));
    }
}
