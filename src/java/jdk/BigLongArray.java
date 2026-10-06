package jdk;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/// Tablica `long`ów indeksowana `long`iem, więc może mieć więcej niż [Integer#MAX_VALUE] elementów,
/// czego nie potrafi żadna tablica w Javie.
///
/// **Granica, którą usuwa.** Długość tablicy to `int`, więc żadna tablica nie ma więcej niż
/// `2^31 - 1` elementów, a HotSpot ogranicza to jeszcze o kilka elementów: `new byte[Integer.MAX_VALUE]`
/// kończy się `OutOfMemoryError: Requested array size exceeds VM limit` niezależnie od rozmiaru
/// sterty. Dla `long[]` to sufit 16 GiB, a dla `byte[]` tylko 2 GiB. Zwykłe obejście to tablica
/// tablic i ręczne dzielenie każdego indeksu.
///
/// [MemorySegment] ma rozmiar i adresy w bajtach typu `long`, więc jedyne granice to przestrzeń
/// adresowa i pamięć za nią. Ta klasa to jeden segment i jeden [VarHandle] indeksowany `long`iem,
/// bez dzielenia na kawałki.
///
/// **Dwa źródła pamięci:**
///
/// - [#allocate]: pamięć natywna z [Arena]. Leży poza stertą Javy, więc `-Xmx` jej nie ogranicza, i
///    jest wyzerowana. Zajmuje RAM.
///
/// - [#map]: plik mapowany w pamięć. Rozmiar pliku ustawia `setLength`, co na ext4 i tmpfs zostawia
///    plik rzadki, więc tablica 16 GiB kosztuje dysk i RAM tylko za strony, do których naprawdę coś
///    zapisano. Tak testy tanio wychodzą poza granicę `int`.
///
/// W obu przypadkach pamięć należy do areny, a jej zamknięcie (na końcu
/// `try (Arena arena = Arena.ofConfined())`) zwalnia pamięć albo odmapowuje plik. Każdy późniejszy
/// dostęp rzuca [IllegalStateException], zamiast czytać zwolnioną pamięć.
///
/// Odczyt sprawdza granice jak tablica: indeks za końcem to [IndexOutOfBoundsException], a ujemny to
/// [IllegalArgumentException]. `fill` używa [MemorySegment#fill] dla zer, a `sum` liczy w `long` od
/// początku do końca.
///
/// **Granica `int` nie zniknęła wszędzie.** Wszystko, co kopiuje segment z powrotem _do_ tablicy,
/// dalej na nią trafia: [MemorySegment#toArray] rzuca wyjątek, gdy wynik potrzebowałby więcej niż
/// `int` elementów. Dane indeksowane `long`iem muszą takie zostać do samego końca.
public final class BigLongArray {

    private static final VarHandle ELEMENT = JAVA_LONG.arrayElementVarHandle().withInvokeExactBehavior();

    private final MemorySegment segment;
    private final long length;

    private BigLongArray(MemorySegment segment) {
        this.segment = segment;
        this.length = segment.byteSize() / JAVA_LONG.byteSize();
    }

    public static BigLongArray allocate(Arena arena, long length) {
        requireNonNegative(length);
        return new BigLongArray(arena.allocate(JAVA_LONG, length));
    }

    public static BigLongArray map(Arena arena, Path file, long length) throws IOException {
        requireNonNegative(length);
        long bytes = Math.multiplyExact(length, JAVA_LONG.byteSize());
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(bytes);                          // ftruncate: plik rzadki, żadne dane nie są zapisywane
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            return new BigLongArray(channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes, arena));
        }
    }

    public long length() {
        return length;
    }

    public long get(long index) {
        return (long) ELEMENT.get(segment, 0L, index);
    }

    public void set(long index, long value) {
        ELEMENT.set(segment, 0L, index, value);
    }

    public void fill(long from, long to, long value) {
        if (value == 0) {
            segment.asSlice(from * JAVA_LONG.byteSize(), (to - from) * JAVA_LONG.byteSize()).fill((byte) 0);
            return;
        }
        for (long i = from; i < to; i++) {
            ELEMENT.set(segment, 0L, i, value);
        }
    }

    public long sum(long from, long to) {
        long total = 0;
        for (long i = from; i < to; i++) {
            total += (long) ELEMENT.get(segment, 0L, i);
        }
        return total;
    }

    public MemorySegment segment() {
        return segment.asReadOnly();
    }

    private static void requireNonNegative(long length) {
        if (length < 0) throw new IllegalArgumentException("negative length: " + length);
    }
}
