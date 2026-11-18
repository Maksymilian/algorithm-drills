package collection.backfill.notification;

import java.util.List;
import java.util.stream.Collectors;

public record Notification(String msisdn, List<StatusCorrection> corrections) {
    public Notification {
        corrections = List.copyOf(corrections);
    }

    public String message() {
        return corrections.stream()
                .map(StatusCorrection::describe)
                .collect(Collectors.joining("; ", "Poprawiliśmy status Twoich usług: ", "."));
    }
}
