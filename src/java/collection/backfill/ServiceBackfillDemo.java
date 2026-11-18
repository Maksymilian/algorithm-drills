package collection.backfill;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import collection.backfill.model.Channel;
import collection.backfill.model.EventType;
import collection.backfill.model.ProvisioningEvent;
import collection.backfill.notification.Notification;
import collection.backfill.table.Table;

final class ServiceBackfillDemo {
    static final YearMonth FIRST = YearMonth.of(2025, 9);
    static final YearMonth NOW = YearMonth.of(2026, 11);
    // w demo partycje mają po kilka wierszy, więc jedna poprawka to już 20–33% partycji
    static final Policy POLICY = new Policy(Policy.DEFAULT.backfillWindowMonths(), 50);
    static final Map<String, String> OFFER_NAMES = Map.of(
            "ROAMING_EU", "Roaming Europa – zniżka świąteczna",
            "ESIM_WATCH", "eSIM w zegarku – łączność bez telefonu",
            "DATA_10GB", "Pakiet danych 10 GB – ważny 30 dni");

    private ServiceBackfillDemo() {
    }

    static void run() {
        List<ProvisioningEvent> events = events();
        Table table = legacyTable(events);
        ServiceBackfill job = new ServiceBackfill(table, events, NOW, POLICY, ServiceBackfillDemo::sendSms);
        List<PartitionPlan> plan = printPlan("Plan na sucho, nic jeszcze nie zapisano:", job);
        Report corrections = printRun("Backfill tylko partycji z poprawkami danych:", job,
                monthsWith(plan, Action.CORRECTION));
        Report schema = printRun("Sama zmiana schematu, bez powiadomień:", job, monthsWith(plan, Action.SCHEMA_ONLY));
        printPlan("Plan po backfillu:", job);
        dropReplaced(table, corrections, schema);
    }

    static List<ProvisioningEvent> events() {
        return Stream.of(untouchedSubscriber(), frozenOutOfOrder(), shopActivationArrivedAfterCancel(),
                        shopSuspendArrivedAfterResume(), shopNameReplacedNextMonth())
                .flatMap(List::stream)
                .toList();
    }

    static Table legacyTable(List<ProvisioningEvent> events) {
        Table table = new Table();
        for (YearMonth month = FIRST; month.isBefore(NOW); month = month.plusMonths(1)) {
            table.load(month, ServiceBackfill.legacySnapshot(events, month));
        }
        table.load(NOW, ServiceBackfill.currentSnapshot(events, NOW));
        return table;
    }

    static List<PartitionPlan> printPlan(String title, ServiceBackfill job) {
        IO.println(title);
        List<PartitionPlan> plan = job.plan(FIRST, NOW);
        plan.forEach(partition -> IO.println("  " + describe(partition)));
        return plan;
    }

    static Report printRun(String title, ServiceBackfill job, List<YearMonth> months) {
        IO.println(title);
        Report report = job.backfill(months);
        IO.println("  podmienione %s, odrzucone %s, powiadomienia: %d".formatted(
                months(report.applied()), months(report.rejected()), report.notifications().size()));
        return report;
    }

    static void dropReplaced(Table table, Report... reports) {
        int dropped = Stream.of(reports)
                .flatMap(report -> report.applied().stream())
                .mapToInt(plan -> table.dropReplaced(plan.month()))
                .sum();
        IO.println("Usunięte stare wersje partycji: %d wierszy w starym schemacie".formatted(dropped));
    }

    static void sendSms(Notification notification) {
        IO.println("  SMS do %s: %s".formatted(notification.msisdn(), notification.message()));
    }

    static List<YearMonth> monthsWith(List<PartitionPlan> plan, Action action) {
        return plan.stream().filter(partition -> partition.action() == action).map(PartitionPlan::month).toList();
    }

    private static List<YearMonth> months(List<PartitionPlan> plans) {
        return plans.stream().map(PartitionPlan::month).toList();
    }

    private static String describe(PartitionPlan plan) {
        return "%s %-11s wiersze %d → %d, statusy %d, nazwy %d%s".formatted(plan.month(), plan.action(),
                plan.rowsBefore(), plan.rowsAfter(), plan.corrections().size(), plan.nameFixes(),
                plan.problems().isEmpty() ? "" : ", problemy: " + plan.problems());
    }

    private static List<ProvisioningEvent> untouchedSubscriber() {
        return List.of(event(1, "2025-09-03T10:00", "48600100200", "DATA_10GB", EventType.ACTIVATE, Channel.APP));
    }

    private static List<ProvisioningEvent> frozenOutOfOrder() {
        return List.of(
                event(3, "2025-10-01T09:00", "48600100300", "ROAMING_EU", EventType.ACTIVATE, Channel.APP),
                event(5, "2025-10-05T12:00", "48600100300", "ROAMING_EU", EventType.SUSPEND, Channel.SHOP),
                event(4, "2025-10-05T19:00", "48600100300", "ROAMING_EU", EventType.RESUME, Channel.APP),
                event(6, "2025-11-03T08:00", "48600100300", "ROAMING_EU", EventType.CANCEL, Channel.APP));
    }

    private static List<ProvisioningEvent> shopActivationArrivedAfterCancel() {
        return List.of(
                event(21, "2026-03-02T10:00", "48600100400", "ESIM_WATCH", EventType.ACTIVATE, Channel.SHOP),
                event(20, "2026-03-28T18:00", "48600100400", "ESIM_WATCH", EventType.CANCEL, Channel.APP),
                event(40, "2026-05-04T11:00", "48600100400", "ESIM_WATCH", EventType.ACTIVATE, Channel.APP));
    }

    private static List<ProvisioningEvent> shopSuspendArrivedAfterResume() {
        return List.of(
                event(50, "2026-06-01T08:00", "48600100500", "ROAMING_EU", EventType.ACTIVATE, Channel.APP),
                event(52, "2026-06-10T12:00", "48600100500", "ROAMING_EU", EventType.SUSPEND, Channel.SHOP),
                event(51, "2026-06-10T18:00", "48600100500", "ROAMING_EU", EventType.RESUME, Channel.APP),
                event(60, "2026-07-15T09:00", "48600100500", "ROAMING_EU", EventType.CANCEL, Channel.APP));
    }

    private static List<ProvisioningEvent> shopNameReplacedNextMonth() {
        return List.of(
                event(70, "2026-09-15T16:00", "48600100600", "DATA_10GB", EventType.ACTIVATE, Channel.SHOP),
                event(80, "2026-10-01T07:00", "48600100600", "DATA_10GB", EventType.ACTIVATE, Channel.APP));
    }

    private static ProvisioningEvent event(long sequence, String warsawTime, String msisdn, String service,
                                           EventType type, Channel channel) {
        Instant occurredAt = LocalDateTime.parse(warsawTime).atZone(ServiceBackfill.ZONE).toInstant();
        String offerName = OFFER_NAMES.get(service);
        String sent = channel == Channel.SHOP ? readAsLatin1(offerName) : offerName;
        return new ProvisioningEvent(sequence, occurredAt, msisdn, service, type, channel, sent);
    }

    // tak kasa w sklepie psuje nazwę: bajty UTF-8 odczytane jako ISO-8859-1
    private static String readAsLatin1(String text) {
        return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }
}
