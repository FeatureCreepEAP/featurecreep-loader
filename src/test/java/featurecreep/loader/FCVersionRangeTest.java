package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class FCVersionRangeTest {

    @Test
    void omittedAndAnyValuesMatchEverything() {
        for (String spec : new String[] { null, "", "   ", "*", "any", "ANY" }) {
            FCVersionRange range = FCVersionRange.parse(spec);
            assertTrue(range.isAny(), spec);
            assertTrue(range.matches("0.1"), spec);
            assertTrue(range.matches(null), "any range accepts a missing version: " + spec);
        }
    }

    @Test
    void exactVersionsCompareSegmentwiseWithZeroPadding() {
        FCVersionRange range = FCVersionRange.parse("1.20.1");
        assertTrue(range.matches("1.20.1"));
        assertTrue(range.matches("1.20.1.0"));
        assertFalse(range.matches("1.20"));
        assertFalse(range.matches("1.20.2"));

        assertTrue(FCVersionRange.parse("=2.0").matches("2"));
        assertTrue(FCVersionRange.parse("==2.0").matches("2.0.0"));
    }

    @Test
    void wildcardMatchesLeadingSegmentsOnly() {
        FCVersionRange range = FCVersionRange.parse("1.20.*");
        assertTrue(range.matches("1.20"));
        assertTrue(range.matches("1.20.4"));
        assertFalse(range.matches("1.2"));
        assertFalse(range.matches("1.21.0"));

        assertTrue(FCVersionRange.parse("1.x").matches("1.99.3"));
        assertFalse(FCVersionRange.parse("1.x").matches("2.0"));
    }

    @Test
    void comparatorsAndCombinedConstraints() {
        assertTrue(FCVersionRange.parse(">=1.20").matches("1.20.1"));
        assertFalse(FCVersionRange.parse(">=1.20").matches("1.19.9"));
        assertFalse(FCVersionRange.parse(">1.20").matches("1.20"));
        assertTrue(FCVersionRange.parse("<=2").matches("2.0.0"));
        assertFalse(FCVersionRange.parse("<2.0").matches("2.0"));

        FCVersionRange combined = FCVersionRange.parse(">=1.20, <2.0");
        assertTrue(combined.matches("1.21"));
        assertFalse(combined.matches("2.0"));
        assertFalse(combined.matches("1.19"));

        assertTrue(FCVersionRange.parse(">= 1.20 <2.0").matches("1.20"));
    }

    @Test
    void bracketIntervalsFollowMavenInclusivityRules() {
        FCVersionRange range = FCVersionRange.parse("[1.20,2.0)");
        assertTrue(range.matches("1.20"));
        assertTrue(range.matches("1.99.9"));
        assertFalse(range.matches("2.0"));
        assertFalse(range.matches("1.19.9"));

        assertTrue(FCVersionRange.parse("(,2.0]").matches("0.1"));
        assertTrue(FCVersionRange.parse("(,2.0]").matches("2.0"));
        assertFalse(FCVersionRange.parse("(,2.0]").matches("2.0.1"));
        assertFalse(FCVersionRange.parse("(1.0,)").matches("1.0"));
        assertTrue(FCVersionRange.parse("[1.0,)").matches("1.0"));
        assertTrue(FCVersionRange.parse("[1.0]").matches("1.0.0"));
        assertFalse(FCVersionRange.parse("[1.0]").matches("1.0.1"));
    }

    @Test
    void multipleBracketIntervalsAreUnioned() {
        FCVersionRange range = FCVersionRange.parse("[1.0,2.0),[3.0,4.0)");
        assertTrue(range.matches("1.5"));
        assertTrue(range.matches("3.5"));
        assertFalse(range.matches("2.5"));
    }

    @Test
    void qualifiersSortBeforeReleases() {
        assertTrue(FCVersionRange.parse("<1.0").matches("1.0-beta"));
        assertFalse(FCVersionRange.parse(">=1.0").matches("1.0-beta"));
        assertTrue(FCVersionRange.parse(">=1.0-alpha").matches("1.0"));
        assertTrue(FCVersionRange.parse("1.0-SNAPSHOT").matches("1.0-snapshot"));
        assertTrue(FCVersionRange.compareVersions("1.10", "1.9") > 0);
    }

    @Test
    void malformedSpecificationsAreRejected() {
        String[] malformed = {
                "[1.0,2.0",          // unclosed interval
                "[2.0,1.0]",         // lower above upper
                "(1.0)",             // exclusive single version
                "[]",                // empty single version
                ">=",                // missing operand
                ">=1.*",             // wildcard with comparator
                "!=1.0",             // unsupported operator
                "abc",               // not a version
                "[abc,2.0)",         // invalid bound
                "[1.0,2.0) junk",    // trailing garbage
                ">=1..2",            // empty segment
        };
        for (String spec : malformed) {
            assertThrows(IllegalArgumentException.class, () -> FCVersionRange.parse(spec), spec);
        }
    }

    @Test
    void invalidCandidateVersionIsReportedNotSilentlyMatched() {
        FCVersionRange range = FCVersionRange.parse(">=1.0");
        Executable call = () -> range.matches("not a version");
        assertThrows(IllegalArgumentException.class, call);
    }

    @Test
    void nonAnyRangeRejectsMissingVersion() {
        assertFalse(FCVersionRange.parse(">=1.0").matches(null));
    }

    @Test
    void tildeAllowsPatchLevelChangesOnly() {
        FCVersionRange range = FCVersionRange.parse("~1.2.3");
        assertTrue(range.matches("1.2.3"));
        assertTrue(range.matches("1.2.9"));
        assertFalse(range.matches("1.2.2"));
        assertFalse(range.matches("1.3.0"));

        FCVersionRange shortForm = FCVersionRange.parse("~1.2");
        assertTrue(shortForm.matches("1.2"));
        assertTrue(shortForm.matches("1.2.7"));
        assertFalse(shortForm.matches("1.3"));

        assertFalse(FCVersionRange.parse("~ 1.2.3").matches("1.3"));
        assertTrue(FCVersionRange.parse("~1").matches("1.9.9"));
        assertFalse(FCVersionRange.parse("~1").matches("2.0"));
    }

    @Test
    void caretPinsFirstNonZeroSegment() {
        FCVersionRange major = FCVersionRange.parse("^1.2.3");
        assertTrue(major.matches("1.2.3"));
        assertTrue(major.matches("1.99.0"));
        assertFalse(major.matches("1.2.2"));
        assertFalse(major.matches("2.0.0"));

        FCVersionRange minor = FCVersionRange.parse("^0.2.3");
        assertTrue(minor.matches("0.2.3"));
        assertTrue(minor.matches("0.2.9"));
        assertFalse(minor.matches("0.2.2"));
        assertFalse(minor.matches("0.3.0"));

        FCVersionRange patch = FCVersionRange.parse("^0.0.3");
        assertTrue(patch.matches("0.0.3"));
        assertFalse(patch.matches("0.0.4"));
        assertFalse(patch.matches("0.0.2"));
    }

    @Test
    void alternativesAreUnioned() {
        FCVersionRange range = FCVersionRange.parse("^1.2 || ^2.0");
        assertTrue(range.matches("1.9"));
        assertTrue(range.matches("2.4"));
        assertFalse(range.matches("3.0"));
        assertFalse(range.matches("1.1"));

        FCVersionRange mixed = FCVersionRange.parse("[1.0,1.5) || >=3");
        assertTrue(mixed.matches("1.2"));
        assertTrue(mixed.matches("3.1"));
        assertFalse(mixed.matches("2.0"));

        assertTrue(FCVersionRange.parse("1.0 || *").isAny());
        assertTrue(FCVersionRange.parse("1.0 || *").matches(null));
    }

    @Test
    void compatibilityOperatorsRejectMalformedOperands() {
        String[] malformed = { "~", "^", "~1.x", "^1.X", "~abc", "^0.0-beta", "~1-beta", "~>=1" };
        for (String spec : malformed) {
            assertThrows(IllegalArgumentException.class, () -> FCVersionRange.parse(spec), spec);
        }
    }

    @Test
    void emptyAlternativesAreRejected() {
        String[] malformed = { "1.0 ||", "|| 1.0", "1.0 || || 2.0", "1.0 | 2.0" };
        for (String spec : malformed) {
            assertThrows(IllegalArgumentException.class, () -> FCVersionRange.parse(spec), spec);
        }
    }
}
