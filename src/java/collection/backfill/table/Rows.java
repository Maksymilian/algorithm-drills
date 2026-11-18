package collection.backfill.table;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import collection.backfill.model.ServiceKey;
import collection.backfill.model.ServiceRow;

final class Rows {

    private Rows() {
    }

    static <T> List<T> requireRows(List<T> rows) {
        Objects.requireNonNull(rows, "brak wierszy partycji");
        for (T row : rows) {
            Objects.requireNonNull(row, "brak wiersza w partycji");
        }
        return List.copyOf(rows);
    }

    static void requireUniqueKeys(List<ServiceRow> rows) {
        Set<ServiceKey> keys = new HashSet<>();
        for (ServiceRow row : rows) {
            if (!keys.add(row.key())) {
                throw new IllegalArgumentException("powtórzony wiersz w partycji: " + row.key());
            }
        }
    }
}
