package collection.backfill.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ProvisioningEvent(long sequence, Instant occurredAt, String msisdn, String serviceCode,
                                EventType type, Channel channel, String offerName) {
    private static final Pattern MSISDN = Pattern.compile("\\d{9,15}");

    public ProvisioningEvent {
        Objects.requireNonNull(occurredAt, "brak czasu zdarzenia");
        Objects.requireNonNull(msisdn, "brak numeru abonenta");
        Objects.requireNonNull(serviceCode, "brak kodu usługi");
        Objects.requireNonNull(type, "brak rodzaju zdarzenia");
        Objects.requireNonNull(channel, "brak kanału");
        Objects.requireNonNull(offerName, "brak nazwy oferty");
        requireFields(sequence, msisdn, serviceCode, channel, offerName);
    }

    public ServiceKey key() {
        return new ServiceKey(msisdn, serviceCode);
    }

    private static void requireFields(long sequence, String msisdn, String serviceCode, Channel channel,
                                      String offerName) {
        requireFormat(sequence >= 0, "numer zdarzenia nie może być ujemny: " + sequence);
        requireFormat(MSISDN.matcher(msisdn).matches(), "numer abonenta to 9–15 cyfr: " + msisdn);
        requireFormat(!serviceCode.isBlank(), "pusty kod usługi");
        requireFormat(channel != Channel.UNKNOWN, "zdarzenie musi mieć znany kanał");
        requireFormat(!offerName.isBlank(), "pusta nazwa oferty");
    }

    private static void requireFormat(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
