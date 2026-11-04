package collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Uzgodnienie portfela (_reconciliation_): stan początkowy `p0` plus transakcje powinien dać
/// stan raportowany `p1`. Zwracamy pozycje, które się nie zgadzają, w formacie `"SYMBOL różnica"`,
/// gdzie różnica = raportowane − oczekiwane.
///
/// ```
/// p0 = [AAB 10, GOOG 30, Cash 1000]
/// t  = [AAB BY 3 200, GOOG SL 10 20, AAB BY 20 210]
/// p1 = [AAB 20, GOOG 40, Cash 3000]
/// oczekiwane: AAB 33, GOOG 20, Cash -3600  →  [AAB -13, GOOG 20, Cash 6600]
/// ```
///
/// **Pomysł.** Wczytujemy `p0` do mapy, każdą transakcję księgujemy dwa razy: na akcjach
/// (`BY` dodaje, `SL` odejmuje ilość) i na gotówce (`BY` odejmuje, `SL` dodaje `ilość·cena`).
/// Potem porównujemy z `p1` po sumie zbiorów symboli z obu stron.
///
/// **Założenia.**
///
/// | | |
/// |---|---|
/// | Pozycja | `"SYMBOL ilość"`, ilość to dowolny `long` (także ujemna: krótka sprzedaż, debet) |
/// | Transakcja | `"SYMBOL BY/SL ilość cena"`, ilość > 0, cena ≥ 0; inaczej `IllegalArgumentException` |
/// | Gotówka | symbol `Cash`; transakcja na `Cash` rzuca `IllegalArgumentException` |
/// | Brak symbolu po którejś stronie | liczy się jako 0, więc wyzerowana pozycja nieobecna w `p1` jest poprawna |
/// | Powtórzony symbol w `p0` albo `p1` | `IllegalArgumentException` |
/// | Puste listy | poprawne wejście |
/// | `null` jako lista albo element | `NullPointerException` |
/// | Przepełnienie | `ArithmeticException` |
/// | Kolejność wyniku | najpierw symbole w kolejności z `p0`, potem nowe z transakcji, potem nowe z `p1` |
///
/// **Złożoność.** O(n + t + m) czasu i pamięci.
public class PortfolioReconciliation {

    private static final String CASH = "Cash";
    private static final String BUY = "BY";
    private static final String SELL = "SL";
    private static final String SEPARATOR = "\\s+";
    private static final int POSITION_FIELDS = 2;
    private static final int TRANSACTION_FIELDS = 4;

    private record Transaction(String symbol, long shareChange, long cashChange) {
    }

    public static List<String> reconcile(List<String> start, List<String> transactions, List<String> reported) {
        requireLines(start, "brak stanu początkowego");
        requireLines(transactions, "brak listy transakcji");
        requireLines(reported, "brak stanu raportowanego");
        Map<String, Long> balances = parsePositions(start);
        transactions.forEach(line -> book(balances, parseTransaction(line)));
        return differences(balances, parsePositions(reported));
    }

    private static void book(Map<String, Long> balances, Transaction transaction) {
        balances.merge(transaction.symbol(), transaction.shareChange(), Math::addExact);
        balances.merge(CASH, transaction.cashChange(), Math::addExact);
    }

    private static List<String> differences(Map<String, Long> balances, Map<String, Long> reported) {
        Set<String> symbols = new LinkedHashSet<>(balances.keySet());
        symbols.addAll(reported.keySet());
        List<String> mismatches = new ArrayList<>();
        for (String symbol : symbols) {
            long difference = Math.subtractExact(reported.getOrDefault(symbol, 0L), balances.getOrDefault(symbol, 0L));
            if (difference != 0) {
                mismatches.add(symbol + " " + difference);
            }
        }
        return mismatches;
    }

    // symbol nieobecny po danej stronie ma saldo 0

    private static Map<String, Long> parsePositions(List<String> lines) {
        Map<String, Long> positions = new LinkedHashMap<>();
        for (String line : lines) {
            String[] fields = split(line, POSITION_FIELDS);
            if (positions.putIfAbsent(fields[0], parseNumber(fields[1], line)) != null) {
                throw new IllegalArgumentException("powtórzony symbol: " + line);
            }
        }
        return positions;
    }

    private static Transaction parseTransaction(String line) {
        String[] fields = split(line, TRANSACTION_FIELDS);
        String symbol = requireNotCash(fields[0], line);
        long quantity = requirePositive(parseNumber(fields[2], line), line);
        long price = requireNonNegative(parseNumber(fields[3], line), line);
        long shareChange = direction(fields[1], line) * quantity;
        return new Transaction(symbol, shareChange, Math.negateExact(Math.multiplyExact(shareChange, price)));
    }

    // +1 dla kupna, −1 dla sprzedaży
    private static long direction(String side, String line) {
        return switch (side) {
            case BUY -> 1;
            case SELL -> -1;
            default -> throw new IllegalArgumentException("nieznany rodzaj transakcji: " + line);
        };
    }

    private static String[] split(String line, int expectedFields) {
        String[] fields = line.strip().split(SEPARATOR);
        if (fields.length != expectedFields) {
            throw new IllegalArgumentException("oczekiwano %d pól: %s".formatted(expectedFields, line));
        }
        return fields;
    }

    private static long parseNumber(String text, String line) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("niepoprawna liczba w: " + line, e);
        }
    }

    private static String requireNotCash(String symbol, String line) {
        if (CASH.equals(symbol)) {
            throw new IllegalArgumentException("transakcja na gotówce: " + line);
        }
        return symbol;
    }

    private static long requirePositive(long quantity, String line) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("ilość musi być dodatnia: " + line);
        }
        return quantity;
    }

    private static long requireNonNegative(long price, String line) {
        if (price < 0) {
            throw new IllegalArgumentException("cena nie może być ujemna: " + line);
        }
        return price;
    }

    private static void requireLines(List<String> lines, String message) {
        Objects.requireNonNull(lines, message);
        for (String line : lines) {
            Objects.requireNonNull(line, "brak wiersza w liście");
        }
    }

    void main() {
        List<String> start = List.of("AAB 10", "GOOG 30", "Cash 1000");
        List<String> transactions = List.of("AAB BY 3 200", "GOOG SL 10 20", "AAB BY 20 210");
        List<String> reported = List.of("AAB 20", "GOOG 40", "Cash 3000");
        IO.println(reconcile(start, transactions, reported));
    }
}
