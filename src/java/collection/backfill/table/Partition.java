package collection.backfill.table;

import java.util.List;

import collection.backfill.model.ServiceRow;

public sealed interface Partition permits LegacyPartition, CurrentPartition {
    List<ServiceRow> read();

    int size();
}
