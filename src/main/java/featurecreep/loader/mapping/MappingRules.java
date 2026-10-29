package featurecreep.loader.mapping;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Access rules indexed by internal class name. Only {@code AccessFlag} rows are applied; other
 * record types are accepted by the parser and ignored here.
 *
 * <p>Member rules are keyed by {@code name:descriptor}. A class-level rule has no member key.
 */
public final class MappingRules {

    private static final MappingRules EMPTY = new MappingRules(Collections.emptyMap());

    private final Map<String, ClassAccessRules> byClass;

    private MappingRules(Map<String, ClassAccessRules> byClass) {
        this.byClass = byClass;
    }

    public static MappingRules empty() {
        return EMPTY;
    }

    /**
     * @throws IllegalArgumentException when an AccessFlag row has a malformed member path
     */
    public static MappingRules of(Collection<MappingExRecord> records) {
        Map<String, ClassAccessRules> byClass = new HashMap<>();
        for (MappingExRecord record : records) {
            if (record.type() != MappingExRecord.Type.ACCESS_FLAG) {
                continue;
            }
            int flags = MappingExParser.parseFlags(record.newName());
            String original = required(record, record.original());
            if (record.methodName() != null) {
                MemberPath path = MemberPath.method(original, record.methodName());
                classRules(byClass, path.owner()).methods.put(path.key(), flags);
            } else if (original.indexOf(':') >= 0) {
                MemberPath path = MemberPath.field(original);
                classRules(byClass, path.owner()).fields.put(path.key(), flags);
            } else {
                classRules(byClass, original.replace('.', '/')).classFlags = flags;
            }
        }
        if (byClass.isEmpty()) {
            return EMPTY;
        }
        return new MappingRules(byClass);
    }

    /**
     * Returns the rules for an internal class name, or {@code null} when the class has none.
     */
    public ClassAccessRules forClass(String internalName) {
        return byClass.get(internalName);
    }

    public boolean isEmpty() {
        return byClass.isEmpty();
    }

    private static ClassAccessRules classRules(Map<String, ClassAccessRules> byClass, String internalName) {
        return byClass.computeIfAbsent(internalName, k -> new ClassAccessRules());
    }

    private static String required(MappingExRecord record, String value) {
        if (value == null) {
            throw new IllegalArgumentException("AccessFlag row has no target: " + record);
        }
        return value;
    }

    /**
     * Access targets for one class. Instances are immutable after {@link MappingRules#of}.
     */
    public static final class ClassAccessRules {
        private Integer classFlags;
        private final Map<String, Integer> fields = new HashMap<>();
        private final Map<String, Integer> methods = new HashMap<>();

        ClassAccessRules() {
        }

        /** Target flags for the class, or {@code null} when no class-level rule exists. */
        public Integer classFlags() {
            return classFlags;
        }

        /** Target flags for a field, or {@code null}. */
        public Integer fieldFlags(String name, String descriptor) {
            return fields.get(name + ':' + descriptor);
        }

        /** Target flags for a method, or {@code null}. */
        public Integer methodFlags(String name, String descriptor) {
            return methods.get(name + ':' + descriptor);
        }
    }

    /**
     * A parsed member reference from a MappingEx path such as {@code net.Owner.name(I)V} or
     * {@code net.Owner.field:Z}.
     */
    private record MemberPath(String owner, String name, String descriptor) {

        static MemberPath method(String original, String methodName) {
            int paren = original.indexOf('(');
            if (paren < 0) {
                throw new IllegalArgumentException("method path has no descriptor: " + original);
            }
            int dot = original.lastIndexOf('.', paren);
            if (dot < 0) {
                throw new IllegalArgumentException("method path has no owner: " + original);
            }
            String name = original.substring(dot + 1, paren);
            if (!name.equals(methodName)) {
                throw new IllegalArgumentException("MethodName '" + methodName + "' does not match " + original);
            }
            return new MemberPath(original.substring(0, dot).replace('.', '/'), name, original.substring(paren));
        }

        static MemberPath field(String original) {
            int colon = original.indexOf(':');
            int dot = original.lastIndexOf('.', colon);
            if (dot < 0) {
                throw new IllegalArgumentException("field path has no owner: " + original);
            }
            return new MemberPath(original.substring(0, dot).replace('.', '/'),
                    original.substring(dot + 1, colon), original.substring(colon + 1));
        }

        String key() {
            return name + ':' + descriptor;
        }
    }
}
