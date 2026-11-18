package collection.backfill;

import java.util.List;

import collection.backfill.model.ServiceKey;
import collection.backfill.notification.StatusCorrection;

record Changes(int nameFixes, List<StatusCorrection> corrections, int addedRows, List<ServiceKey> lostRows) {
    boolean touchData() {
        return nameFixes > 0 || !corrections.isEmpty() || addedRows > 0 || !lostRows.isEmpty();
    }
}
