package array;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockMaxProfitTest {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "oczekiwano " + expected + ", jest " + actual);
    }

    @Test
    void buyLowSellHighLater() {
        List<BigDecimal> prices = StockMaxProfit.prices("7.20", "1.10", "5.35", "3.00", "6.15", "4.00");
        assertAmount("5.05", StockMaxProfit.maxProfit(prices));
        assertEquals(new StockMaxProfit.Trade(1, 4, new BigDecimal("5.05")), StockMaxProfit.bestTrade(prices));
        assertAmount("7.40", StockMaxProfit.maxProfitManyTrades(prices));
    }

    @Test
    void maximumBeforeMinimumIsNotAProfit() {
        List<BigDecimal> falling = StockMaxProfit.prices("5", "4", "3");
        assertAmount("2", StockMaxProfit.maxProfit(StockMaxProfit.prices("9", "8", "2", "4", "1")));
        assertAmount("0", StockMaxProfit.maxProfit(falling));
        assertEquals(new StockMaxProfit.Trade(-1, -1, BigDecimal.ZERO), StockMaxProfit.bestTrade(falling));
        assertAmount("0", StockMaxProfit.maxProfit(List.of()));
    }

    @Test
    void decimalPricesAreExact() {
        List<BigDecimal> prices = StockMaxProfit.prices("0.10", "0.30", "0.20", "0.40");
        assertAmount("0.30", StockMaxProfit.maxProfit(prices));
        assertAmount("0.40", StockMaxProfit.maxProfitManyTrades(prices));
    }

    @Test
    void onePassAgreesWithEveryPair() {
        Random random = new Random(2);
        for (int round = 0; round < 500; round++) {
            List<BigDecimal> prices = randomPrices(random, random.nextInt(12));
            BigDecimal expected = bestProfitOfEveryPair(prices);
            assertEquals(expected, StockMaxProfit.maxProfit(prices), prices.toString());
            assertEquals(expected, StockMaxProfit.bestTrade(prices).profit(), prices.toString());
        }
    }

    private static List<BigDecimal> randomPrices(Random random, int days) {
        return random.ints(days, 1, 10_001).mapToObj(cents -> BigDecimal.valueOf(cents, 2)).toList();
    }

    private static BigDecimal bestProfitOfEveryPair(List<BigDecimal> prices) {
        BigDecimal best = BigDecimal.ZERO;
        for (int buy = 0; buy < prices.size(); buy++) {
            for (int sell = buy + 1; sell < prices.size(); sell++) {
                best = best.max(prices.get(sell).subtract(prices.get(buy)));
            }
        }
        return best;
    }
}
