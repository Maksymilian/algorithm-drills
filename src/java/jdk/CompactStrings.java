package jdk;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.ThreadMXBean;

/// `String` trzyma znaki w tablicy **bajtów**, a nie znaków (Compact Strings, JEP 254, od Javy 9).
/// Notatka z pomiarami: `docs/jdk/compact-strings.md`.
///
/// **Budowa.** [#instanceFields()] czyta przez refleksję pola instancji klasy `String`:
/// `byte[] value`, `byte coder`, `int hash`, `boolean hashIsZero`. Nie ma pola `char[]`. Sama
/// nazwa i typ pola są dostępne bez `--add-opens`; zamknięta jest dopiero jego wartość.
///
/// **Dwa kodowania.** `coder` mówi, jak czytać `value`:
///
/// - `LATIN1`: gdy każdy znak mieści się w ISO-8859-1 (kody 0–255), **1 bajt na znak**;
/// - `UTF16`: w przeciwnym razie, **2 bajty na znak**, dla całego napisu.
///
/// Jedna litera spoza Latin-1 przełącza więc cały napis na 2 bajty na znak. Z polskich liter w
/// Latin-1 jest tylko `ó`; `ą ć ę ł ń ś ź ż` już nie, więc „zażółć” to UTF-16 ([#fitsLatin1(String)]).
///
/// **Jak to wykazać.** [#allocatedBytes(Supplier)] mierzy licznikiem alokacji bieżącego wątku
/// (`com.sun.management.ThreadMXBean`), ile bajtów zaalokowało utworzenie napisu: obiekt `String` plus
/// jego tablica `byte[]`. `"a".repeat(1000)` to 1040 B, `"ą".repeat(1000)` 2040 B: różnica to
/// dokładnie 1000 bajtów, po jednym na znak. Z wyłączonym `-XX:-CompactStrings`
/// ([#compactStringsEnabled()] to sprawdza) oba napisy zajmują 2040 B.
public class CompactStrings {

    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile Object sink;

    public static Map<String, String> instanceFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Field field : String.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fields.put(field.getName(), field.getType().getSimpleName());
            }
        }
        return fields;
    }

    public static boolean compactStringsEnabled() {
        HotSpotDiagnosticMXBean hotspot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        return Boolean.parseBoolean(hotspot.getVMOption("CompactStrings").getValue());
    }

    public static boolean fitsLatin1(String text) {
        return text.chars().allMatch(c -> c <= 0xFF);
    }

    public static long allocatedBytes(Supplier<?> action) {
        for (int i = 0; i < 1_000; i++) {
            sink = action.get();   // rozgrzanie: bez alokacji przy ładowaniu klas i kompilacji
        }
        long before = THREADS.getCurrentThreadAllocatedBytes();
        sink = action.get();   // volatile: obiekt ucieka, więc JIT nie może usunąć alokacji
        return THREADS.getCurrentThreadAllocatedBytes() - before;
    }

    private static void report(String label, Supplier<String> text) {
        long bytes = allocatedBytes(text);
        IO.println("%-30s %5d B  (%.2f B na znak)".formatted(label, bytes, (double) bytes / text.get().length()));
    }

    void main() {
        String tail = "a".repeat(999);
        IO.println("pola instancji String: " + instanceFields());
        IO.println("CompactStrings: " + compactStringsEnabled());
        report("1000 × 'a'", () -> "a".repeat(1000));
        report("1000 × 'ó' (jest w Latin-1)", () -> "ó".repeat(1000));
        report("1000 × 'ą' (spoza Latin-1)", () -> "ą".repeat(1000));
        report("'a' + 999 × 'a'", () -> "a" + tail);
        report("'ą' + 999 × 'a'", () -> "ą" + tail);
    }
}
