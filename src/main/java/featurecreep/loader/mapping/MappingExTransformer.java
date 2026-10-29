package featurecreep.loader.mapping;

import java.io.IOException;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.ClassTransformer;

import featurecreep.loader.mapping.MappingRules.ClassAccessRules;

/**
 * Applies MappingEx access rules. Visibility is only raised and {@code final} is cleared; a rule
 * that would lower visibility is logged once and skipped.
 *
 * <p>Returns {@code null} when nothing changes, so unaffected classes are not rebuilt.
 */
public final class MappingExTransformer implements ClassTransformer {

    static final int ACC_PUBLIC = 0x0001;
    static final int ACC_PRIVATE = 0x0002;
    static final int ACC_PROTECTED = 0x0004;
    static final int ACC_FINAL = 0x0010;
    static final int VISIBILITY = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED;

    private final MappingRules rules;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public MappingExTransformer(MappingRules rules) {
        this.rules = rules;
    }

    public static MappingExTransformer fromFiles(Collection<Path> files) throws IOException {
        List<MappingExRecord> records = new ArrayList<>();
        for (Path file : files) {
            records.addAll(MappingExParser.parse(file));
        }
        return new MappingExTransformer(MappingRules.of(records));
    }

    @Override
    public ByteBuffer transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
            ByteBuffer classBuffer) {
        if (className == null || classBuffer == null || rules.isEmpty()) {
            return null;
        }
        ClassAccessRules access = rules.forClass(className.replace('.', '/'));
        if (access == null) {
            return null;
        }
        ClassFile classFile = ClassFile.of();
        ClassModel model;
        try {
            model = classFile.parse(copyReadable(classBuffer));
        } catch (IllegalArgumentException e) {
            warnOnce("unparseable:" + className, "MappingEx skipped unparseable class " + className + ": " + e.getMessage());
            return null;
        }
        String thisName = model.thisClass().asInternalName();
        int currentHeader = model.flags().flagsMask();
        int nextHeader = access.classFlags() == null
                ? currentHeader
                : headerFlags(currentHeader, access.classFlags(), thisName);
        if (nextHeader == currentHeader && !membersChanged(model, access, thisName)) {
            return null;
        }
        ClassTransform rewrite = (builder, element) -> rewriteElement(builder, element, access, thisName);
        ClassTransform transform = rewrite.andThen(ClassTransform.endHandler(builder -> builder.withFlags(nextHeader)));
        return ByteBuffer.wrap(classFile.transformClass(model, transform));
    }

    private boolean membersChanged(ClassModel model, ClassAccessRules access, String thisName) {
        for (FieldModel field : model.fields()) {
            if (fieldFlags(field, access, thisName) != field.flags().flagsMask()) {
                return true;
            }
        }
        for (MethodModel method : model.methods()) {
            if (methodFlags(method, access, thisName) != method.flags().flagsMask()) {
                return true;
            }
        }
        for (ClassElement element : model) {
            if (element instanceof InnerClassesAttribute attribute && innerChanged(attribute, access, thisName)) {
                return true;
            }
        }
        return false;
    }

    private void rewriteElement(ClassBuilder builder, ClassElement element, ClassAccessRules access, String thisName) {
        if (element instanceof FieldModel field) {
            int next = fieldFlags(field, access, thisName);
            if (next == field.flags().flagsMask()) {
                builder.with(field);
            } else {
                builder.withField(field.fieldName(), field.fieldType(), fb -> {
                    fb.withFlags(next);
                    for (FieldElement e : field) {
                        if (!(e instanceof AccessFlags)) {
                            fb.with(e);
                        }
                    }
                });
            }
        } else if (element instanceof MethodModel method) {
            int next = methodFlags(method, access, thisName);
            if (next == method.flags().flagsMask()) {
                builder.with(method);
            } else {
                builder.withMethod(method.methodName(), method.methodType(), next, mb -> {
                    for (MethodElement e : method) {
                        if (!(e instanceof AccessFlags)) {
                            mb.with(e);
                        }
                    }
                });
            }
        } else if (element instanceof InnerClassesAttribute attribute && innerChanged(attribute, access, thisName)) {
            builder.with(rewriteInner(attribute, access, thisName));
        } else {
            builder.with(element);
        }
    }

    private int fieldFlags(FieldModel field, ClassAccessRules access, String thisName) {
        String name = field.fieldName().stringValue();
        int current = field.flags().flagsMask();
        Integer target = access.fieldFlags(name, field.fieldType().stringValue());
        return target == null ? current : widen(current, target, thisName, name);
    }

    private int methodFlags(MethodModel method, ClassAccessRules access, String thisName) {
        String name = method.methodName().stringValue();
        int current = method.flags().flagsMask();
        Integer target = access.methodFlags(name, method.methodType().stringValue());
        return target == null ? current : widen(current, target, thisName, name);
    }

    /**
     * The class header carries only public or package visibility (the top-level rule), so the
     * header is raised to public only when the rule says public. Nested classes take their full
     * flags from the InnerClasses entry, which is handled separately.
     */
    private int headerFlags(int current, int target, String thisName) {
        int topLevel = (target & ACC_PUBLIC) != 0 ? ACC_PUBLIC : 0;
        return widen(current, topLevel | (target & ACC_FINAL), thisName, thisName);
    }

    private boolean innerChanged(InnerClassesAttribute attribute, ClassAccessRules access, String thisName) {
        for (InnerClassInfo info : attribute.classes()) {
            if (selfInnerFlags(info, access, thisName) != info.flagsMask()) {
                return true;
            }
        }
        return false;
    }

    private InnerClassesAttribute rewriteInner(InnerClassesAttribute attribute, ClassAccessRules access, String thisName) {
        List<InnerClassInfo> out = new ArrayList<>();
        for (InnerClassInfo info : attribute.classes()) {
            int next = selfInnerFlags(info, access, thisName);
            out.add(next == info.flagsMask() ? info
                    : InnerClassInfo.of(info.innerClass(), info.outerClass(), info.innerName(), next));
        }
        return InnerClassesAttribute.of(out);
    }

    private int selfInnerFlags(InnerClassInfo info, ClassAccessRules access, String thisName) {
        int current = info.flagsMask();
        Integer target = access.classFlags();
        if (target == null || !info.innerClass().asInternalName().equals(thisName)) {
            return current;
        }
        return widen(current, target, thisName, thisName);
    }

    /**
     * Raises visibility to the target's rank and clears {@code final} unless the target sets it.
     * A target that would lower visibility is ignored and logged.
     */
    private int widen(int current, int target, String owner, String member) {
        int next = current;
        int targetRank = rank(target);
        int currentRank = rank(current);
        if (targetRank > currentRank) {
            next = (current & ~VISIBILITY) | (target & VISIBILITY);
        } else if (targetRank < currentRank) {
            warnOnce("narrow:" + owner + "." + member, "MappingEx narrowing skipped for " + owner + "." + member);
        }
        if ((target & ACC_FINAL) == 0) {
            next &= ~ACC_FINAL;
        }
        return next;
    }

    /** private 0 &lt; package 1 &lt; protected 2 &lt; public 3. */
    private static int rank(int flags) {
        if ((flags & ACC_PUBLIC) != 0) {
            return 3;
        }
        if ((flags & ACC_PROTECTED) != 0) {
            return 2;
        }
        if ((flags & ACC_PRIVATE) != 0) {
            return 0;
        }
        return 1;
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            System.err.println(message);
        }
    }

    private static byte[] copyReadable(ByteBuffer buffer) {
        ByteBuffer readable = buffer.duplicate();
        byte[] out = new byte[readable.remaining()];
        readable.get(out);
        return out;
    }
}
