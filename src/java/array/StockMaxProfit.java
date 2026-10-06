package array;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/// Największy możliwy zysk z jednego kupna i jednej sprzedaży akcji.
///
/// **Treść.** Mamy notowania jednej spółki dzień po dniu. Kupujemy raz i sprzedajemy raz,
/// później niż kupiliśmy. Zysk to cena sprzedaży minus cena kupna. Dla
/// `[7.20, 1.10, 5.35, 3.00, 6.15, 4.00]` najlepiej kupić za 1.10 i sprzedać za 6.15, z zyskiem
/// **5.05**.
///
/// **Pomysł.** Jeśli sprzedajemy dnia `i`, to najlepiej kupić po najniższej cenie
/// sprzed dnia `i`. Idziemy więc raz od lewej, pamiętając dotychczasowe minimum i najlepszą
/// różnicę. Podejście „max − min” jest błędne, bo maksimum może przypaść przed minimum.
///
/// **Dlaczego [BigDecimal].** Ceny to pieniądze. `double` nie zapisze dokładnie
/// większości ułamków dziesiętnych (`0.1 + 0.2 == 0.30000000000000004`), a błędy sumują się
/// przy wielu transakcjach. Trzy zasady:
///
/// - tworzymy z napisu: `new BigDecimal("0.10")`, nie `new BigDecimal(0.1)`, które
///    przenosi błąd `double` (`0.1000000000000000055511151231257827…`);
///
/// - porównujemy przez `compareTo`, `min` i `max`, nie przez `equals`:
///    `equals` uwzględnia skalę, więc `5.0` i `5.00` są „różne”;
///
/// - to dotyczy też [Trade]: rekord porównuje zysk przez `equals`, więc w testach
///    oczekiwany zysk musi mieć tę samą skalę co ceny.
///
/// **Złożoność.** O(n) operacji na `BigDecimal`, O(1) dodatkowej pamięci. Naiwnie, każda
/// para dni, O(n²). Dostęp przez `get(i)`, więc lista powinna mieć szybki dostęp po indeksie
/// (`List.of`, `ArrayList`).
///
/// **Dopytania.**
///
/// - Ceny tylko spadają: zwracamy 0 (nie handlujemy). Jeśli transakcja jest obowiązkowa,
///    najlepszy zysk startuje od różnicy pierwszych dwóch dni i może być ujemny.
///
/// - Dowolnie wiele transakcji: suma wszystkich dodatnich różnic między kolejnymi dniami
///    ([#maxProfitManyTrades]).
///
/// - Najwyżej k transakcji: programowanie dynamiczne, O(n · k).
public class StockMaxProfit {

    public record Trade(int buyDay, int sellDay, BigDecimal profit) {}

    public static BigDecimal maxProfit(List<BigDecimal> prices) {
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal lowest = prices.getFirst(), best = BigDecimal.ZERO;
        for (BigDecimal price : prices) {
            lowest = lowest.min(price);
            best = best.max(price.subtract(lowest));
        }
        return best;
    }

    public static Trade bestTrade(List<BigDecimal> prices) {
        Trade best = new Trade(-1, -1, BigDecimal.ZERO);
        int lowestDay = 0;
        for (int day = 1; day < prices.size(); day++) {
            BigDecimal profit = prices.get(day).subtract(prices.get(lowestDay));
            if (profit.compareTo(best.profit()) > 0) {
                best = new Trade(lowestDay, day, profit);
            }
            lowestDay = prices.get(day).compareTo(prices.get(lowestDay)) < 0 ? day : lowestDay;
        }
        return best;
    }

    public static BigDecimal maxProfitManyTrades(List<BigDecimal> prices) {
        BigDecimal total = BigDecimal.ZERO;
        for (int day = 1; day < prices.size(); day++) {
            total = total.add(prices.get(day).subtract(prices.get(day - 1)).max(BigDecimal.ZERO));
        }
        return total;
    }

    public static List<BigDecimal> prices(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    void main() {
        List<BigDecimal> prices = prices("7.20", "1.10", "5.35", "3.00", "6.15", "4.00");
        IO.println(maxProfit(prices));             // 5.05
        IO.println(bestTrade(prices));             // Trade[buyDay=1, sellDay=4, profit=5.05]
        IO.println(maxProfitManyTrades(prices));   // 7.40 = (5.35 − 1.10) + (6.15 − 3.00)
    }
}
