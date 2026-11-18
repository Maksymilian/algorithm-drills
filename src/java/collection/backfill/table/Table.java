package collection.backfill.table;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import collection.backfill.model.ServiceRow;

public final class Table {
    private final ConcurrentMap<YearMonth, Partition> partitions = new ConcurrentSkipListMap<>();
    private final ConcurrentMap<YearMonth, Optional<Partition>> replaced = new ConcurrentHashMap<>();

    public void load(YearMonth month, Partition partition) {
        Objects.requireNonNull(month, "brak miesiąca");
        Objects.requireNonNull(partition, "brak partycji");
        if (partitions.putIfAbsent(month, partition) != null) {
            throw new IllegalStateException("partycja %s już istnieje".formatted(month));
        }
    }

    public Optional<Partition> partition(YearMonth month) {
        Objects.requireNonNull(month, "brak miesiąca");
        return Optional.ofNullable(partitions.get(month));
    }

    public List<ServiceRow> read(YearMonth month) {
        return partition(month).map(Partition::read).orElse(List.of());
    }

    public void rollback(YearMonth month) {
        Optional<Partition> previous = takeReplaced(month);
        if (previous.isPresent()) {
            partitions.put(month, previous.get());
        } else {
            partitions.remove(month);
        }
    }

    public int dropReplaced(YearMonth month) {
        return takeReplaced(month).map(Partition::size).orElse(0);
    }

    // podmienia tylko wtedy, gdy partycja jest wciąż tą, którą przeliczaliśmy (compare-and-swap)
    public boolean swap(YearMonth month, Optional<Partition> expected, Partition replacement) {
        Objects.requireNonNull(month, "brak miesiąca");
        Objects.requireNonNull(expected, "brak oczekiwanej wersji");
        Objects.requireNonNull(replacement, "brak nowej partycji");
        boolean swapped = expected.isPresent()
                ? partitions.replace(month, expected.get(), replacement)
                : partitions.putIfAbsent(month, replacement) == null;
        if (swapped) {
            replaced.putIfAbsent(month, expected);
        }
        return swapped;
    }

    private Optional<Partition> takeReplaced(YearMonth month) {
        Objects.requireNonNull(month, "brak miesiąca");
        Optional<Partition> previous = replaced.remove(month);
        if (previous == null) {
            throw new IllegalStateException("partycja %s nie ma poprzedniej wersji".formatted(month));
        }
        return previous;
    }
}
