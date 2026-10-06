package jdk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.LongAdder;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/// Licznik rozłożony na paski, czyli ręcznie zbudowany [LongAdder], którego układ w pamięci jest
/// zadeklarowany, a nie zostawiony JVM, przy użyciu Foreign Function &amp; Memory API.
///
/// **Problem, który rozwiązuje wypełnienie.** Dwa wątki zwiększające dwa _różne_ `long`i nadal ze
/// sobą walczą, jeśli obie wartości leżą w jednej linii pamięci podręcznej: każdy zapis bierze linię
/// na wyłączność, więc odbija się ona między rdzeniami dokładnie tak, jakby wątki dzieliły wartość.
/// To fałszywe współdzielenie (false sharing), a tablica ośmiu `long`ów to najgorszy przypadek:
/// wszystkie osiem mieści się w jednej 64-bajtowej linii.
///
/// Tutaj każdy pasek to [#SLOT] o rozmiarze {@value #STRIDE} bajtów: licznik, a potem
/// [wypełnienie][MemoryLayout#paddingLayout] do końca. 128, a nie 64, bo prefetcher sąsiednich linii
/// Intela pobiera 64-bajtowe linie parami, więc sąsiedzi o jedną linię dalej nadal sobie
/// przeszkadzają; `@jdk.internal.vm.annotation.Contended` wypełnia do 128 z tego samego powodu.
/// `@Contended` wymagałoby `-XX:-RestrictContended` i zostawia układ JVM; układ zadeklarowany w
/// kodzie źródłowym da się sprawdzić testem.
///
/// **Dlaczego VarHandle jest statyczny i dokładny.** [#VALUE] wynika z układu, więc jego
/// współrzędne to `(MemorySegment, long baseOffset, long index)`. Ponieważ jest `static final`, C2
/// zwija go do stałej, a `getAndAdd` kompiluje się do jednej instrukcji `lock xadd`.
/// [VarHandle#withInvokeExactBehavior()] sprawia, że wywołanie ze złymi typami współrzędnych
/// (indeks `int`, `0` zamiast `0L`) rzuca wyjątek, zamiast być po cichu dopasowywane przy każdym
/// wywołaniu.
///
/// `increment()` dodaje jeden do paska bieżącego wątku; to wciąż atomowe odczyt-modyfikacja-zapis,
/// bo dwa wątki mogą trafić na ten sam pasek, a wypełnienie usuwa tylko walkę między _różnymi_
/// paskami. `sum()`, jak [LongAdder#sum()], nie jest migawką: każdy pasek czyta atomowo, ale
/// zwiększenia w trakcie pętli mogą zostać policzone albo nie.
///
/// **Kto jest właścicielem pamięci.** Wywołujący: konstruktor przyjmuje [Arena], a zamknięcie tej
/// areny zwalnia paski. Żeby licznik miał sens, arena musi być [współdzielona][Arena#ofShared()],
/// bo segment z areny [ograniczonej do wątku][Arena#ofConfined()] rzuca [WrongThreadException] w
/// każdym wątku poza tym, który go utworzył. Arena ograniczona do wątku nadal pasuje do użycia
/// jednowątkowego i taniej się ją zamyka: zamknięcie współdzielonej wymaga uzgodnienia z każdym
/// wątkiem, który może być w trakcie dostępu.
///
/// @see LongAdder
public final class PaddedCounters {

    public static final long STRIDE = 128;

    public static final StructLayout SLOT = MemoryLayout.structLayout(
            JAVA_LONG.withName("value"),
            MemoryLayout.paddingLayout(STRIDE - JAVA_LONG.byteSize())
    ).withByteAlignment(STRIDE);

    private static final VarHandle VALUE =
            SLOT.arrayElementVarHandle(groupElement("value")).withInvokeExactBehavior();

    private final MemorySegment slots;
    private final long mask;

    public PaddedCounters(Arena arena, int stripes) {
        ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
        if (stripes <= 0 || Integer.bitCount(stripes) != 1) {
            throw new IllegalArgumentException("stripes must be a positive power of two: " + stripes);
        }
        this.mask = stripes - 1;
        this.slots = arena.allocate(SLOT, stripes);   // wyzerowana i z wyrównaniem do 128 bajtów
    }

    public void increment() {
        add(1L);
    }

    public void add(long delta) {
        long stripe = Thread.currentThread().threadId() & mask;
        long unused = (long) VALUE.getAndAdd(slots, 0L, stripe, delta);
    }

    public long sum() {
        long total = 0;
        for (long i = 0; i <= mask; i++) {
            total += (long) VALUE.getVolatile(slots, 0L, i);
        }
        return total;
    }

    public long stripe(long index) {
        return (long) VALUE.getVolatile(slots, 0L, index);
    }

    public int stripes() {
        return (int) (mask + 1);
    }

    public MemorySegment segment() {
        return slots.asReadOnly();
    }
}
