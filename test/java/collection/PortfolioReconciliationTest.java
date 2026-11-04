package collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortfolioReconciliationTest {

    private static final List<String> START = List.of("AAB 10", "GOOG 30", "Cash 1000");
    private static final List<String> TRANSACTIONS = List.of("AAB BY 3 200", "GOOG SL 10 20", "AAB BY 20 210");

    @Test
    void sampleFromTask() {
        List<String> reported = List.of("AAB 20", "GOOG 40", "Cash 3000");
        assertEquals(List.of("AAB -13", "GOOG 20", "Cash 6600"),
                PortfolioReconciliation.reconcile(START, TRANSACTIONS, reported));
    }

    @Test
    void matchingBalancesAreNotReported() {
        List<String> reported = List.of("AAB 20", "GOOG 20", "Cash -3600");
        assertEquals(List.of("AAB -13"), PortfolioReconciliation.reconcile(START, TRANSACTIONS, reported));
    }

    @Test
    void fullyReconciledPortfolioGivesEmptyList() {
        List<String> reported = List.of("Cash -3600", "GOOG 20", "AAB 33");
        assertEquals(List.of(), PortfolioReconciliation.reconcile(START, TRANSACTIONS, reported), "kolejność w p1 bez znaczenia");
    }

    @Test
    void emptyInputsAreReconciled() {
        assertEquals(List.of(), PortfolioReconciliation.reconcile(List.of(), List.of(), List.of()));
    }

    @Test
    void missingSymbolCountsAsZero() {
        assertEquals(List.of(), PortfolioReconciliation.reconcile(
                List.of("AAB 5", "Cash 0"), List.of("AAB SL 5 10"), List.of("Cash 50")), "wyzerowana pozycja może zniknąć");
        assertEquals(List.of("AAB -5"), PortfolioReconciliation.reconcile(
                List.of("AAB 5"), List.of(), List.of()), "zniknęła niezerowa pozycja");
        assertEquals(List.of("MSFT 7"), PortfolioReconciliation.reconcile(
                List.of(), List.of(), List.of("MSFT 7")), "nowy symbol tylko w p1");
    }

    @Test
    void symbolOnlyInTransactionsStartsFromZero() {
        assertEquals(List.of("TSLA -2", "Cash 300"), PortfolioReconciliation.reconcile(
                List.of(), List.of("TSLA BY 2 150"), List.of()));
    }

    @Test
    void newSymbolsFromTransactionsAndSymbolsGoneFromReport() {
        List<String> start = List.of("AAB 10", "Cash 100");
        List<String> transactions = List.of("MSFT BY 5 10", "AAB SL 10 1", "NVDA BY 2 1");
        List<String> reported = List.of("Cash 58", "NVDA 2");
        assertEquals(List.of("MSFT -5"), PortfolioReconciliation.reconcile(start, transactions, reported),
                "AAB sprzedane do zera może zniknąć, MSFT kupione i zgubione w raporcie");
    }

    @Test
    void symbolBoughtAndSoldWithinTransactionsMayBeAbsentEverywhere() {
        assertEquals(List.of(), PortfolioReconciliation.reconcile(
                List.of("Cash 100"), List.of("MSFT BY 5 10", "MSFT SL 5 12"), List.of("Cash 110")));
    }

    @Test
    void newSymbolFromTransactionsReportedWithWrongBalance() {
        assertEquals(List.of("MSFT 2"), PortfolioReconciliation.reconcile(
                List.of("Cash 100"), List.of("MSFT BY 5 10"), List.of("Cash 50", "MSFT 7")));
    }

    @Test
    void mismatchesFollowOrderStartThenTransactionsThenReport() {
        assertEquals(List.of("AAB -1", "MSFT -1", "TSLA 3"), PortfolioReconciliation.reconcile(
                List.of("AAB 1"), List.of("MSFT BY 1 0"), List.of("TSLA 3")));
    }

    @Test
    void negativeBalancesAreAllowed() {
        assertEquals(List.of(), PortfolioReconciliation.reconcile(
                List.of("Cash -10"), List.of("AAB SL 3 5"), List.of("AAB -3", "Cash 5")));
    }

    @Test
    void extraWhitespaceAndUnicodeSymbolsAreAccepted() {
        assertEquals(List.of("Żółć 1"), PortfolioReconciliation.reconcile(
                List.of("  Żółć   4 "), List.of("Żółć\tSL 1 0"), List.of("Żółć 4")));
    }

    @Test
    void invalidFormatIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("AAB XX 1 1"), "zły rodzaj");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("AAB BY 1"), "za mało pól");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("AAB BY 0 1"), "zerowa ilość");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("AAB BY -1 1"), "ujemna ilość");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("AAB BY 1 -1"), "ujemna cena");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("AAB BY 1 2.5"), "ułamek");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction("Cash BY 1 1"), "transakcja na gotówce");
        assertThrows(IllegalArgumentException.class, () -> reconcileTransaction(""), "pusty wiersz");
        assertThrows(IllegalArgumentException.class, () -> PortfolioReconciliation.reconcile(
                List.of("AAB ten"), List.of(), List.of()), "ilość nie jest liczbą");
        assertThrows(IllegalArgumentException.class, () -> PortfolioReconciliation.reconcile(
                List.of(), List.of(), List.of("AAB 1", "AAB 2")), "powtórzony symbol");
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> PortfolioReconciliation.reconcile(null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> PortfolioReconciliation.reconcile(List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> PortfolioReconciliation.reconcile(List.of(), List.of(), null));
        assertThrows(NullPointerException.class, () -> PortfolioReconciliation.reconcile(
                List.of(), Arrays.asList("AAB BY 1 1", null), List.of()));
    }

    @Test
    void overflowIsDetected() {
        assertThrows(ArithmeticException.class, () -> reconcileTransaction("AAB BY " + Long.MAX_VALUE + " 2"), "wartość transakcji");
        assertThrows(ArithmeticException.class, () -> PortfolioReconciliation.reconcile(
                List.of("AAB " + Long.MAX_VALUE), List.of("AAB BY 1 0"), List.of()), "saldo akcji");
        assertThrows(ArithmeticException.class, () -> PortfolioReconciliation.reconcile(
                List.of("AAB " + Long.MIN_VALUE), List.of(), List.of("AAB 1")), "różnica");
    }

    @Test
    void reportedFromIndependentSimulationAlwaysReconciles() {
        Random random = new Random(42);
        for (int round = 0; round < 500; round++) {
            List<String> transactions = randomTransactions(random);
            assertEquals(List.of(), PortfolioReconciliation.reconcile(START, transactions, simulate(transactions)),
                    "transakcje: " + transactions);
        }
    }

    private static List<String> reconcileTransaction(String transaction) {
        return PortfolioReconciliation.reconcile(List.of(), List.of(transaction), List.of());
    }

    private static List<String> randomTransactions(Random random) {
        String[] symbols = {"AAB", "GOOG", "MSFT"};
        List<String> transactions = new ArrayList<>();
        for (int i = random.nextInt(20); i > 0; i--) {
            String side = random.nextBoolean() ? "BY" : "SL";
            transactions.add("%s %s %d %d".formatted(symbols[random.nextInt(3)], side, 1 + random.nextInt(50), random.nextInt(500)));
        }
        return transactions;
    }

    private static List<String> simulate(List<String> transactions) {
        Map<String, Long> balances = new HashMap<>(Map.of("AAB", 10L, "GOOG", 30L, "Cash", 1000L));
        for (String transaction : transactions) {
            String[] fields = transaction.split(" ");
            long quantity = Long.parseLong(fields[2]) * (fields[1].equals("BY") ? 1 : -1);
            balances.merge(fields[0], quantity, Long::sum);
            balances.merge("Cash", -quantity * Long.parseLong(fields[3]), Long::sum);
        }
        return balances.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList();
    }
}
