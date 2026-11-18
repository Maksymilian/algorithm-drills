package collection.backfill.notification;

import java.time.YearMonth;

import collection.backfill.model.Status;

public record StatusCorrection(YearMonth month, String msisdn, String serviceCode, String offerName,
                               Status before, Status after) {

    String describe() {
        return "%s w %s: %s → %s".formatted(offerName, month, before.label(), after.label());
    }
}
