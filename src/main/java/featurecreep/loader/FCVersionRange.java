package featurecreep.loader;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version range used by FeatureCreep module metadata (for example
 * {@code FeatureCreep-Target-Game-Version} or a dependency {@code version}
 * attribute).
 *
 * <p>Supported forms:
 * <ul>
 * <li>omitted, empty, {@code *} or {@code any}: matches every version (including
 * a missing version);</li>
 * <li>exact: {@code 1.20.1} or {@code =1.20.1};</li>
 * <li>wildcard: {@code 1.20.*} (also {@code 1.20.x}) matches versions whose leading
 * segments equal the given segments;</li>
 * <li>comparators: {@code >=1.20}, {@code >1.0}, {@code <=2}, {@code <2.0},
 * combined with commas or whitespace ({@code >=1.20,<2.0} or {@code >=1.20 <2.0})
 * where every comparator must hold;</li>
 * <li>Maven-style bracket intervals: {@code [1.20,2.0)}, {@code (,2.0]},
 * {@code [1.0,)}, {@code [1.0]}, optionally several joined with commas
 * ({@code [1.0,2.0),[3.0,4.0)}) where any interval may match.</li>
 * <li>tilde: {@code ~1.2.3} is {@code >=1.2.3 <1.3} and {@code ~1.2} is {@code >=1.2 <1.3};</li>
 * <li>caret: {@code ^1.2.3} is {@code >=1.2.3 <2}; the first non-zero segment is pinned, so
 * {@code ^0.2.3} is {@code >=0.2.3 <0.3} and {@code ^0.0.3} is {@code >=0.0.3 <0.0.4}.</li>
 * <li>alternatives joined with {@code ||}, where any alternative may match:
 * {@code ^1.2 || ^2.0}.</li>
 * </ul>
 *
 * <p>Versions are split into numeric and alphabetic segments at {@code .}, {@code -},
 * {@code _}, {@code +} and at digit/letter boundaries. Numeric segments compare
 * numerically, missing trailing segments count as {@code 0}, and an alphabetic
 * segment (a qualifier such as {@code beta}) sorts before a numeric one, so
 * {@code 1.0-beta} is older than {@code 1.0}.
 *
 * <p>Malformed specifications are rejected with {@link IllegalArgumentException};
 * they are never treated as compatible.
 */
public final class FCVersionRange {

    private static final Pattern SEPARATORS = Pattern.compile("[._+\\-]");
    private static final Pattern SEGMENT = Pattern.compile("\\d+|[A-Za-z]+");
    private static final Pattern VERSION_CHARS = Pattern.compile("[A-Za-z0-9._+\\-]+");
    private static final Pattern BRACKET_INTERVAL = Pattern.compile("([\\[(])([^\\[\\]()]*)([\\])])");
    private static final Pattern BETWEEN_INTERVALS = Pattern.compile("[\\s,]*");
    private static final Pattern COMPARATOR_SPACING = Pattern.compile("(>=|<=|==|>|<|=|~|\\^)\\s+");
    private static final Pattern WHITESPACE_OR_COMMA = Pattern.compile("[,\\s]+");

    private enum Op { EQ, GT, GE, LT, LE, PREFIX }

    /** A single constraint on a version. */
    private static final class Constraint {
        final Op op;
        final Version bound;

        Constraint(Op op, Version bound) {
            this.op = op;
            this.bound = bound;
        }

        boolean matches(Version candidate) {
            switch (op) {
                case EQ: return compare(candidate, bound) == 0;
                case GT: return compare(candidate, bound) > 0;
                case GE: return compare(candidate, bound) >= 0;
                case LT: return compare(candidate, bound) < 0;
                case LE: return compare(candidate, bound) <= 0;
                case PREFIX: return candidate.hasPrefix(bound);
                default: return false;
            }
        }
    }

    /** Conjunction of constraints; an empty clause matches every version. */
    private static final class Clause {
        final List<Constraint> constraints = new ArrayList<Constraint>();

