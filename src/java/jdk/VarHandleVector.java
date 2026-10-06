package jdk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/// Arytmetyka wektorowa przez [VarHandle], w obu znaczeniach, które to API naprawdę wspiera: żadne z
/// nich nie jest SIMD i oba warto znać, zanim sięgnie się po `jdk.incubator.vector`.
///
/// **1. Tablica bajtów czytana po osiem pasów naraz.** [MethodHandles#byteArrayViewVarHandle] czyta
/// `byte[]` jako tablicę szerszych wartości. Odczyt jednego `long`a wciąga **osiem pasów bajtowych do
/// jednego rejestru**, a zwykła arytmetyka całkowita działa na wszystkich ośmiu naraz. Ta technika
/// to SWAR (SIMD Within A Register) i tak napisane są `String.indexOf` i pokrewne.
///
/// Arytmetyka ma jedną zasadę: **pas nie może przenieść cyfry do sąsiada**. Każda operacja poniżej
/// jest zbudowana tak, żeby ją szanować, a maski są dowodem, nie ozdobą:
///
/// - `count(data, value)`: XOR z wzorcem rozgłoszonym na wszystkie pasy zamienia „równe `value`” w
///    „zero”, a maska zaznacza zerowe pasy;
/// - [#indexOf]: ten sam krok, a indeks wynika z najniższego zaznaczonego pasa;
/// - `add(a, b)`: dodawanie pas po pasie, zawijające się jak `(byte) (a[i] + b[i])`.
///
/// Kolejność bajtów to nie formalność. [ByteOrder#LITTLE_ENDIAN] jest tu ustalone, żeby pas _i_ był
/// bitami `8i..8i+7`; dzięki temu [Long#numberOfTrailingZeros] to indeks pasa w [#indexOf].
/// [ByteOrder#nativeOrder()] byłoby odrobinę szybsze na maszynie little-endian i po cichu dawałoby
/// [#indexOf] złą odpowiedź na big-endian.
///
/// Widok tablicy bajtów oferuje **tylko `get` i `set`**: każdy tryb atomowy rzuca
/// [UnsupportedOperationException]. Tutaj to zaleta: odczyty są z założenia niewyrównane (pętla idzie
/// co 8 od indeksu 0 dowolnej tablicy), a tylko zwykły dostęp to toleruje. Szeroki dostęp _atomowy_
/// wymaga [MethodHandles#byteBufferViewVarHandle] na buforze _bezpośrednim_; na buforze na stercie też
/// zawodzi.
///
/// **2. Jeden element tablicy naraz, atomowo.** [MethodHandles#arrayElementVarHandle] daje każdemu
/// elementowi tablicy pełen zestaw trybów dostępu (`getVolatile`, `compareAndSet`, `getAndAdd`) bez
/// opakowania w `AtomicLongArray`. Zwykły `long[]` staje się więc akumulatorem bez blokad, do którego
/// może dodawać kilka wątków (`addInto`), a `getAndAdd` działa też na `double[]` i `float[]` (tylko
/// tryby _bitowe_ są wyłącznie dla typów całkowitych).
///
/// To kupuje atomowość **pasa** i nic więcej. Czytelnik całej tablicy może zobaczyć mieszankę stanu
/// sprzed i po, bo żadne dwa pasy nie zmieniają się razem. Gdy wektor ma niezmiennik _między_ pasami,
/// to nie wystarcza i odpowiedzią jest blokada: do tego służą [#addIntoUnderLock] i `snapshot`, a
/// testy obok tej klasy to mierzą.
///
/// Nie ma trybu dostępu dla niczego, na co sprzęt nie ma instrukcji, na przykład maksimum. [#maxInto]
/// to pętla ponawiania CAS, która wypełnia tę lukę, i tu zaczyna mieć znaczenie bitowa natura
/// `compareAndSet` na `double`: `-0.0` nie pasuje do `0.0`, a `NaN` pasuje do samego siebie; tę samą
/// asymetrię `ArrayComparisonTest` sprawdza dla `Arrays.equals`.
public final class VarHandleVector {

