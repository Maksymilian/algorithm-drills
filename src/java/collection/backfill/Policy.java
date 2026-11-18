package collection.backfill;

public record Policy(int backfillWindowMonths, int maxStatusChangePercent) {
    private static final int PERCENT = 100;
    private static final int DEFAULT_WINDOW_MONTHS = 12;
    private static final int DEFAULT_MAX_STATUS_CHANGE_PERCENT = 20;

    public static final Policy DEFAULT = new Policy(DEFAULT_WINDOW_MONTHS, DEFAULT_MAX_STATUS_CHANGE_PERCENT);

    public Policy {
        if (backfillWindowMonths < 1) {
            throw new IllegalArgumentException("okno backfillu to co najmniej miesiąc: " + backfillWindowMonths);
        }
        if (maxStatusChangePercent < 0 || maxStatusChangePercent > PERCENT) {
            throw new IllegalArgumentException("próg zmian statusu to 0–100%: " + maxStatusChangePercent);
        }
    }

    boolean allowsStatusChanges(int changed, int rows) {
        return (long) changed * PERCENT <= (long) maxStatusChangePercent * rows;
    }
}