        boolean matches(Version candidate) {
            for (Constraint constraint : constraints) {
                if (!constraint.matches(candidate)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final FCVersionRange ANY = new FCVersionRange("*", Collections.singletonList(new Clause()));

    private final String spec;
    private final List<Clause> clauses;

    private FCVersionRange(String spec, List<Clause> clauses) {
        this.spec = spec;
        this.clauses = clauses;
    }

    /** Range matching every version. */
    public static FCVersionRange any() {
        return ANY;
    }

    /**
     * Parses a range specification.
     *
     * @param spec the specification; {@code null}, empty, {@code *} or {@code any}
     *             mean "any version"
     * @return the parsed range
     * @throws IllegalArgumentException if the specification is malformed
     */
    public static FCVersionRange parse(String spec) {
        if (spec == null) {
            return ANY;
        }
        String trimmed = spec.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed) || "any".equalsIgnoreCase(trimmed)) {
            return ANY;
        }
        return new FCVersionRange(trimmed, parseAlternatives(trimmed));
    }

    /** Original (trimmed) specification text. */
    public String getSpec() {
        return spec;
    }

    /** True when this range accepts every version. */
    public boolean isAny() {
        for (Clause clause : clauses) {
            if (clause.constraints.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests a concrete version against this range.
     *
     * @param version the version to test; {@code null} matches only {@link #isAny()} ranges
     * @return whether the version satisfies the range
     * @throws IllegalArgumentException if {@code version} is not a valid version string
     */
    public boolean matches(String version) {
        if (isAny()) {
            return true;
        }
        if (version == null) {
            return false;
        }
        Version candidate = Version.parse(version);
        for (Clause clause : clauses) {
            if (clause.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compares two version strings using the ordering described in the class
     * documentation.
     *
     * @throws IllegalArgumentException if either argument is not a valid version
     */
    public static int compareVersions(String left, String right) {
        return compare(Version.parse(left), Version.parse(right));
    }

    @Override
    public String toString() {
        return spec;
    }

    private static Clause parseComparators(String text) {
        String normalized = COMPARATOR_SPACING.matcher(text).replaceAll("$1");
        Clause clause = new Clause();
        for (String token : WHITESPACE_OR_COMMA.split(normalized)) {
            if (token.isEmpty()) {
                continue;
            }
            if ("*".equals(token)) {
                continue; // "*" alongside other comparators adds no constraint
            }
            if (token.charAt(0) == '~' || token.charAt(0) == '^') {
                clause.constraints.addAll(parseCompatible(token));
                continue;
            }
            clause.constraints.add(parseComparator(token));
        }
        if (clause.constraints.isEmpty()) {
            throw new IllegalArgumentException("no version constraint in '" + text + "'");
        }
        return clause;
    }

    private static List<Clause> parseAlternatives(String text) {
        List<Clause> clauses = new ArrayList<Clause>();
        for (String alternative : text.split("\\|\\|", -1)) {
            String part = alternative.trim();
            if (part.isEmpty()) {
                throw new IllegalArgumentException("empty alternative in '" + text + "'");
            }
            if ("*".equals(part) || "any".equalsIgnoreCase(part)) {
                clauses.add(new Clause());
            } else if (part.charAt(0) == '[' || part.charAt(0) == '(') {
                clauses.addAll(parseIntervals(part));
            } else {
                clauses.add(parseComparators(part));
            }
        }
        return clauses;
    }

    /**
     * Expands {@code ~V} or {@code ^V} into a lower bound and an exclusive upper bound.
     * Tilde pins the first two segments (or only the first when there is one segment);
     * caret pins up to and including the first non-zero segment.
     */
    private static List<Constraint> parseCompatible(String token) {
        boolean caret = token.charAt(0) == '^';
        String operand = token.substring(1);
        if (operand.endsWith(".x") || operand.endsWith(".X")) {
            throw new IllegalArgumentException("wildcard not allowed with '" + token.charAt(0) + "': '" + token + "'");
        }
        Version base = Version.parse(operand);
        List<Segment> segments = base.segments;
        int size = segments.size();
        int pivotIndex;
        if (caret) {
            pivotIndex = size - 1;
            for (int i = 0; i < size; i++) {
                Segment segment = segments.get(i);
                if (segment.isNumeric() && !segment.number.equals(BigInteger.ZERO)) {
                    pivotIndex = i;
                    break;
                }
            }
        } else {
            pivotIndex = Math.min(1, size - 1);
        }
        Segment pivot = segments.get(pivotIndex);
        if (!pivot.isNumeric()) {
            throw new IllegalArgumentException("cannot apply '" + token + "' to a qualifier");
        }
        List<Segment> upper = new ArrayList<Segment>(segments.subList(0, pivotIndex));
        upper.add(new Segment(pivot.number.add(BigInteger.ONE), null));
        List<Constraint> constraints = new ArrayList<Constraint>(2);
        constraints.add(new Constraint(Op.GE, base));
        constraints.add(new Constraint(Op.LT, new Version(upper)));
        return constraints;
    }

    private static Constraint parseComparator(String token) {
        Op op;
        String operand;
        if (token.startsWith(">=")) {
            op = Op.GE; operand = token.substring(2);
        } else if (token.startsWith("<=")) {
            op = Op.LE; operand = token.substring(2);
        } else if (token.startsWith("==")) {
            op = Op.EQ; operand = token.substring(2);
        } else if (token.startsWith(">")) {
            op = Op.GT; operand = token.substring(1);
        } else if (token.startsWith("<")) {
            op = Op.LT; operand = token.substring(1);
        } else if (token.startsWith("=")) {
            op = Op.EQ; operand = token.substring(1);
        } else {
            op = Op.EQ; operand = token;
        }
        if (operand.endsWith(".*") || operand.endsWith(".x") || operand.endsWith(".X")) {
            if (op != Op.EQ) {
                throw new IllegalArgumentException("wildcard only allowed without a comparator: '" + token + "'");
            }
            return new Constraint(Op.PREFIX, Version.parse(operand.substring(0, operand.length() - 2)));
        }
        return new Constraint(op, Version.parse(operand));
    }

    private static List<Clause> parseIntervals(String text) {
        List<Clause> clauses = new ArrayList<Clause>();
        Matcher matcher = BRACKET_INTERVAL.matcher(text);
        int position = 0;
        while (matcher.find()) {
            String gap = text.substring(position, matcher.start());
            if (!BETWEEN_INTERVALS.matcher(gap).matches()) {
                throw new IllegalArgumentException("unexpected text '" + gap.trim() + "' in range '" + text + "'");
            }
            clauses.add(parseInterval(matcher.group(1), matcher.group(2), matcher.group(3), text));
            position = matcher.end();
        }
        String tail = text.substring(position);
        if (clauses.isEmpty() || !BETWEEN_INTERVALS.matcher(tail).matches()) {
            throw new IllegalArgumentException("malformed interval range '" + text + "'");
        }
        return clauses;
    }

    private static Clause parseInterval(String open, String body, String close, String text) {
        Clause clause = new Clause();
        int comma = body.indexOf(',');
        if (comma < 0) {
            if (!"[".equals(open) || !"]".equals(close)) {
                throw new IllegalArgumentException("single version must use [ ] in '" + text + "'");
            }
            String exact = body.trim();
            if (exact.isEmpty()) {
                throw new IllegalArgumentException("empty version in '" + text + "'");
            }
            clause.constraints.add(new Constraint(Op.EQ, Version.parse(exact)));
            return clause;
        }
        String lowerText = body.substring(0, comma).trim();
        String upperText = body.substring(comma + 1).trim();
        Version lower = lowerText.isEmpty() ? null : Version.parse(lowerText);
        Version upper = upperText.isEmpty() ? null : Version.parse(upperText);
        if (lower != null) {
            clause.constraints.add(new Constraint("[".equals(open) ? Op.GE : Op.GT, lower));
        }
        if (upper != null) {
            clause.constraints.add(new Constraint("]".equals(close) ? Op.LE : Op.LT, upper));
        }
        if (lower != null && upper != null && compare(lower, upper) > 0) {
            throw new IllegalArgumentException("lower bound above upper bound in '" + text + "'");
        }
        return clause;
    }

    private static int compare(Version left, Version right) {
        int size = Math.max(left.segments.size(), right.segments.size());
        for (int i = 0; i < size; i++) {
            Segment a = i < left.segments.size() ? left.segments.get(i) : Segment.ZERO;
            Segment b = i < right.segments.size() ? right.segments.get(i) : Segment.ZERO;
            int result = a.compareTo(b);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static final class Segment implements Comparable<Segment> {
        static final Segment ZERO = new Segment(BigInteger.ZERO, null);

        final BigInteger number;
        final String text;

        Segment(BigInteger number, String text) {
            this.number = number;
            this.text = text;
        }

        boolean isNumeric() {
            return number != null;
        }

        @Override
        public int compareTo(Segment other) {
            if (isNumeric() && other.isNumeric()) {
                return number.compareTo(other.number);
            }
            if (!isNumeric() && !other.isNumeric()) {
                return text.compareTo(other.text);
            }
            // Qualifiers (alphabetic) sort before release numbers.
            return isNumeric() ? 1 : -1;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Segment && compareTo((Segment) obj) == 0;
        }

        @Override
        public int hashCode() {
            return isNumeric() ? number.hashCode() : text.hashCode();
        }
    }

    private static final class Version {
        final List<Segment> segments;

        private Version(List<Segment> segments) {
            this.segments = segments;
        }

        static Version parse(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("version is null");
            }
            String value = raw.trim();
            if (value.isEmpty() || !VERSION_CHARS.matcher(value).matches()) {
                throw new IllegalArgumentException("invalid version '" + raw + "'");
            }
            if (!Character.isDigit(value.charAt(0))) {
                throw new IllegalArgumentException("version must start with a number: '" + raw + "'");
            }
            List<Segment> segments = new ArrayList<Segment>();
            for (String part : SEPARATORS.split(value, -1)) {
                if (part.isEmpty()) {
                    throw new IllegalArgumentException("empty version segment in '" + raw + "'");
                }
                Matcher matcher = SEGMENT.matcher(part);
                while (matcher.find()) {
                    String token = matcher.group();
                    if (Character.isDigit(token.charAt(0))) {
                        segments.add(new Segment(new BigInteger(token), null));
                    } else {
                        segments.add(new Segment(null, token.toLowerCase(Locale.ROOT)));
                    }
                }
            }
            return new Version(segments);
        }

        /** True when the leading segments of {@code prefix} equal this version's leading segments. */
        boolean hasPrefix(Version prefix) {
            if (segments.size() < prefix.segments.size()) {
                return false;
            }
            for (int i = 0; i < prefix.segments.size(); i++) {
                if (!segments.get(i).equals(prefix.segments.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
