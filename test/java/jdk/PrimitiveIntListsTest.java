package jdk;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveIntListsTest {

    @Test
    void allThreeHoldTheSameValuesAndReadThemBackUnboxed() {
        int[] array = {3, 1, 2};
        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(3, 1, 2);
        MutableIntList eclipse = IntArrayList.newListWith(3, 1, 2);

        for (int i = 0; i < array.length; i++) {
            assertEquals(array[i], fast.getInt(i));
            assertEquals(array[i], eclipse.get(i));
        }
        assertArrayEquals(array, fast.toIntArray());
        assertArrayEquals(array, eclipse.toArray());
    }

    @Test
    void anArrayListOfIntegerHoldsObjectsAndTheirIdentityLeaks() {
        List<Integer> boxed = new ArrayList<>(List.of(1000));
        boxed.add(1000);
        assertNotSame(boxed.get(0), boxed.get(1), "two boxes of the same value");
        assertTrue(boxed.get(0).equals(boxed.get(1)));

        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(1000, 1000);
        assertTrue(fast.getInt(0) == fast.getInt(1));
        MutableIntList eclipse = IntArrayList.newListWith(1000, 1000);
        assertTrue(eclipse.get(0) == eclipse.get(1));
    }

    @Test
    @SuppressWarnings("deprecation")
    void fastutilStillOffersTheBoxedViewEclipseDoesNot() {
        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(1000);
        Integer boxed = fast.get(0);
        assertNotSame(boxed, fast.get(0), "each call boxes again");
        assertTrue(fast instanceof List<?>, "fastutil lists are java.util.Lists");
        assertFalse(((Object) IntArrayList.newListWith(1000)) instanceof List<?>,
                "Eclipse primitive lists are not, so there is no boxed get to call by accident");
    }

    @Test
    void anArrayHasAFixedLengthAndGrowsOnlyByCopying() {
        int[] array = {1, 2};
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> { int[] a = array; a[2] = 3; });

        int[] grown = Arrays.copyOf(array, 3);
        grown[2] = 3;
        assertNotSame(array, grown, "growing means a new array; anything holding the old one is stale");
        assertEquals(2, array.length);
        assertArrayEquals(new int[]{1, 2, 3}, grown);
    }

    @Test
    void bothListsGrowTheirBackingArrayForYou() {
        it.unimi.dsi.fastutil.ints.IntArrayList fast = new it.unimi.dsi.fastutil.ints.IntArrayList(2);
        MutableIntList eclipse = new IntArrayList(2);
        for (int i = 0; i < 1_000; i++) {
            fast.add(i);
            eclipse.add(i);
        }
        assertEquals(1_000, fast.size());
        assertEquals(1_000, eclipse.size());
        assertEquals(999, fast.getInt(999));
        assertEquals(999, eclipse.get(999));
    }

    @Test
    void fastutilExposesItsBackingArrayIncludingTheUnusedTail() {
        it.unimi.dsi.fastutil.ints.IntArrayList fast = new it.unimi.dsi.fastutil.ints.IntArrayList(16);
        fast.add(7);
        int[] backing = fast.elements();
        assertEquals(16, backing.length, "elements() is the whole array, capacity and all");
        assertEquals(1, fast.size(), "only size() of it is the list");
        assertEquals(7, backing[0]);

        backing[0] = 8;
        assertEquals(8, fast.getInt(0), "it is not a copy: writing to it writes to the list");

        fast.trim();
        assertEquals(1, fast.elements().length, "trim() shrinks the array to the size");
    }

    @Test
    void fastutilWrapSharesTheArrayUntilItHasToGrow() {
        int[] array = {1, 2, 3};
        it.unimi.dsi.fastutil.ints.IntArrayList wrapped = it.unimi.dsi.fastutil.ints.IntArrayList.wrap(array);
        assertSame(array, wrapped.elements(), "wrap does not copy");

        array[0] = 10;
        assertEquals(10, wrapped.getInt(0), "a write to the array shows in the list");
        wrapped.set(1, 20);
        assertEquals(20, array[1], "and a write to the list shows in the array");

        wrapped.add(4);
        assertNotSame(array, wrapped.elements(), "the array was full, so add moved the list to a new one");
        wrapped.set(2, 30);
        assertEquals(3, array[2], "from here on the two are independent");
    }

    @Test
    void fastutilsConstructorCopies() {
        int[] array = {1, 2, 3};
        it.unimi.dsi.fastutil.ints.IntArrayList fast = new it.unimi.dsi.fastutil.ints.IntArrayList(array);
        array[0] = 10;
        assertEquals(1, fast.getInt(0), "new IntArrayList(int[]) copies");
    }

    @Test
    void eclipsesVarargsFactoriesAdoptTheArrayTheyAreGiven() {
        int[] array = {1, 2, 3};
        MutableIntList newListWith = IntArrayList.newListWith(array);
        MutableIntList constructed = new IntArrayList(array);
        MutableIntList viaFactory = org.eclipse.collections.api.factory.primitive.IntLists.mutable.with(array);
        array[0] = 10;

        assertEquals(10, newListWith.get(0), "IntArrayList.newListWith(int...) does not copy");
        assertEquals(10, constructed.get(0), "new IntArrayList(int...) does not copy");
        assertEquals(10, viaFactory.get(0), "IntLists.mutable.with(int...) does not copy");

        newListWith.add(4);
        array[1] = 20;
        assertEquals(2, newListWith.get(1), "add reallocated, so the list no longer sees the array");
        assertEquals(20, constructed.get(1), "the others have not grown and still do");
    }

    @Test
    void eclipseCopiesWhenItStartsFromAnotherListOrBuildsAnImmutableOne() {
        int[] array = {1, 2, 3};
        MutableIntList copied = IntArrayList.newList(IntArrayList.newListWith(array));
        ImmutableIntList immutable = org.eclipse.collections.api.factory.primitive.IntLists.immutable.of(array);
        array[0] = 10;

        assertEquals(1, copied.get(0), "newList(IntIterable) copies");
        assertEquals(1, immutable.get(0), "an immutable list cannot afford to share, so it copies");
    }

    @Test
    void toArrayIsAlwaysACopy() {
        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2);
        MutableIntList eclipse = IntArrayList.newListWith(1, 2);

        fast.toIntArray()[0] = 10;
        eclipse.toArray()[0] = 10;
        assertEquals(1, fast.getInt(0));
        assertEquals(1, eclipse.get(0));
    }

    @Test
    void arraysCompareByIdentityAndTheListsByContents() {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        assertFalse(a.equals(b), "an array inherits Object.equals");
        assertTrue(Arrays.equals(a, b));

        assertEquals(it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2, 3), it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2, 3));
        assertEquals(IntArrayList.newListWith(1, 2, 3), IntArrayList.newListWith(1, 2, 3));
    }

    @Test
    void onlyFastutilIsEqualToAnArrayListOfTheSameValues() {
        List<Integer> boxed = List.of(1, 2, 3);
        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2, 3);
        MutableIntList eclipse = IntArrayList.newListWith(1, 2, 3);

        assertEquals(boxed, fast);
        assertEquals(fast, boxed, "symmetric");
        assertEquals(boxed.hashCode(), fast.hashCode());

        assertNotEquals(boxed, eclipse);
        assertNotEquals(eclipse, boxed);
        assertEquals(boxed.hashCode(), eclipse.hashCode(),
                "the hash is computed the List way all the same, so it is only equals that refuses");
    }

    @Test
    void theTwoLibrariesAreNeverEqualToEachOther() {
        assertNotEquals(it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2, 3), IntArrayList.newListWith(1, 2, 3));
        assertNotEquals(IntArrayList.newListWith(1, 2, 3), it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2, 3));
    }

    @Test
    void removalByIndexAndByValueHaveDifferentNames() {
        List<Integer> boxed = new ArrayList<>(List.of(5, 1, 7));
        boxed.remove(1);
        assertEquals(List.of(5, 7), boxed, "removed the element at index 1, not the value 1");

        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(5, 1, 7);
        fast.removeInt(1);
        assertEquals(it.unimi.dsi.fastutil.ints.IntArrayList.of(5, 7), fast, "removeInt is by index");
        fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(5, 1, 7);
        assertTrue(fast.rem(7));
        assertEquals(it.unimi.dsi.fastutil.ints.IntArrayList.of(5, 1), fast, "rem is by value");

        MutableIntList eclipse = IntArrayList.newListWith(5, 1, 7);
        eclipse.removeAtIndex(1);
        assertEquals(IntArrayList.newListWith(5, 7), eclipse, "removeAtIndex is by index");
        eclipse = IntArrayList.newListWith(5, 1, 7);
        assertTrue(eclipse.remove(7));
        assertEquals(IntArrayList.newListWith(5, 1), eclipse, "remove is by value");
    }

    @Test
    void readingPastTheSizeFailsEvenWhenTheArrayIsLongerThanThat() {
        it.unimi.dsi.fastutil.ints.IntArrayList fast = new it.unimi.dsi.fastutil.ints.IntArrayList(16);
        fast.add(1);
        assertTrue(fast.elements().length > 1, "index 1 is inside the backing array...");
        assertThrows(IndexOutOfBoundsException.class, () -> fast.getInt(1), "...but outside the list");

        MutableIntList eclipse = new IntArrayList(16);
        eclipse.add(1);
        assertThrows(IndexOutOfBoundsException.class, () -> eclipse.get(1));

        int[] array = new int[16];
        assertEquals(0, array[1], "an array has no size apart from its length: unused slots read as 0");
    }

    @Test
    void sumOverflowsInAnIntStreamButNotInEclipse() {
        int[] array = {Integer.MAX_VALUE, 1};
        long exact = (long) Integer.MAX_VALUE + 1;

        assertEquals(Integer.MIN_VALUE, Arrays.stream(array).sum(), "int, so it wraps");
        assertEquals(exact, Arrays.stream(array).asLongStream().sum(), "widen first to get it right");

        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.wrap(array);
        assertEquals(Integer.MIN_VALUE, fast.intStream().sum(), "fastutil has no sum of its own; its stream wraps too");

        assertEquals(exact, IntArrayList.newListWith(array).sum(), "Eclipse sums into a long");
    }

    @Test
    void eclipseCarriesTheAggregatesOnTheListItself() {
        MutableIntList eclipse = IntArrayList.newListWith(4, 1, 3, 2);
        assertEquals(1, eclipse.min());
        assertEquals(4, eclipse.max());
        assertEquals(2.5, eclipse.average());
        assertEquals(2.5, eclipse.median());
        assertEquals(2, eclipse.count(v -> v % 2 == 0));
        assertEquals(IntArrayList.newListWith(4, 2), eclipse.select(v -> v % 2 == 0), "select stays primitive");
    }

    @Test
    void onlyFastutilSortsPrimitivesWithAComparator() {
        int[] array = {3, 1, 2};
        Arrays.sort(array);
        assertArrayEquals(new int[]{1, 2, 3}, array);

        int[] descending = {3, 1, 2};
        IntArrays.quickSort(descending, IntComparators.OPPOSITE_COMPARATOR);
        assertArrayEquals(new int[]{3, 2, 1}, descending);

        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(3, 1, 2);
        fast.sort(IntComparators.OPPOSITE_COMPARATOR);
        assertEquals(it.unimi.dsi.fastutil.ints.IntArrayList.of(3, 2, 1), fast);

        MutableIntList eclipse = IntArrayList.newListWith(3, 1, 2);
        eclipse.sortThis();
        assertEquals(IntArrayList.newListWith(1, 2, 3), eclipse);
        eclipse.reverseThis();
        assertEquals(IntArrayList.newListWith(3, 2, 1), eclipse, "Eclipse sorts ascending and reverses");
    }

    @Test
    void binarySearchAgreesAcrossAllThree() {
        int[] array = {1, 3, 5, 7};
        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.wrap(array);
        MutableIntList eclipse = IntArrayList.newListWith(array);

        for (int key : new int[]{1, 5, 7, 0, 4, 8}) {
            int expected = Arrays.binarySearch(array, key);
            assertEquals(expected, IntArrays.binarySearch(fast.elements(), 0, fast.size(), key), "key " + key);
            assertEquals(expected, eclipse.binarySearch(key), "key " + key);
        }
    }

    @Test
    void readOnlyIsAViewInFastutilAndACopyInEclipse() {
        it.unimi.dsi.fastutil.ints.IntArrayList fast = it.unimi.dsi.fastutil.ints.IntArrayList.of(1, 2);
        IntList view = IntLists.unmodifiable(fast);
        assertThrows(UnsupportedOperationException.class, () -> view.add(3));
        fast.add(3);
        assertEquals(3, view.size(), "an unmodifiable view still sees the list change underneath it");

        MutableIntList eclipse = IntArrayList.newListWith(1, 2);
        ImmutableIntList frozen = eclipse.toImmutable();
        eclipse.add(3);
        assertEquals(2, frozen.size(), "toImmutable copies, so later writes do not reach it");
        assertEquals(IntArrayList.newListWith(1, 2, 9), frozen.newWith(9), "changing it gives a new list");
        assertEquals(2, frozen.size());

        int[] array = {1, 2};
        assertNotSame(array, array.clone());
    }
}
