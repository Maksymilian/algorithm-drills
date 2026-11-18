package collection.backfill.model;

import java.util.Objects;

public record ServiceRow(String msisdn, String serviceCode, String offerName, Status status,
                         Channel activationChannel) {
    public ServiceRow {
        Objects.requireNonNull(msisdn, "brak numeru abonenta");
        Objects.requireNonNull(serviceCode, "brak kodu usługi");
        Objects.requireNonNull(offerName, "brak nazwy oferty");
        Objects.requireNonNull(status, "brak statusu");
        Objects.requireNonNull(activationChannel, "brak kanału aktywacji");
    }

    public ServiceKey key() {
        return new ServiceKey(msisdn, serviceCode);
    }
}
