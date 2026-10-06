package jdk;

import org.eclipse.collections.api.list.primitive.IntList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jmh.annotations.Benchmark;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveIntListsBenchmarkTest {

    private static final int[] SIZES = {1, 2, 1_000, 2_500};

    private static final List<String> CONTAINERS = List.of("array", "fastutil", "eclipse", "boxed");

    static Stream<Arguments> operationsAndSizes() {
        return operations().keySet().stream()
                .flatMap(operation -> Arrays.stream(SIZES).mapToObj(size -> Arguments.of(operation, size)));
    }

    static Stream<String> variants() {
        return operations().values().stream().flatMap(List::stream).map(Method::getName);
    }

    @ParameterizedTest(name = "{0}, size {1}")
    @MethodSource("operationsAndSizes")
    void everyVariantOfAnOperationGivesTheSameAnswer(String operation, int size) throws Exception {
        PrimitiveIntListsBenchmark benchmark = benchmark(size);
        List<Method> variants = operations().get(operation);

        Method reference = variants.getFirst();
        Object expected = normalise(reference.invoke(benchmark), size);
        for (Method variant : variants.subList(1, variants.size())) {
            assertEquals(expected, normalise(variant.invoke(benchmark), size),
                    variant.getName() + " disagrees with " + reference.getName());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("variants")
    void noVariantChangesTheStateTheNextInvocationReads(String variant) throws Exception {
        PrimitiveIntListsBenchmark benchmark = benchmark(2_500);
        int[] array = benchmark.array.clone();
        int[] indices = benchmark.indices.clone();
        int[] fastutil = benchmark.fastutil.toIntArray();
        int[] eclipse = benchmark.eclipse.toArray();
        List<Integer> boxed = List.copyOf(benchmark.boxed);

        PrimitiveIntListsBenchmark.class.getMethod(variant).invoke(benchmark);

        assertArrayEquals(array, benchmark.array);
        assertArrayEquals(indices, benchmark.indices);
        assertArrayEquals(fastutil, benchmark.fastutil.toIntArray());
        assertArrayEquals(eclipse, benchmark.eclipse.toArray());
        assertEquals(boxed, benchmark.boxed);
    }

    @Test
    void everyOperationIsMeasuredOnAllFourContainers() {
        operations().forEach((operation, variants) -> {
            for (String container : CONTAINERS) {
                assertTrue(variants.stream().anyMatch(m -> m.getName().startsWith(operation + "_" + container)),
                        operation + " has no " + container + " variant");
            }
        });
    }

    @Test
    void allFourContainersStartWithTheSameValues() {
        PrimitiveIntListsBenchmark benchmark = benchmark(1_000);
        assertArrayEquals(benchmark.array, benchmark.fastutil.toIntArray());
        assertArrayEquals(benchmark.array, benchmark.eclipse.toArray());
        assertArrayEquals(benchmark.array, benchmark.boxed.stream().mapToInt(Integer::intValue).toArray());
    }

    @Test
    void theValuesLieInZeroToSize() {
        PrimitiveIntListsBenchmark benchmark = benchmark(2_500);
        assertTrue(Arrays.stream(benchmark.array).allMatch(v -> v >= 0 && v < 2_500));
        assertTrue(Arrays.stream(benchmark.indices).allMatch(i -> i >= 0 && i < 2_500));
    }

    @Test
    void containsSearchesForAValueThatIsNotThere() throws Exception {
        PrimitiveIntListsBenchmark benchmark = benchmark(2_500);
        for (Method variant : operations().get("contains")) {
            assertFalse((Boolean) variant.invoke(benchmark), variant.getName());
        }
    }

    private static PrimitiveIntListsBenchmark benchmark(int size) {
        PrimitiveIntListsBenchmark benchmark = new PrimitiveIntListsBenchmark();
        benchmark.size = size;
        benchmark.setUp();
        return benchmark;
    }

    private static Map<String, List<Method>> operations() {
        return Arrays.stream(PrimitiveIntListsBenchmark.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Benchmark.class))
                .sorted(Comparator.comparing(Method::getName))
                .collect(Collectors.groupingBy(method -> method.getName().substring(0, method.getName().indexOf('_')),
                        TreeMap::new, Collectors.toList()));
    }

    private static Object normalise(Object result, int size) {
        return switch (result) {
            case int[] array -> Arrays.stream(array, 0, size).boxed().toList();
            case it.unimi.dsi.fastutil.ints.IntList list -> Arrays.stream(list.toIntArray()).boxed().toList();
            case IntList list -> Arrays.stream(list.toArray()).boxed().toList();
            default -> result;
        };
    }
}
