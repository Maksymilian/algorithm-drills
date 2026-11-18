package collection.backfill;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import collection.backfill.notification.StatusCorrection;

public record PartitionPlan(YearMonth month, Action action, int rowsBefore, int rowsAfter, int nameFixes,
                            List<StatusCorrection> corrections, List<String> problems) {
    public PartitionPlan {
        corrections = List.copyOf(corrections);
        problems = List.copyOf(problems);
    }

    PartitionPlan withProblem(String problem) {
        List<String> all = Stream.concat(problems.stream(), Stream.of(problem)).toList();
        return new PartitionPlan(month, action, rowsBefore, rowsAfter, nameFixes, corrections, all);
    }
}
