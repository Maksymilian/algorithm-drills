package collection.backfill.table;

import java.util.List;

import collection.backfill.model.ServiceRow;

public record CurrentPartition(List<ServiceRow> rows) implements Partition {
    public CurrentPartition {
        rows = Rows.requireRows(rows);
        Rows.requireUniqueKeys(rows);
    }

    @Override
    public List<ServiceRow> read() {
        return rows;
    }

    @Override
    public int size() {
        return rows.size();
    }
}
