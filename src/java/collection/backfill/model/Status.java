package collection.backfill.model;

public enum Status {
    ACTIVE(1, "aktywna"),
    SUSPENDED(2, "zawieszona"),
    CANCELLED(3, "anulowana");

    private final int legacyCode;
    private final String label;

    Status(int legacyCode, String label) {
        this.legacyCode = legacyCode;
        this.label = label;
    }

    public int legacyCode() {
        return legacyCode;
    }

    public String label() {
        return label;
    }
}
