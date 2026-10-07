package jdk;

import java.util.Arrays;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/// Jawne SIMD w Javie przez Vector API (`jdk.incubator.vector`) na tablicach `int`. Notatka o tym
/// procesorze i pomiarach: `docs/jdk/int-vector-and-this-cpu.md`.
///
/// Wektor to rejestr dzielony na pasy. [IntVector#SPECIES_PREFERRED] wybiera największy, jaki ma
/// procesor: na i7-14650HX to AVX2, czyli 256 bitów i **8 pasów `int`**. Każda metoda ma ten sam
/// kształt: pętla po pełnych wektorach do [VectorSpecies#loopBound(int)], potem zwykła pętla po
/// końcówce, której nie da się wypełnić całym wektorem.
///
/// - [#add(int\[\], int\[\])]: dodawanie pas po pasie. Zwykła pętla `out[i] = a[i] + b[i]` jest już
///   automatycznie wektoryzowana przez C2, więc tu Vector API nic nie zyskuje.
/// - [#sum(int\[\])]: suma przez akumulator wektorowy i jedną redukcję na końcu. Dodawanie `int` jest
///   łączne modulo 2³², więc wynik jest identyczny z sumą po kolei, także przy przepełnieniu (dla
///   `float` i `double` kolejność zmienia wynik).
/// - [#count(int\[\], int)]: porównanie wszystkich pasów naraz daje maskę, a `trueCount()` liczy
///   trafienia. Zwykła pętla z warunkiem nie jest automatycznie wektoryzowana i tu Vector API wygrywa.
/// - [#indexOf(int\[\], int)]: to samo porównanie z wczesnym wyjściem przez `anyTrue()` i
///   `firstTrue()`. Pętli z wczesnym wyjściem C2 też nie wektoryzuje.
///
/// Wymaga `--add-modules jdk.incubator.vector` przy kompilacji i uruchomieniu (ustawione w `pom.xml`
/// i w `scripts/perf/common.sh`); JVM wypisuje wtedy ostrzeżenie o module inkubatora. Kod Vector API
/// jest szybki dopiero po kompilacji przez C2: w interpreterze i C1 każdy wektor to zwykły obiekt.
public class IntVectors {

    static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    public static int lanes() {
        return SPECIES.length();
    }

    public static int[] add(int[] a, int[] b) {
        requireSameLength(a, b);
        int[] out = new int[a.length];
        int i = 0;
        for (; i < SPECIES.loopBound(a.length); i += SPECIES.length()) {
            IntVector.fromArray(SPECIES, a, i).add(IntVector.fromArray(SPECIES, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) {
            out[i] = a[i] + b[i];
        }
        return out;
    }

    public static int sum(int[] data) {
        IntVector acc = IntVector.zero(SPECIES);
        int i = 0;
        for (; i < SPECIES.loopBound(data.length); i += SPECIES.length()) {
            acc = acc.add(IntVector.fromArray(SPECIES, data, i));
        }
        int sum = acc.reduceLanes(VectorOperators.ADD);
        for (; i < data.length; i++) {
            sum += data[i];
        }
        return sum;
    }

    public static int count(int[] data, int value) {
        int count = 0, i = 0;
        for (; i < SPECIES.loopBound(data.length); i += SPECIES.length()) {
            count += IntVector.fromArray(SPECIES, data, i).compare(VectorOperators.EQ, value).trueCount();
        }
        for (; i < data.length; i++) {
            count += data[i] == value ? 1 : 0;
        }
        return count;
    }

    public static int indexOf(int[] data, int value) {
        int i = 0;
        for (; i < SPECIES.loopBound(data.length); i += SPECIES.length()) {
            VectorMask<Integer> hits = IntVector.fromArray(SPECIES, data, i).compare(VectorOperators.EQ, value);
            if (hits.anyTrue()) {
                return i + hits.firstTrue();
            }
        }
        return indexOfFrom(data, value, i);
    }

    private static int indexOfFrom(int[] data, int value, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static void requireSameLength(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("różne długości: " + a.length + " i " + b.length);
        }
    }

    void main() {
        int[] data = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3};
        IO.println("wektor: " + SPECIES.vectorBitSize() + " bitów, " + lanes() + " pasów int");
        IO.println("add:     " + Arrays.toString(add(data, data)));
        IO.println("sum:     " + sum(data));
        IO.println("count 9: " + count(data, 9));
        IO.println("indexOf 9: " + indexOf(data, 9));
    }
}
