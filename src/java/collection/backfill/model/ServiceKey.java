package collection.backfill.model;

import java.util.Comparator;

public record ServiceKey(String msisdn, String serviceCode) implements Comparable<ServiceKey> {
    private static final Comparator<ServiceKey> ORDER =
            Comparator.comparing(ServiceKey::msisdn).thenComparing(ServiceKey::serviceCode);

    @Override
    public int compareTo(ServiceKey other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return msisdn + "/" + serviceCode;
    }
}
