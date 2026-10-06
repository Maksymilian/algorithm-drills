package jdk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;
import java.util.function.LongConsumer;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/// Ograniczona kolejka `long`ów dla jednego producenta i jednego konsumenta, poza stertą, z indeksami
/// ułożonymi tak, że oba wątki nigdy nie piszą do tej samej linii pamięci podręcznej.
///
/// **Dlaczego bez blokad i bez CAS.** Każdy indeks ma dokładnie jednego piszącego: tylko producent
/// przesuwa `tail`, tylko konsument przesuwa `head`. Jedyny piszący nigdy nie potrzebuje atomowego
/// odczytu-modyfikacji-zapisu, więc każda zmiana to zwykły odczyt i `setRelease`, na x86 zwykły
/// zapis. Para release/acquire to cały argument o synchronizacji:
///
/// - producent zapisuje element, potem `TAIL.setRelease(tail + 1)`; konsument, który przez
///    `getAcquire` odczyta ten tail, na pewno zobaczy element.
///
/// - konsument czyta element, potem `HEAD.setRelease(head + 1)`; producent, który przez
///    `getAcquire` odczyta ten head, wie, że pole można nadpisać.
///
/// Indeksy rosną bez końca i są maskowane tylko przy adresowaniu pola, więc `tail - head` to rozmiar
/// i nie ma niejednoznaczności pełna/pusta, na którą trzeba by poświęcić pole. `long` zwiększany raz
/// na nanosekundę przepełni się po 292 latach.
///
/// **Układ:**
///
/// ```
///   offset   0  producent: tail, headCache, wypełnienie do 128   (pisze tylko producent)
///   offset 128  konsument: head, tailCache, wypełnienie do 128   (pisze tylko konsument)
///   osobno      dane:      capacity × long
/// ```
///
/// O szybkości decydują kopie (cache). Bez nich producent czytałby `head` konsumenta przy każdym
/// `offer`, za każdym razem ściągając linię konsumenta przez magistralę. Z nimi czyta `head` tylko
/// wtedy, gdy jego kopia mówi, że kolejka jest pełna, co w działającym potoku zdarza się raz na
/// okrążenie, a nie raz na element. Kopie muszą leżeć _wewnątrz_ wypełnionych grup: jako dwa zwykłe
/// pola tego obiektu stałyby obok siebie i fałszywie współdzieliłyby linię ze sobą nawzajem.
///
/// `offer(value)` dodaje element albo zwraca `false`, gdy kolejka jest pełna (tylko wątek
/// producenta). `drain(sink, max)` przekazuje do `max` elementów po kolei i zwraca ich liczbę
/// (tylko wątek konsumenta). `size()` jest dokładne tylko z wątku, który w tej chwili ani nie
/// produkuje, ani nie konsumuje.
///
/// **Kto jest właścicielem pamięci.** Wywołujący, przez [Arena] przekazaną konstruktorowi. Kolejki
/// używają dwa wątki, więc w prawdziwym użyciu arena musi być [współdzielona][Arena#ofShared()] i
/// nie wolno jej zamknąć, dopóki któryś wątek może jeszcze sięgnąć do kolejki. Arena
/// [ograniczona do wątku][Arena#ofConfined()] działa tylko wtedy, gdy jeden wątek gra obie role, co
/// robią testy jednowątkowe.
///
/// Nic nie sprawdza, czy producent i konsument są naprawdę jedni. Dwóch producentów odczytałoby ten
/// sam tail i nadpisało sobie elementy; to cena braku CAS.
public final class SpscRingBuffer {

    public static final long STRIDE = 128;

    private static StructLayout side(String index, String cache) {
        return MemoryLayout.structLayout(
                JAVA_LONG.withName(index),
                JAVA_LONG.withName(cache),
                MemoryLayout.paddingLayout(STRIDE - 2 * JAVA_LONG.byteSize())
        ).withByteAlignment(STRIDE);
    }

    public static final StructLayout HEADER = MemoryLayout.structLayout(
            side("tail", "headCache").withName("producer"),
            side("head", "tailCache").withName("consumer"));

    // (MemorySegment header, long baseOffset) -> long
    private static final VarHandle TAIL = field("producer", "tail");
    private static final VarHandle HEAD_CACHE = field("producer", "headCache");
    private static final VarHandle HEAD = field("consumer", "head");
    private static final VarHandle TAIL_CACHE = field("consumer", "tailCache");

    private static VarHandle field(String group, String name) {
        return HEADER.varHandle(groupElement(group), groupElement(name)).withInvokeExactBehavior();
    }

    private final MemorySegment header;
    private final MemorySegment data;
    private final long mask;

    public SpscRingBuffer(Arena arena, int capacity) {
        if (capacity <= 0 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a positive power of two: " + capacity);
        }
        this.mask = capacity - 1;
        this.header = arena.allocate(HEADER);
        this.data = arena.allocate(JAVA_LONG, capacity);
    }

    // ---------- strona producenta ----------

    public boolean offer(long value) {
        long tail = (long) TAIL.get(header, 0L);                  // zwykły odczyt: jesteśmy jedynym piszącym
        if (tail - (long) HEAD_CACHE.get(header, 0L) > mask) {    // według kopii wygląda na pełną
            long head = (long) HEAD.getAcquire(header, 0L);       // więc sprawdź prawdziwą wartość
            HEAD_CACHE.set(header, 0L, head);
            if (tail - head > mask) return false;
        }
        data.setAtIndex(JAVA_LONG, tail & mask, value);
        TAIL.setRelease(header, 0L, tail + 1);                    // publikuje element
        return true;
    }

    // ---------- strona konsumenta ----------

    public int drain(LongConsumer sink, int max) {
        long head = (long) HEAD.get(header, 0L);                  // zwykły odczyt: jesteśmy jedynym piszącym
        long available = (long) TAIL_CACHE.get(header, 0L) - head;
        if (available < max) {                                    // kopia może być nieaktualna; odśwież
            long tail = (long) TAIL.getAcquire(header, 0L);
            TAIL_CACHE.set(header, 0L, tail);
            available = tail - head;
        }
        int n = (int) Math.min(available, max);
        if (n <= 0) return 0;
        for (int i = 0; i < n; i++) {
            sink.accept(data.getAtIndex(JAVA_LONG, (head + i) & mask));
        }
        HEAD.setRelease(header, 0L, head + n);                    // zwalnia wszystkie n pól naraz
        return n;
    }

    // ---------- dowolna strona, w przybliżeniu ----------

    public long size() {
        long head = (long) HEAD.getAcquire(header, 0L);
        long tail = (long) TAIL.getAcquire(header, 0L);
        return tail - head;
    }

    public int capacity() {
        return (int) (mask + 1);
    }

    public MemorySegment headerSegment() {
        return header.asReadOnly();
    }
}