    private static final VarHandle LONG_LANES =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final VarHandle LONG_ELEMENT = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle DOUBLE_ELEMENT = MethodHandles.arrayElementVarHandle(double[].class);

    private static final int LANES = Long.BYTES;
    private static final long ONES = 0x0101010101010101L;   // 1 w każdym pasie
    private static final long HIGHS = 0x8080808080808080L;  // najwyższy bit każdego pasa
    private static final long LOWS = 0x7F7F7F7F7F7F7F7FL;   // wszystko poza najwyższym bitem

    private VarHandleVector() {
    }

    // ---------- 1. SWAR na byte[] ----------

    private static long zeroLanes(long v) {
        return ~((((v & LOWS) + LOWS) | v) | LOWS);
    }

    private static long broadcast(byte value) {
        return (value & 0xFFL) * ONES;
    }

    public static int count(byte[] data, byte value) {
        long pattern = broadcast(value);
        int i = 0, found = 0;
        int limit = data.length - LANES;
        for (; i <= limit; i += LANES) {
            found += Long.bitCount(zeroLanes((long) LONG_LANES.get(data, i) ^ pattern));
        }
        for (; i < data.length; i++) {                      // końcówka, po jednym pasie
            if (data[i] == value) found++;
        }
        return found;
    }

    public static int indexOf(byte[] data, byte value) {
        long pattern = broadcast(value);
        int i = 0;
        int limit = data.length - LANES;
        for (; i <= limit; i += LANES) {
            long marks = zeroLanes((long) LONG_LANES.get(data, i) ^ pattern);
            if (marks != 0) return i + (Long.numberOfTrailingZeros(marks) >>> 3);
        }
        for (; i < data.length; i++) {
            if (data[i] == value) return i;
        }
        return -1;
    }

    public static byte[] add(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("length " + a.length + " != " + b.length);
        }
        byte[] out = new byte[a.length];
        int i = 0;
        int limit = a.length - LANES;
        for (; i <= limit; i += LANES) {
            long x = (long) LONG_LANES.get(a, i);
            long y = (long) LONG_LANES.get(b, i);
            LONG_LANES.set(out, i, ((x & LOWS) + (y & LOWS)) ^ ((x ^ y) & HIGHS));
        }
        for (; i < a.length; i++) {
            out[i] = (byte) (a[i] + b[i]);
        }
        return out;
    }

    // ---------- 2. atomowe operacje na elementach zwykłej tablicy ----------

    public static void addInto(long[] accumulator, long[] delta) {
        requireSameLength(accumulator.length, delta.length);
        for (int i = 0; i < accumulator.length; i++) {
            LONG_ELEMENT.getAndAdd(accumulator, i, delta[i]);
        }
    }

    public static void addInto(double[] accumulator, double[] delta) {
        requireSameLength(accumulator.length, delta.length);
        for (int i = 0; i < accumulator.length; i++) {
            DOUBLE_ELEMENT.getAndAdd(accumulator, i, delta[i]);
        }
    }

    public static void maxInto(double[] accumulator, double[] candidate) {
        requireSameLength(accumulator.length, candidate.length);
        for (int i = 0; i < accumulator.length; i++) {
            double seen;
            do {
                seen = (double) DOUBLE_ELEMENT.getVolatile(accumulator, i);
                if (Double.compare(candidate[i], seen) <= 0) break;   // już co najmniej tak duża
            } while (!DOUBLE_ELEMENT.compareAndSet(accumulator, i, seen, candidate[i]));
        }
    }

    public static void addIntoUnderLock(VarHandleLock lock, long[] accumulator, long[] delta) {
        requireSameLength(accumulator.length, delta.length);
        lock.lock();
        try {
            for (int i = 0; i < accumulator.length; i++) {
                accumulator[i] += delta[i];       // zwykły zapis: publikuje je blokada
            }
        } finally {
            lock.unlock();
        }
    }

    public static long[] snapshot(VarHandleLock lock, long[] vector) {
        lock.lock();
        try {
            return Arrays.copyOf(vector, vector.length);
        } finally {
            lock.unlock();
        }
    }

    private static void requireSameLength(int a, int b) {
        if (a != b) throw new IllegalArgumentException("length " + a + " != " + b);
    }
}
