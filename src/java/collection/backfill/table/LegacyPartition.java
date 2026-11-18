package collection.backfill.table;

import java.util.List;

import collection.backfill.model.LegacyServiceRow;
import collection.backfill.model.ServiceRow;

public record LegacyPartition(List<LegacyServiceRow> rows) implements Partition {
    public LegacyPartition {
        rows = Rows.requireRows(rows);
        Rows.requireUniqueKeys(rows.stream().map(LegacyServiceRow::upcast).toList());
    }

    @Override
    public List<ServiceRow> read() {
        return rows.stream().map(LegacyServiceRow::upcast).toList();
    }

    @Override
    public int size() {
        return rows.size();
    }
}
