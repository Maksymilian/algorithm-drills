package collection.backfill;

import java.time.YearMonth;

import collection.backfill.model.ServiceRow;
import collection.backfill.notification.StatusCorrection;

record RowPair(ServiceRow before, ServiceRow after) {
    boolean nameChanged() {
        return !before.offerName().equals(after.offerName());
    }

    boolean statusChanged() {
        return before.status() != after.status();
    }

    StatusCorrection correction(YearMonth month) {
        return new StatusCorrection(month, after.msisdn(), after.serviceCode(), after.offerName(),
                before.status(), after.status());
    }
}
