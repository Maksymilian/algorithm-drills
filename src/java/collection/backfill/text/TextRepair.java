package collection.backfill.text;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/// Naprawa nazw zepsutych przez kasy w sklepach: bajty UTF-8 odczytane jako ISO-8859-1
/// (`"Å¼"` zamiast `"ż"`). Napis naprawiamy tylko wtedy, gdy wszystkie znaki mieszczą się w
/// ISO-8859-1, co najmniej jeden jest spoza ASCII, a bajty dają poprawny UTF-8. W każdym innym
/// przypadku (np. `"Café"`) zwracamy napis bez zmian. `null` to `NullPointerException`.
public final class TextRepair {

    private static final int LAST_LATIN1 = 0xFF;
    private static final int FIRST_NON_ASCII = 0x80;

    private TextRepair() {
    }

    public static String repair(String text) {
        Objects.requireNonNull(text, "brak napisu");
        if (!isLatin1WithHighBytes(text)) {
            return text;
        }
        return decodeStrictUtf8(text.getBytes(StandardCharsets.ISO_8859_1)).orElse(text);
    }

    public static boolean isMangled(String text) {
        return !repair(text).equals(text);
    }

    private static boolean isLatin1WithHighBytes(String text) {
        return text.chars().allMatch(c -> c <= LAST_LATIN1) && text.chars().anyMatch(c -> c >= FIRST_NON_ASCII);
    }

    private static Optional<String> decodeStrictUtf8(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }
}
