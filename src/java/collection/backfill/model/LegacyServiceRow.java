package collection.backfill.model;

import java.util.Objects;

public record LegacyServiceRow(String msisdn, String serviceCode, String planName, Status status,
                               int legacyStatusCode) {
    public LegacyServiceRow {
        Objects.requireNonNull(msisdn, "brak numeru abonenta");
        Objects.requireNonNull(serviceCode, "brak kodu usługi");
        Objects.requireNonNull(planName, "brak nazwy planu");
        Objects.requireNonNull(status, "brak statusu");
    }

    public ServiceRow upcast() {
        return new ServiceRow(msisdn, serviceCode, planName, status, Channel.UNKNOWN);
    }
}
