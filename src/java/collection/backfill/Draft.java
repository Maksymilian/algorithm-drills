package collection.backfill;

import java.util.Optional;

import collection.backfill.table.CurrentPartition;
import collection.backfill.table.Partition;

record Draft(PartitionPlan plan, Optional<Partition> before, CurrentPartition after) {
}
