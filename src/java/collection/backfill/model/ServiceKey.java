package collection.backfill.model;

import java.util.Comparator;
import java.util.Objects;

public record ServiceKey(String msisdn, String serviceCode) implements Comparable<ServiceKey> {
    private static final Comparator<ServiceKey> ORDER =
            Comparator.comparing(ServiceKey::msisdn).thenComparing(ServiceKey::serviceCode);

    public ServiceKey {
        Objects.requireNonNull(msisdn, "brak numeru abonenta");
        Objects.requireNonNull(serviceCode, "brak kodu usługi");
    }

    @Override
    public int compareTo(ServiceKey other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return msisdn + "/" + serviceCode;
    }
}
