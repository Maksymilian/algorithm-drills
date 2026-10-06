package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayComparisonTest {

    static Stream<Arguments> oneDimensionalPairs() {
        int[] shared = {1, 2, 3};
        return Stream.of(
                Arguments.of("equal contents, distinct arrays", new int[]{1, 2, 3},      new int[]{1, 2, 3},      false, true),
                Arguments.of("the very same array twice",       shared,                 shared,                  true,  true),
                Arguments.of("different contents",              new int[]{1, 2, 3},     new int[]{1, 9, 3},      false, false),
                Arguments.of("different lengths",               new int[]{1, 2},        new int[]{1, 2, 3},      false, false),
                Arguments.of("two empty arrays",                new int[0],             new int[0],              false, true),
                Arguments.of("{NaN} vs {NaN}",                  new double[]{Double.NaN}, new double[]{Double.NaN}, false, true),
                Arguments.of("{0.0} vs {-0.0}",                 new double[]{0.0},      new double[]{-0.0},      false, false),
                Arguments.of("{0.0f} vs {-0.0f}",               new float[]{0.0f},      new float[]{-0.0f},      false, false),
                Arguments.of("boxed elements, equal values",    new Double[]{1.5},      new Double[]{1.5},       false, true),
                Arguments.of("strings, equal values",           new String[]{"a", "b"}, new String[]{"a", "b"},  false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneDimensionalPairs")
    void referenceIdentityAndContentsAreDifferentQuestions(
            String label, Object a, Object b, boolean sameReference, boolean contentsEqual) {
        assertEquals(sameReference, a.equals(b), label + ": reference comparison");
        assertEquals(contentsEqual, contentsEqual(a, b), label + ": Arrays.equals");
    }

    private static boolean contentsEqual(Object a, Object b) {
        return switch (a) {
            case int[] ints -> Arrays.equals(ints, (int[]) b);
            case double[] doubles -> Arrays.equals(doubles, (double[]) b);
            case float[] floats -> Arrays.equals(floats, (float[]) b);
            case Object[] objects -> Arrays.equals(objects, (Object[]) b);
            default -> throw new IllegalArgumentException("unsupported: " + a.getClass());
        };
    }

    @Test
    void everyReferenceFlavourOfEqualityAgreesWithEachOtherAndNotWithContents() {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};

        assertFalse(a == b, "distinct arrays are never == however equal their contents");
        assertFalse(a.equals(b), "distinct arrays are never == however equal their contents");
        assertFalse(a.equals(b), "an array inherits Object.equals, which is ==");
        assertFalse(Objects.equals(a, b), "Objects.equals delegates to Object.equals: still identity");
        assertTrue(Arrays.equals(a, b), "only Arrays.equals looks at the contents");

        assertSame(a, a);
        assertTrue(a.equals(a) && Objects.equals(a, a) && Arrays.equals(a, a), "all agree on one array");
    }

    @Test
    void floatingPointContentsInvertWhatComparisonSays() {
        assertFalse(Double.NaN == Double.NaN);
        assertFalse(Float.NaN == Float.NaN);
        assertTrue(Arrays.equals(new double[]{Double.NaN}, new double[]{Double.NaN}));

        assertTrue(0.0 == -0.0);
        assertTrue(0l == -0l);
        assertTrue(Arrays.equals(new double[]{0.0}, new double[]{0.0}));
        assertFalse(Arrays.equals(new double[]{0.0}, new double[]{-0.0}));
        assertFalse(Arrays.equals(new float[]{0.0f}, new float[]{-0.0f}));
    }

    @Test
    void boxedElementsAreComparedByValueNotByReference() {
        Double[] a = {1.5};
        Double[] b = {1.5};
        assertNotSame(a[0], b[0], "the elements are distinct objects");
        assertTrue(Arrays.equals(a, b), "Arrays.equals compares elements with equals(), not ==");
    }

    @Test
    void arraysEqualsIsOnlyOneLevelDeep() {
        int[][] a = {{1, 2}, {3}};
        int[][] b = {{1, 2}, {3}};

        assertFalse(Arrays.equals(a, b), "the rows are distinct objects, so shallow equality fails");
        assertTrue(Arrays.deepEquals(a, b), "deepEquals recurses into the rows");

        Object[] wrapping = {new int[]{1, 2}};
        Object[] wrappingToo = {new int[]{1, 2}};
        assertFalse(Arrays.equals(wrapping, wrappingToo));
        assertTrue(Arrays.deepEquals(wrapping, wrappingToo), "deepEquals unwraps nested primitives too");
    }

    @Test
    void deepEqualsGoesAllTheWayDown() {
        Object[] a = {new Object[]{new int[]{1}, "x"}, 2};
        Object[] b = {new Object[]{new int[]{1}, "x"}, 2};
        assertTrue(Arrays.deepEquals(a, b));

        Object[] differsAtTheBottom = {new Object[]{new int[]{9}, "x"}, 2};
        assertFalse(Arrays.deepEquals(a, differsAtTheBottom), "one leaf is enough to fail");
    }

    @Test
    void deepEqualsAgreesWithArraysEqualsWhenThereIsNothingToRecurseInto() {
        String[] a = {"a", "b"};
        String[] b = {"a", "b"};
        assertTrue(Arrays.equals(a, b));
        assertTrue(Arrays.deepEquals(a, b));
    }

    @Test
    void nullsAreComparedPerOverloadNotDereferenced() {
        assertTrue(Arrays.equals((int[]) null, (int[]) null), "two absent arrays are equal");
        assertFalse(Arrays.equals(null, new int[]{1}), "an absent array equals nothing present");
        assertFalse(Arrays.equals(new int[]{1}, null));
        assertTrue(Arrays.deepEquals(null, null));
        assertFalse(Arrays.deepEquals(null, new Object[]{1}));

        assertTrue(Objects.equals(null, null), "Objects.equals is the same as == for nulls");
        assertTrue(Objects.deepEquals(null, null));
    }

    @Test
    void nullElementsAreFineInsideTheArray() {
        Object[] a = {null, "a"};
        Object[] b = {null, "a"};
        assertTrue(Arrays.equals(a, b), "a null element equals a null element");
        assertTrue(Arrays.deepEquals(a, b));
        assertFalse(Arrays.equals(a, new Object[]{"a", "a"}));

        Object[][] nested = {{null}};
        Object[][] nestedToo = {{null}};
        assertTrue(Arrays.deepEquals(nested, nestedToo), "and the same one level down");
    }

    @Test
    void rangedEqualsComparesOnlyTheWindow() {
        int[] a = {9, 1, 2, 3, 9};
        int[] b = {7, 1, 2, 3, 7};

        assertFalse(Arrays.equals(a, b), "the ends differ");
        assertTrue(Arrays.equals(a, 1, 4, b, 1, 4), "but [1, 4) is the same in both");
        assertFalse(Arrays.equals(a, 0, 4, b, 0, 4), "widen it by one and the answer flips");

        assertFalse(Arrays.equals(a, 1, 4, b, 1, 3));
        assertTrue(Arrays.equals(a, 2, 2, b, 4, 4), "two empty windows are equal");
    }
}
