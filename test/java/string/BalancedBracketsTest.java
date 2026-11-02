package string;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static string.BalancedBrackets.balanced;
import static string.BalancedBrackets.isBalanced;

class BalancedBracketsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'{[()]}', YES",
            "'{[(])}', NO",
            "'{{[[(())]]}}', YES",
            "'{(([])[])[]}', YES",
            "'{(([])[])[]]}', NO",
            "'{(([])[])[]}[]', YES",
            "'()[]{}', YES",
            "'()', YES",
            "'(', NO",
            "')', NO",
            "'(]', NO",
            "'((', NO",
            "'))((', NO",
            "')(', NO",
            "'(()', NO",
            "'())', NO",
            "'([)]', NO",
            "'[](){}[{}]', YES",
    })
    void decidesBalance(String s, String expected) {
        assertEquals(expected, isBalanced(s), s);
    }

    @Test
    void emptyStringIsBalanced() {
        assertEquals("YES", isBalanced(""));
    }

    @Test
    void siblingPairsAreNotMatchedMirrorWise() {
        assertEquals("YES", isBalanced("()[]"), "'(' must match the next ')', not the last ']'");
    }

    @Test
    void handlesMillionLevelsOfNestingWithoutStackOverflow() {
        String s = "(".repeat(1_000_000) + ")".repeat(1_000_000);
        assertTrue(balanced(s));
        assertEquals("NO", isBalanced(s + "("));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'(a)', YES",
            "'( )', YES",
            "'abc', YES",
            "'f(a[0]) { return x; }', YES",
            "'f(a[0) { }', NO",
            "'a)b(c', NO",
    })
    void ignoresCharactersOtherThanBrackets(String s, String expected) {
        assertEquals(expected, isBalanced(s), s);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'<()>', YES",
            "'<(>)', YES",
            "'（）', YES",
            "'(（)', YES",
            "'😀(😀)😀', YES",
            "'(😀', NO",
            "'(\t[\n]\r)', YES",
            "'\\(\\)', YES",
    })
    void treatsLookalikesAndUnicodeAsText(String s, String expected) {
        assertEquals(expected, isBalanced(s), s);
    }

    @Test
    void nullCharacterIsIgnored() {
        assertEquals("YES", isBalanced("(\0)"));
    }

    @Test
    void everySingleBracketAloneIsUnbalanced() {
        for (String s : new String[]{"(", ")", "[", "]", "{", "}"}) {
            assertEquals("NO", isBalanced(s), s);
        }
    }

    @Test
    void everyMismatchedPairIsUnbalanced() {
        for (String s : new String[]{"(]", "(}", "[)", "[}", "{)", "{]"}) {
            assertEquals("NO", isBalanced(s), s);
        }
    }

    @Test
    void closingFirstFailsEvenWhenCountsMatch() {
        assertEquals("NO", isBalanced("][]["));
        assertEquals("NO", isBalanced("}{"));
    }

    @Test
    void mismatchAtTheVeryBottomOfDeepNesting() {
        String s = "(" + "[".repeat(100_000) + "]".repeat(100_000) + "]";
        assertEquals("NO", isBalanced(s));
    }

    @Test
    void longFlatSequenceKeepsStackSmall() {
        assertEquals("YES", isBalanced("()[]{}".repeat(500_000)));
        assertEquals("NO", isBalanced("()[]{}".repeat(500_000) + "]"));
    }

    @Test
    void unclosedOpeningsAtTheEnd() {
        assertEquals("NO", isBalanced("(".repeat(1_000_000)));
        assertEquals("NO", isBalanced("()".repeat(1000) + "{"));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> isBalanced(null));
    }
}
