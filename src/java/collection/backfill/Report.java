package collection.backfill;

import java.util.List;

import collection.backfill.notification.Notification;

public record Report(List<PartitionPlan> applied, List<PartitionPlan> skipped, List<PartitionPlan> rejected,
                     List<Notification> notifications, List<Notification> undelivered) {
    public Report {
        applied = List.copyOf(applied);
        skipped = List.copyOf(skipped);
        rejected = List.copyOf(rejected);
        notifications = List.copyOf(notifications);
        undelivered = List.copyOf(undelivered);
    }
}
