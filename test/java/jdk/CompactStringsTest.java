package jdk;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static jdk.CompactStrings.allocatedBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactStringsTest {

    private static final String TAIL = "a".repeat(999);

    @Test
    void stringKeepsItsCharactersInAByteArray() {
        Map<String, String> fields = CompactStrings.instanceFields();
        assertEquals("byte[]", fields.get("value"));
        assertEquals("byte", fields.get("coder"));
        assertFalse(fields.containsValue("char[]"), fields.toString());
    }

    @Test
    void compactStringsAreOnByDefault() {
        assertTrue(CompactStrings.compactStringsEnabled());
    }

    @Test
    void latin1TextCostsOneBytePerCharacter() {
        assertEquals(1_000, allocatedBytes(() -> "a".repeat(2_000)) - allocatedBytes(() -> "a".repeat(1_000)));
    }

    @Test
    void textOutsideLatin1CostsTwoBytesPerCharacter() {
        assertEquals(2_000, allocatedBytes(() -> "ą".repeat(2_000)) - allocatedBytes(() -> "ą".repeat(1_000)));
    }

    @Test
    void oneCharacterOutsideLatin1DoublesTheWholeString() {
        assertEquals(1_000, allocatedBytes(() -> "ą" + TAIL) - allocatedBytes(() -> "a" + TAIL));
    }

    @Test
    void ofThePolishLettersOnlyOWithAcuteFitsLatin1() {
        assertEquals(allocatedBytes(() -> "a".repeat(1_000)), allocatedBytes(() -> "ó".repeat(1_000)));
        assertTrue(CompactStrings.fitsLatin1("ó"));
        for (String letter : List.of("ą", "ć", "ę", "ł", "ń", "ś", "ź", "ż")) {
            assertFalse(CompactStrings.fitsLatin1(letter), letter);
        }
    }

    @Test
    void withCompactStringsOffLatin1TextTakesTwoBytesPerCharacterToo() throws IOException, InterruptedException {
        List<String> lines = runWithoutCompactStrings();
        assertTrue(lines.contains("CompactStrings: false"), lines.toString());
        assertEquals(bytesOf(lines, "1000 × 'ą'"), bytesOf(lines, "1000 × 'a'"), lines.toString());
    }

    private static List<String> runWithoutCompactStrings() throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElse("java");
        Process process = new ProcessBuilder(java, "-XX:-CompactStrings", "-cp", System.getProperty("java.class.path"),
                "jdk.CompactStrings").redirectErrorStream(true).start();
        List<String> lines = process.inputReader().lines().toList();
        assertEquals(0, process.waitFor(), lines.toString());
        return lines;
    }

    private static long bytesOf(List<String> lines, String label) {
        String line = lines.stream().filter(l -> l.startsWith(label)).findFirst().orElseThrow();
        return Long.parseLong(line.substring(30).trim().split(" ")[0]);
    }
}
