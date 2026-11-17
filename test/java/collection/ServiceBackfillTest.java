package collection;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import collection.ServiceBackfill.Action;
import collection.ServiceBackfill.Channel;
import collection.ServiceBackfill.CurrentPartition;
import collection.ServiceBackfill.EventType;
import collection.ServiceBackfill.LegacyPartition;
import collection.ServiceBackfill.LegacyServiceRow;
import collection.ServiceBackfill.Notification;
import collection.ServiceBackfill.Partition;
import collection.ServiceBackfill.PartitionPlan;
import collection.ServiceBackfill.Policy;
import collection.ServiceBackfill.ProvisioningEvent;
import collection.ServiceBackfill.Report;
import collection.ServiceBackfill.ServiceRow;
import collection.ServiceBackfill.Status;
import collection.ServiceBackfill.StatusCorrection;
import collection.ServiceBackfill.Table;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceBackfillTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final YearMonth FIRST = YearMonth.of(2025, 9);
    private static final YearMonth OCTOBER_2025 = YearMonth.of(2025, 10);
    private static final YearMonth NOVEMBER_2025 = YearMonth.of(2025, 11);
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final YearMonth MAY = YearMonth.of(2026, 5);
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOW = YearMonth.of(2026, 11);
    private static final Policy LENIENT = new Policy(12, 50);
    private static final Policy NO_LIMIT = new Policy(12, 100);

    private static final String ROAMING = "Roaming Europa – zniżka świąteczna";
    private static final String ESIM = "eSIM w zegarku – łączność bez telefonu";
    private static final String DATA = "Pakiet danych 10 GB – ważny 30 dni";
    private static final Map<String, String> OFFER_NAMES = Map.of("ROAMING_EU", ROAMING, "ESIM_WATCH", ESIM,
            "DATA_10GB", DATA);

    private static final String UNTOUCHED = "48600100200";
    private static final String FROZEN_OUT_OF_ORDER = "48600100300";
    private static final String ACTIVATED_AFTER_CANCEL = "48600100400";
    private static final String SUSPENDED_AFTER_RESUME = "48600100500";
    private static final String NAME_FIXED_ONLY = "48600100600";
    private static final String STATIC_MSISDN = "48600100100";

    private static final YearMonth RANDOM_FIRST = YearMonth.of(2026, 3);
    private static final YearMonth RANDOM_NOW = YearMonth.of(2026, 9);
    private static final Instant RANDOM_START = RANDOM_FIRST.atDay(1).atStartOfDay(WARSAW).toInstant();
    private static final int RANDOM_DAYS = 183;
    private static final Comparator<ProvisioningEvent> ARRIVAL = Comparator.comparingLong(ProvisioningEvent::sequence);
    private static final Comparator<ProvisioningEvent> OCCURRENCE =
            Comparator.comparing(ProvisioningEvent::occurredAt).thenComparing(ARRIVAL);
    private static final String POLISH_ALPHABET = "abcxyzABC ąćęłńóśźżĄĆĘŁŃÓŚŹŻ–";
    private static final int MIN_SWAPS = 300;
    private static final int MIN_READS = 1_000;

    private static final List<Action> DEMO_ACTIONS = List.of(
            Action.FROZEN, Action.FROZEN,
            Action.SCHEMA_ONLY, Action.SCHEMA_ONLY, Action.SCHEMA_ONLY, Action.SCHEMA_ONLY,
            Action.CORRECTION, Action.CORRECTION, Action.SCHEMA_ONLY, Action.CORRECTION,
            Action.SCHEMA_ONLY, Action.SCHEMA_ONLY, Action.CORRECTION, Action.SCHEMA_ONLY,
            Action.OPEN);

    private final List<ProvisioningEvent> events = demoEvents();
    private final Table table = demoTable(events);
    private final List<Notification> sent = new ArrayList<>();
    private final ServiceBackfill job = new ServiceBackfill(table, events, NOW, LENIENT, sent::add);

    @Test
    void repairTextUndoesUtf8ReadAsLatin1() {
        for (String text : List.of(ROAMING, ESIM, "ąćęłńóśźż ĄĆĘŁŃÓŚŹŻ", "Łódź", "Pakiet 🎉", "日本")) {
            assertEquals(text, ServiceBackfill.repairText(readAsLatin1(text)), "naprawa: " + text);
        }
    }

    @Test
    void repairTextLeavesCorrectTextUnchanged() {
        for (String text : List.of("", "ASCII", "Łódź", "Café", "Ó", "óó", "zniżka", "Pakiet 🎉", "\u0080")) {
            assertEquals(text, ServiceBackfill.repairText(text), "poprawny napis zostaje: " + text);
        }
    }

    @Test
    void repairTextRemovesOnlyOneLayerOfMangling() {
        String twice = readAsLatin1(readAsLatin1(ESIM));
        assertEquals(readAsLatin1(ESIM), ServiceBackfill.repairText(twice), "zostaje jedna warstwa");
    }

    @Test
    void repairTextOnRandomPolishTextUndoesManglingAndKeepsCorrectText() {
        Random random = new Random(42);
        for (int round = 0; round < 2_000; round++) {
            String text = randomPolishText(random);
            assertEquals(text, ServiceBackfill.repairText(text), "poprawny: " + text);
            assertEquals(text, ServiceBackfill.repairText(readAsLatin1(text)), "zepsuty: " + text);
        }
    }

    @Test
    void legacyLogicAppliesArrivalOrderAndCurrentLogicOccurrenceOrder() {
        LegacyServiceRow legacy = ServiceBackfill.legacySnapshot(events, MARCH).rows().get(2);
        ServiceRow current = rowOf(ServiceBackfill.currentSnapshot(events, MARCH).rows(), ACTIVATED_AFTER_CANCEL);
        assertEquals(new LegacyServiceRow(ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", readAsLatin1(ESIM), Status.ACTIVE, 1),
                legacy, "stara logika: anulowanie przyszło przed aktywacją, więc przepadło");
        assertEquals(new ServiceRow(ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", ESIM, Status.CANCELLED, Channel.SHOP),
                current, "nowa logika: aktywacja 2 marca, anulowanie 28 marca");
    }

    @Test
    void suspendFromShopArrivingAfterResumeLeavesServiceActive() {
        assertEquals(Status.SUSPENDED, legacyStatus(events, JUNE, SUSPENDED_AFTER_RESUME), "stara logika");
        assertEquals(Status.ACTIVE, currentStatus(events, JUNE, SUSPENDED_AFTER_RESUME), "nowa logika");
        assertEquals(Status.CANCELLED, legacyStatus(events, YearMonth.of(2026, 7), SUSPENDED_AFTER_RESUME),
                "od lipca obie logiki dają to samo");
    }

    @Test
    void eventsWithTheSameTimeAreOrderedBySequence() {
        List<ProvisioningEvent> activateFirst = List.of(atNoon(1, EventType.ACTIVATE), atNoon(2, EventType.CANCEL));
        List<ProvisioningEvent> cancelFirst = List.of(atNoon(2, EventType.ACTIVATE), atNoon(1, EventType.CANCEL));
        assertEquals(Status.CANCELLED, currentStatus(activateFirst, MARCH, UNTOUCHED));
        assertEquals(Status.ACTIVE, currentStatus(cancelFirst, MARCH, UNTOUCHED), "anulowanie przed aktywacją pomijamy");
    }

    @Test
    void statusFollowsTheStateMachine() {
        List<ProvisioningEvent> log = List.of(
                monthly(1, 1, EventType.SUSPEND), monthly(2, 2, EventType.ACTIVATE), monthly(3, 3, EventType.RESUME),
                monthly(4, 4, EventType.SUSPEND), monthly(5, 5, EventType.SUSPEND), monthly(6, 6, EventType.CANCEL),
                monthly(7, 7, EventType.RESUME), monthly(8, 8, EventType.SUSPEND), monthly(9, 9, EventType.CANCEL));
        List<Status> byMonth = IntStream.rangeClosed(2, 9)
                .mapToObj(month -> currentStatus(log, YearMonth.of(2026, month), UNTOUCHED))
                .toList();
        assertEquals(List.of(Status.ACTIVE, Status.ACTIVE, Status.SUSPENDED, Status.SUSPENDED, Status.CANCELLED,
                Status.CANCELLED, Status.CANCELLED, Status.CANCELLED), byMonth);
        assertEquals(List.of(), ServiceBackfill.currentSnapshot(log, YearMonth.of(2026, 1)).rows(), "brak aktywacji");
    }

    @Test
    void reactivationAfterCancelTakesNameAndChannelFromTheNewActivation() {
        ServiceRow row = rowOf(ServiceBackfill.currentSnapshot(events, MAY).rows(), ACTIVATED_AFTER_CANCEL);
        assertEquals(new ServiceRow(ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", ESIM, Status.ACTIVE, Channel.APP), row);
    }

    @Test
    void partitionBoundaryIsMidnightInWarsaw() {
        ProvisioningEvent lastSecondOfMarch = new ProvisioningEvent(1, Instant.parse("2026-03-31T21:59:59Z"),
                UNTOUCHED, "DATA_10GB", EventType.ACTIVATE, Channel.APP, DATA);
        ProvisioningEvent firstSecondOfApril = new ProvisioningEvent(2, Instant.parse("2026-03-31T22:00:00Z"),
                NAME_FIXED_ONLY, "DATA_10GB", EventType.ACTIVATE, Channel.APP, DATA);
        ProvisioningEvent firstSecondOfFebruary = new ProvisioningEvent(3, Instant.parse("2026-01-31T23:00:00Z"),
                STATIC_MSISDN, "DATA_10GB", EventType.ACTIVATE, Channel.APP, DATA);
        List<ProvisioningEvent> log = List.of(lastSecondOfMarch, firstSecondOfApril, firstSecondOfFebruary);
        assertEquals(List.of(), subscribers(log, YearMonth.of(2026, 1)), "1 lutego 00:00 czasu zimowego");
        assertEquals(List.of(STATIC_MSISDN, UNTOUCHED), subscribers(log, MARCH), "31 marca 23:59:59 czasu letniego");
        assertEquals(List.of(STATIC_MSISDN, UNTOUCHED, NAME_FIXED_ONLY), subscribers(log, APRIL));
    }

    @Test
    void planDeterminesWhichPartitionsAreAffected() {
        List<PartitionPlan> plan = job.plan(FIRST, NOW);
        assertEquals(DEMO_ACTIONS, plan.stream().map(PartitionPlan::action).toList());
        assertEquals(List.of(MARCH, APRIL, JUNE, SEPTEMBER), monthsWith(plan, Action.CORRECTION));
        assertEquals(List.of(1, 1, 1, 0), correctionCounts(plan, Action.CORRECTION), "zmiany statusu");
        assertEquals(List.of(1, 1, 0, 1), nameFixes(plan, Action.CORRECTION), "naprawione nazwy");
    }

    @Test
    void planShowsCorrectionInFrozenPartitionButBackfillRefusesIt() {
        PartitionPlan frozen = job.plan(OCTOBER_2025, OCTOBER_2025).getFirst();
        assertEquals(Action.FROZEN, frozen.action());
        assertEquals(List.of(new StatusCorrection(OCTOBER_2025, FROZEN_OUT_OF_ORDER, "ROAMING_EU", ROAMING,
                Status.SUSPENDED, Status.ACTIVE)), frozen.corrections(), "błąd sięga zamrożonej partycji");
        assertThrows(IllegalArgumentException.class, () -> job.backfill(List.of(OCTOBER_2025)));
    }

    @Test
    void planWindowBoundaries() {
        ServiceBackfill empty = new ServiceBackfill(new Table(), List.of(), NOW, Policy.DEFAULT, sent::add);
        List<Action> actions = empty.plan(OCTOBER_2025, NOVEMBER_2025).stream().map(PartitionPlan::action).toList();
        assertEquals(List.of(Action.FROZEN, Action.UP_TO_DATE), actions, "okno to 12 ostatnich zamkniętych miesięcy");
        List<Action> latest = empty.plan(OCTOBER, NOW.plusMonths(1)).stream().map(PartitionPlan::action).toList();
        assertEquals(List.of(Action.UP_TO_DATE, Action.OPEN, Action.OPEN), latest, "bieżący i przyszłe są otwarte");
    }

    @Test
    void planDoesNotChangeTheTable() {
        Partition march = table.partition(MARCH).orElseThrow();
        job.plan(FIRST, NOW);
        assertSame(march, table.partition(MARCH).orElseThrow());
        assertEquals(List.of(), sent, "plan nie powiadamia");
    }

    @Test
    void planOfOneMonthAndReversedRange() {
        assertEquals(1, job.plan(MARCH, MARCH).size());
        assertThrows(IllegalArgumentException.class, () -> job.plan(APRIL, MARCH));
    }

    @Test
    void backfillRewritesOnlyTheRequestedPartitions() {
        Report report = job.backfill(List.of(MARCH, APRIL, JUNE, SEPTEMBER));
        assertEquals(List.of(MARCH, APRIL, JUNE, SEPTEMBER), months(report.applied()));
        for (YearMonth month : List.of(MARCH, APRIL, JUNE, SEPTEMBER)) {
            assertEquals(ServiceBackfill.currentSnapshot(events, month), table.partition(month).orElseThrow(), "" + month);
        }
        for (YearMonth month : List.of(FIRST, OCTOBER_2025, NOVEMBER_2025, MAY, OCTOBER)) {
            assertInstanceOf(LegacyPartition.class, table.partition(month).orElseThrow(), "nietknięta " + month);
        }
    }

    @Test
    void backfillNotifiesOnlySubscribersWhoseStatusChanged() {
        Report report = job.backfill(List.of(MARCH, APRIL, JUNE, SEPTEMBER));
        assertEquals(List.of(ACTIVATED_AFTER_CANCEL, SUSPENDED_AFTER_RESUME),
                report.notifications().stream().map(Notification::msisdn).toList(),
                "bez abonenta z samą poprawką nazwy i bez tego z zamrożonej partycji");
        assertEquals(report.notifications(), sent, "wysłane to te same, które są w raporcie");
    }

    @Test
    void notificationGroupsAllCorrectionsOfOneSubscriber() {
        Notification notification = job.backfill(List.of(MARCH, APRIL)).notifications().getFirst();
        assertEquals(1, sent.size(), "jedno powiadomienie mimo dwóch partycji");
        assertEquals("Poprawiliśmy status Twoich usług: " + ESIM + " w 2026-03: aktywna → anulowana; "
                + ESIM + " w 2026-04: aktywna → anulowana.", notification.message());
    }

    @Test
    void schemaOnlyBackfillRenamesAddsAndDropsColumnsWithoutNotifications() {
        Report report = job.backfill(monthsWith(job.plan(FIRST, NOW), Action.SCHEMA_ONLY));
        assertEquals(8, report.applied().size());
        assertEquals(List.of(), sent, "sama zmiana schematu nikogo nie dotyczy");
        CurrentPartition may = assertInstanceOf(CurrentPartition.class, table.partition(MAY).orElseThrow());
        assertEquals(List.of(new ServiceRow(UNTOUCHED, "DATA_10GB", DATA, Status.ACTIVE, Channel.APP),
                new ServiceRow(FROZEN_OUT_OF_ORDER, "ROAMING_EU", ROAMING, Status.CANCELLED, Channel.APP),
                new ServiceRow(ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", ESIM, Status.ACTIVE, Channel.APP)), may.rows(),
                "planName → offerName, nowy activationChannel, bez legacyStatusCode");
    }

    @Test
    void schemaOnReadUpcastsLegacyRows() {
        assertEquals(new ServiceRow(ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", readAsLatin1(ESIM), Status.ACTIVE,
                Channel.UNKNOWN), rowOf(table.read(MARCH), ACTIVATED_AFTER_CANCEL),
                "planName czytany jako offerName, kanał nieznany");
        assertEquals(List.of(), table.read(YearMonth.of(2020, 1)), "brak partycji czyta się jak pustą");
    }

    @Test
    void backfillIsIdempotent() {
        job.backfill(List.of(MARCH, JUNE));
        Partition march = table.partition(MARCH).orElseThrow();
        sent.clear();
        Report again = job.backfill(List.of(MARCH, JUNE));
        assertEquals(List.of(MARCH, JUNE), months(again.skipped()));
        assertEquals(List.of(), again.applied());
        assertEquals(List.of(), sent, "drugi przebieg nikogo nie powiadamia");
        assertSame(march, table.partition(MARCH).orElseThrow());
    }

    @Test
    void frozenOrOpenMonthRejectsTheWholeRequestBeforeAnyChange() {
        Partition march = table.partition(MARCH).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> job.backfill(List.of(MARCH, OCTOBER_2025)), "zamrożona");
        assertThrows(IllegalArgumentException.class, () -> job.backfill(List.of(MARCH, NOW)), "otwarta");
        assertSame(march, table.partition(MARCH).orElseThrow(), "nic nie zmienione");
        assertEquals(List.of(), sent);
        assertDoesNotThrow(() -> job.backfill(List.of(NOVEMBER_2025, OCTOBER)), "skraje okna");
    }

    @Test
    void duplicateMonthsAreBackfilledOnceAndEmptyListIsANoOp() {
        Report report = job.backfill(List.of(MARCH, MARCH));
        assertEquals(List.of(MARCH), months(report.applied()));
        assertEquals(1, report.notifications().getFirst().corrections().size());
        assertEquals(new Report(List.of(), List.of(), List.of(), List.of()), job.backfill(List.of()));
    }

    @Test
    void missingPartitionIsCreatedWithoutNotifications() {
        Table gaps = new Table();
        ServiceBackfill fill = new ServiceBackfill(gaps, events, NOW, LENIENT, sent::add);
        PartitionPlan plan = fill.plan(MARCH, MARCH).getFirst();
        assertEquals(List.of(Action.CORRECTION, 0, 3), List.of(plan.action(), plan.rowsBefore(), plan.rowsAfter()));
        fill.backfill(List.of(MARCH));
        assertEquals(ServiceBackfill.currentSnapshot(events, MARCH), gaps.partition(MARCH).orElseThrow());
        assertEquals(List.of(), sent, "nikt nie widział brakującej partycji");
    }

    @Test
    void emptyLogAndEmptyTableAreUpToDate() {
        Table empty = new Table();
        Report report = new ServiceBackfill(empty, List.of(), NOW, Policy.DEFAULT, sent::add).backfill(List.of(MARCH));
        assertEquals(List.of(MARCH), months(report.skipped()));
        assertTrue(empty.partition(MARCH).isEmpty(), "pustej partycji nie tworzymy");
    }

    @Test
    void partitionThatWouldLoseRowsIsRejectedAndOthersProceed() {
        LegacyServiceRow orphan = new LegacyServiceRow("48699999999", "ESIM_WATCH", ESIM, Status.ACTIVE, 1);
        Table withOrphan = demoTable(events, List.of(orphan));
        Partition march = withOrphan.partition(MARCH).orElseThrow();
        Report report = new ServiceBackfill(withOrphan, events, NOW, LENIENT, sent::add).backfill(List.of(MARCH, JUNE));
        assertEquals(List.of(MARCH), months(report.rejected()));
        assertEquals(List.of(JUNE), months(report.applied()), "pozostałe partycje idą dalej");
        assertEquals(List.of("wiersz zniknąłby z partycji, bo w dzienniku nie ma jego zdarzeń: 48699999999/ESIM_WATCH"),
                report.rejected().getFirst().problems());
        assertSame(march, withOrphan.partition(MARCH).orElseThrow(), "odrzucona partycja nietknięta");
    }

    @Test
    void tooManyStatusChangesAreRejectedAtTheThreshold() {
        assertEquals(List.of(), backfillWith(new Policy(12, 25), JUNE).rejected(), "1 z 4 to dokładnie 25%");
        Report strict = backfillWith(new Policy(12, 24), JUNE);
        assertEquals(List.of(JUNE), months(strict.rejected()));
        assertEquals(List.of("zmiana statusu w 1 z 4 wierszy przekracza próg 24%"),
                strict.rejected().getFirst().problems());
        assertEquals(List.of(SEPTEMBER), months(backfillWith(Policy.DEFAULT, MARCH, SEPTEMBER).applied()),
                "domyślne 20% odrzuca 1 z 3 w marcu");
    }

    @Test
    void doubleMangledNameIsRejected() {
        ProvisioningEvent broken = new ProvisioningEvent(1, warsaw("2026-03-10T10:00"), UNTOUCHED, "ESIM_WATCH",
                EventType.ACTIVATE, Channel.SHOP, readAsLatin1(readAsLatin1(ESIM)));
        ServiceBackfill fix = new ServiceBackfill(new Table(), List.of(broken), NOW, LENIENT, sent::add);
        Report report = fix.backfill(List.of(MARCH));
        assertEquals(List.of("nazwa oferty nadal z zepsutym kodowaniem: 48600100200/ESIM_WATCH"),
                report.rejected().getFirst().problems());
    }

    @Test
    void rollbackRestoresThePreviousVersion() {
        Partition legacy = table.partition(MARCH).orElseThrow();
        job.backfill(List.of(MARCH));
        table.rollback(MARCH);
        assertSame(legacy, table.partition(MARCH).orElseThrow());
        assertEquals(Action.CORRECTION, job.plan(MARCH, MARCH).getFirst().action(), "znowu do poprawy");
    }

    @Test
    void dropReplacedRemovesTheOldVersionForGood() {
        job.backfill(List.of(MARCH));
        assertEquals(3, table.dropReplaced(MARCH), "usunięte wiersze starej wersji");
        assertThrows(IllegalStateException.class, () -> table.rollback(MARCH), "nie ma już do czego wrócić");
        assertThrows(IllegalStateException.class, () -> table.dropReplaced(MARCH));
        assertInstanceOf(CurrentPartition.class, table.partition(MARCH).orElseThrow());
    }

    @Test
    void rollbackOfCreatedPartitionRemovesIt() {
        Table gaps = new Table();
        new ServiceBackfill(gaps, events, NOW, LENIENT, sent::add).backfill(List.of(MARCH));
        assertEquals(0, gaps.dropReplaced(MARCH), "poprzednio nie było partycji");
        new ServiceBackfill(gaps, events, NOW, LENIENT, sent::add).backfill(List.of(APRIL));
        gaps.rollback(APRIL);
        assertTrue(gaps.partition(APRIL).isEmpty());
    }

    @Test
    void secondBackfillKeepsTheOriginalVersionForRollback() {
        Partition legacy = table.partition(MARCH).orElseThrow();
        job.backfill(List.of(MARCH));
        List<ProvisioningEvent> withLateEvent = new ArrayList<>(events);
        withLateEvent.add(event(99, "2026-03-20T10:00", "48600100900", "DATA_10GB", EventType.ACTIVATE, Channel.SHOP));
        Report late = new ServiceBackfill(table, withLateEvent, NOW, LENIENT, sent::add).backfill(List.of(MARCH));
        assertEquals(List.of(MARCH), months(late.applied()), "spóźnione zdarzenie to kolejna poprawka");
        table.rollback(MARCH);
        assertSame(legacy, table.partition(MARCH).orElseThrow(), "wycofanie wraca do stanu sprzed pierwszego backfillu");
    }

    @Test
    void readersSeeEitherTheWholeOldOrTheWholeNewPartition() {
        Set<List<ServiceRow>> allowed = Set.of(table.read(MARCH), ServiceBackfill.currentSnapshot(events, MARCH).rows());
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<Integer> reader = CompletableFuture.supplyAsync(() -> readUntilStopped(allowed, stop, reads));
        for (int round = 0; round < MIN_SWAPS || reads.get() < MIN_READS; round++) {
            job.backfill(List.of(MARCH));
            table.rollback(MARCH);
        }
        stop.set(true);
        assertEquals(0, reader.join(), "czytelnik nie widzi partycji w połowie podmiany");
    }

    @Test
    void concurrentBackfillsOfOnePartitionApplyItExactlyOnce() {
        List<Notification> notified = Collections.synchronizedList(new ArrayList<>());
        ServiceBackfill first = new ServiceBackfill(table, events, NOW, LENIENT, notified::add);
        ServiceBackfill second = new ServiceBackfill(table, events, NOW, LENIENT, notified::add);
        for (int round = 0; round < MIN_SWAPS; round++) {
            CompletableFuture<Report> parallel = CompletableFuture.supplyAsync(() -> first.backfill(List.of(MARCH)));
            Report report = second.backfill(List.of(MARCH));
            assertEquals(1, parallel.join().applied().size() + report.applied().size(), "runda " + round);
            table.rollback(MARCH);
        }
        assertEquals(MIN_SWAPS, notified.size(), "jedno powiadomienie na rundę, od tego, kto podmienił");
    }

    @Test
    void tableRejectsLoadingTheSamePartitionTwice() {
        assertThrows(IllegalStateException.class, () -> table.load(MARCH, new CurrentPartition(List.of())));
    }

    @Test
    void invalidEventsAreRejected() {
        for (String msisdn : List.of("+48600100200", "48 600 100", "12345678", "1234567890123456", "４８６００１００２００")) {
            assertThrows(IllegalArgumentException.class, () -> activation(0, msisdn), msisdn);
        }
        assertThrows(IllegalArgumentException.class, () -> activation(-1, UNTOUCHED));
        assertThrows(IllegalArgumentException.class, () -> activation(Long.MIN_VALUE, UNTOUCHED));
        assertThrows(IllegalArgumentException.class, () -> eventWith(0, UNTOUCHED, "  ", Channel.APP, DATA), "pusta usługa");
        assertThrows(IllegalArgumentException.class, () -> eventWith(0, UNTOUCHED, "DATA_10GB", Channel.UNKNOWN, DATA));
        assertThrows(IllegalArgumentException.class, () -> eventWith(0, UNTOUCHED, "DATA_10GB", Channel.APP, " "), "pusta nazwa");
    }

    @Test
    void extremeButValidEventsAreAccepted() {
        List<ProvisioningEvent> log = List.of(
                new ProvisioningEvent(0, Instant.MIN, "486001002", "DATA_10GB", EventType.ACTIVATE, Channel.APP, DATA),
                new ProvisioningEvent(Long.MAX_VALUE, Instant.MAX, "486001002003004", "DATA_10GB", EventType.ACTIVATE,
                        Channel.CALL_CENTER, DATA));
        assertEquals(List.of("486001002"), subscribers(log, MARCH), "Instant.MAX nie należy do żadnej partycji");
    }

    @Test
    void duplicateSequenceIsRejected() {
        List<ProvisioningEvent> duplicated = List.of(eventWith(7, UNTOUCHED, "DATA_10GB", Channel.APP, DATA),
                eventWith(7, NAME_FIXED_ONLY, "DATA_10GB", Channel.APP, DATA));
        assertThrows(IllegalArgumentException.class, () -> new ServiceBackfill(table, duplicated, NOW, LENIENT, sent::add));
        assertThrows(IllegalArgumentException.class, () -> ServiceBackfill.legacySnapshot(duplicated, MARCH));
        assertThrows(IllegalArgumentException.class, () -> ServiceBackfill.currentSnapshot(duplicated, MARCH));
    }

    @Test
    void invalidPoliciesAndPartitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Policy(0, 20));
        assertThrows(IllegalArgumentException.class, () -> new Policy(12, -1));
        assertThrows(IllegalArgumentException.class, () -> new Policy(12, 101));
        assertDoesNotThrow(() -> List.of(new Policy(1, 0), new Policy(Integer.MAX_VALUE, 100)));
        ServiceRow row = new ServiceRow(UNTOUCHED, "DATA_10GB", DATA, Status.ACTIVE, Channel.APP);
        assertThrows(IllegalArgumentException.class, () -> new CurrentPartition(List.of(row, row)), "powtórzony wiersz");
    }

    @Test
    void nullsAreRejectedByStaticMethods() {
        List<ProvisioningEvent> withNull = Arrays.asList(events.getFirst(), null);
        assertThrows(NullPointerException.class, () -> ServiceBackfill.repairText(null));
        assertThrows(NullPointerException.class, () -> ServiceBackfill.currentSnapshot(null, MARCH));
        assertThrows(NullPointerException.class, () -> ServiceBackfill.legacySnapshot(events, null));
        assertThrows(NullPointerException.class, () -> ServiceBackfill.currentSnapshot(withNull, MARCH));
    }

    @Test
    void nullsAreRejectedByConstructors() {
        assertThrows(NullPointerException.class, () -> new ServiceBackfill(null, events, NOW, LENIENT, sent::add));
        assertThrows(NullPointerException.class, () -> new ServiceBackfill(table, events, null, LENIENT, sent::add));
        assertThrows(NullPointerException.class, () -> new ServiceBackfill(table, events, NOW, null, sent::add));
        assertThrows(NullPointerException.class, () -> new ServiceBackfill(table, events, NOW, LENIENT, null));
        assertThrows(NullPointerException.class, () -> eventWith(0, null, "DATA_10GB", Channel.APP, DATA));
        assertThrows(NullPointerException.class, () -> eventWith(0, UNTOUCHED, "DATA_10GB", null, DATA));
    }

    @Test
    void nullsAreRejectedByJobAndTable() {
        assertThrows(NullPointerException.class, () -> job.plan(null, MARCH));
        assertThrows(NullPointerException.class, () -> job.plan(MARCH, null));
        assertThrows(NullPointerException.class, () -> job.backfill(null));
        assertThrows(NullPointerException.class, () -> job.backfill(Arrays.asList(MARCH, null)));
        assertThrows(NullPointerException.class, () -> table.partition(null));
        assertThrows(NullPointerException.class, () -> table.load(MARCH, null));
        assertThrows(NullPointerException.class, () -> table.rollback(null));
        assertThrows(NullPointerException.class, () -> new LegacyPartition(Arrays.asList((LegacyServiceRow) null)));
        assertThrows(NullPointerException.class, () -> new ServiceRow(UNTOUCHED, "DATA_10GB", DATA, Status.ACTIVE, null));
    }

    @Test
    void randomLogsAreBackfilledToTheBruteForceReplay() {
        Random random = new Random(7);
        for (int round = 0; round < 300; round++) {
            assertMatchesBruteForce(randomEvents(random));
        }
    }

    @Test
    void largeLogIsBackfilledWithTheDefaultPolicy() {
        List<ProvisioningEvent> log = largeLog(30_000);
        Table large = tableOf(log, YearMonth.of(2026, 1), NOW, List.of());
        List<Notification> notifications = new ArrayList<>();
        ServiceBackfill backfill = new ServiceBackfill(large, log, NOW, Policy.DEFAULT, notifications::add);
        Report report = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> backfill.backfill(monthsBetween(YearMonth.of(2026, 1), OCTOBER)));
        assertEquals(10, report.applied().size());
        assertEquals(3_000, notifications.size(), "co dziesiąty abonent miał zawieszenie po wznowieniu");
    }

    private int readUntilStopped(Set<List<ServiceRow>> allowed, AtomicBoolean stop, AtomicInteger reads) {
        int inconsistent = 0;
        while (!stop.get()) {
            reads.incrementAndGet();
            if (!allowed.contains(table.read(MARCH))) {
                inconsistent++;
            }
        }
        return inconsistent;
    }

    private Report backfillWith(Policy policy, YearMonth... months) {
        return new ServiceBackfill(demoTable(events), events, NOW, policy, sent::add).backfill(List.of(months));
    }

    private static void assertMatchesBruteForce(List<ProvisioningEvent> log) {
        Table random = tableOf(log, RANDOM_FIRST, RANDOM_NOW, List.of());
        List<Notification> notifications = new ArrayList<>();
        new ServiceBackfill(random, log, RANDOM_NOW, NO_LIMIT, notifications::add)
                .backfill(monthsBetween(RANDOM_FIRST, RANDOM_NOW.minusMonths(1)));
        for (YearMonth month : monthsBetween(RANDOM_FIRST, RANDOM_NOW.minusMonths(1))) {
            assertEquals(bruteForce(log, month, OCCURRENCE), statuses(random.read(month)), month + " " + log);
            assertTrue(random.read(month).stream().allMatch(row -> OFFER_NAMES.containsValue(row.offerName())));
        }
        Set<String> notified = notifications.stream().map(Notification::msisdn).collect(Collectors.toSet());
        assertEquals(concernedSubscribers(log), notified, "powiadomieni dokładnie ci, którym zmienił się status");
    }

    private static Map<String, String> bruteForce(List<ProvisioningEvent> log, YearMonth month,
                                                  Comparator<ProvisioningEvent> order) {
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(WARSAW).toInstant();
        List<ProvisioningEvent> applied = new ArrayList<>(log.stream().filter(e -> e.occurredAt().isBefore(end)).toList());
        applied.sort(order);
        Map<String, String> statuses = new TreeMap<>();
        applied.forEach(e -> statuses.compute(e.msisdn() + "/" + e.serviceCode(), (key, status) -> next(status, e.type())));
        return statuses;
    }

    private static String next(String status, EventType type) {
        return switch (type) {
            case ACTIVATE -> "ACTIVE";
            case SUSPEND -> "ACTIVE".equals(status) ? "SUSPENDED" : status;
            case RESUME -> "SUSPENDED".equals(status) ? "ACTIVE" : status;
            case CANCEL -> status == null ? null : "CANCELLED";
        };
    }

    private static Set<String> concernedSubscribers(List<ProvisioningEvent> log) {
        Set<String> concerned = new HashSet<>();
        for (YearMonth month : monthsBetween(RANDOM_FIRST, RANDOM_NOW.minusMonths(1))) {
            Map<String, String> before = bruteForce(log, month, ARRIVAL);
            bruteForce(log, month, OCCURRENCE).forEach((key, status) -> addIfChanged(concerned, key, status, before));
        }
        return concerned;
    }

    private static void addIfChanged(Set<String> concerned, String key, String status, Map<String, String> before) {
        if (!status.equals(before.get(key))) {
            concerned.add(key.substring(0, key.indexOf('/')));
        }
    }

    private static Map<String, String> statuses(List<ServiceRow> rows) {
        return rows.stream().collect(Collectors.toMap(row -> row.msisdn() + "/" + row.serviceCode(),
                row -> row.status().name(), (a, b) -> a, TreeMap::new));
    }

    private static List<ProvisioningEvent> randomEvents(Random random) {
        List<Long> sequences = new ArrayList<>(LongStream.range(0, random.nextInt(30)).boxed().toList());
        Collections.shuffle(sequences, random);
        return sequences.stream().map(sequence -> randomEvent(random, sequence)).toList();
    }

    private static ProvisioningEvent randomEvent(Random random, long sequence) {
        Instant occurredAt = RANDOM_START.plus(Duration.ofDays(random.nextInt(RANDOM_DAYS)));
        String msisdn = "4860010000" + random.nextInt(3);
        String service = random.nextBoolean() ? "ROAMING_EU" : "ESIM_WATCH";
        EventType type = EventType.values()[random.nextInt(EventType.values().length)];
        Channel channel = random.nextBoolean() ? Channel.SHOP : Channel.APP;
        return new ProvisioningEvent(sequence, occurredAt, msisdn, service, type, channel, sentName(service, channel));
    }

    private static List<ProvisioningEvent> largeLog(int subscribers) {
        return IntStream.range(0, subscribers).boxed().flatMap(ServiceBackfillTest::largeLogSubscriber).toList();
    }

    private static Stream<ProvisioningEvent> largeLogSubscriber(int index) {
        String msisdn = String.valueOf(48_500_000_000L + index);
        String month = YearMonth.of(2026, 1 + index % 7).toString();
        ProvisioningEvent activate = event(3L * index, month + "-01T10:00", msisdn, "DATA_10GB", EventType.ACTIVATE,
                Channel.APP);
        if (index % 10 != 0) {
            return Stream.of(activate);
        }
        return Stream.of(activate,
                event(3L * index + 2, month + "-10T12:00", msisdn, "DATA_10GB", EventType.SUSPEND, Channel.SHOP),
                event(3L * index + 1, month + "-10T18:00", msisdn, "DATA_10GB", EventType.RESUME, Channel.APP));
    }

    private static List<ProvisioningEvent> demoEvents() {
        return Stream.of(untouched(), frozenOutOfOrder(), activatedAfterCancel(), suspendedAfterResume(), nameFixedOnly())
                .flatMap(List::stream)
                .toList();
    }

    private static List<ProvisioningEvent> untouched() {
        return List.of(event(1, "2025-09-03T10:00", UNTOUCHED, "DATA_10GB", EventType.ACTIVATE, Channel.APP));
    }

    private static List<ProvisioningEvent> frozenOutOfOrder() {
        return List.of(
                event(3, "2025-10-01T09:00", FROZEN_OUT_OF_ORDER, "ROAMING_EU", EventType.ACTIVATE, Channel.APP),
                event(5, "2025-10-05T12:00", FROZEN_OUT_OF_ORDER, "ROAMING_EU", EventType.SUSPEND, Channel.SHOP),
                event(4, "2025-10-05T19:00", FROZEN_OUT_OF_ORDER, "ROAMING_EU", EventType.RESUME, Channel.APP),
                event(6, "2025-11-03T08:00", FROZEN_OUT_OF_ORDER, "ROAMING_EU", EventType.CANCEL, Channel.APP));
    }

    private static List<ProvisioningEvent> activatedAfterCancel() {
        return List.of(
                event(21, "2026-03-02T10:00", ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", EventType.ACTIVATE, Channel.SHOP),
                event(20, "2026-03-28T18:00", ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", EventType.CANCEL, Channel.APP),
                event(40, "2026-05-04T11:00", ACTIVATED_AFTER_CANCEL, "ESIM_WATCH", EventType.ACTIVATE, Channel.APP));
    }

    private static List<ProvisioningEvent> suspendedAfterResume() {
        return List.of(
                event(50, "2026-06-01T08:00", SUSPENDED_AFTER_RESUME, "ROAMING_EU", EventType.ACTIVATE, Channel.APP),
                event(52, "2026-06-10T12:00", SUSPENDED_AFTER_RESUME, "ROAMING_EU", EventType.SUSPEND, Channel.SHOP),
                event(51, "2026-06-10T18:00", SUSPENDED_AFTER_RESUME, "ROAMING_EU", EventType.RESUME, Channel.APP),
                event(60, "2026-07-15T09:00", SUSPENDED_AFTER_RESUME, "ROAMING_EU", EventType.CANCEL, Channel.APP));
    }

    private static List<ProvisioningEvent> nameFixedOnly() {
        return List.of(
                event(70, "2026-09-15T16:00", NAME_FIXED_ONLY, "DATA_10GB", EventType.ACTIVATE, Channel.SHOP),
                event(80, "2026-10-01T07:00", NAME_FIXED_ONLY, "DATA_10GB", EventType.ACTIVATE, Channel.APP));
    }

    private static Table demoTable(List<ProvisioningEvent> log) {
        return demoTable(log, List.of());
    }

    private static Table demoTable(List<ProvisioningEvent> log, List<LegacyServiceRow> extraMarchRows) {
        Table demo = tableOf(log, FIRST, NOW, extraMarchRows);
        demo.load(NOW, ServiceBackfill.currentSnapshot(log, NOW));
        return demo;
    }

    private static Table tableOf(List<ProvisioningEvent> log, YearMonth from, YearMonth toExclusive,
                                 List<LegacyServiceRow> extraMarchRows) {
        Table legacy = new Table();
        for (YearMonth month : monthsBetween(from, toExclusive.minusMonths(1))) {
            List<LegacyServiceRow> rows = new ArrayList<>(ServiceBackfill.legacySnapshot(log, month).rows());
            rows.addAll(month.equals(MARCH) ? extraMarchRows : List.of());
            legacy.load(month, new LegacyPartition(rows));
        }
        return legacy;
    }

    private static List<YearMonth> monthsBetween(YearMonth from, YearMonth to) {
        return LongStream.rangeClosed(0, from.until(to, ChronoUnit.MONTHS)).mapToObj(from::plusMonths).toList();
    }

    private static List<YearMonth> monthsWith(List<PartitionPlan> plan, Action action) {
        return plan.stream().filter(partition -> partition.action() == action).map(PartitionPlan::month).toList();
    }

    private static List<YearMonth> months(List<PartitionPlan> plans) {
        return plans.stream().map(PartitionPlan::month).toList();
    }

    private static List<Integer> correctionCounts(List<PartitionPlan> plan, Action action) {
        return plan.stream().filter(partition -> partition.action() == action).map(p -> p.corrections().size()).toList();
    }

    private static List<Integer> nameFixes(List<PartitionPlan> plan, Action action) {
        return plan.stream().filter(partition -> partition.action() == action).map(PartitionPlan::nameFixes).toList();
    }

    private static ServiceRow rowOf(List<ServiceRow> rows, String msisdn) {
        return rows.stream().filter(row -> row.msisdn().equals(msisdn)).findFirst().orElseThrow();
    }

    private static Status legacyStatus(List<ProvisioningEvent> log, YearMonth month, String msisdn) {
        return rowOf(ServiceBackfill.legacySnapshot(log, month).read(), msisdn).status();
    }

    private static Status currentStatus(List<ProvisioningEvent> log, YearMonth month, String msisdn) {
        return rowOf(ServiceBackfill.currentSnapshot(log, month).rows(), msisdn).status();
    }

    private static List<String> subscribers(List<ProvisioningEvent> log, YearMonth month) {
        return ServiceBackfill.currentSnapshot(log, month).rows().stream().map(ServiceRow::msisdn).toList();
    }

    private static ProvisioningEvent atNoon(long sequence, EventType type) {
        return event(sequence, "2026-03-05T12:00", UNTOUCHED, "DATA_10GB", type, Channel.APP);
    }

    private static ProvisioningEvent monthly(long sequence, int month, EventType type) {
        return event(sequence, YearMonth.of(2026, month).atDay(15) + "T12:00", UNTOUCHED, "DATA_10GB", type, Channel.APP);
    }

    private static ProvisioningEvent event(long sequence, String warsawTime, String msisdn, String service,
                                           EventType type, Channel channel) {
        return new ProvisioningEvent(sequence, warsaw(warsawTime), msisdn, service, type, channel,
                sentName(service, channel));
    }

    private static ProvisioningEvent activation(long sequence, String msisdn) {
        return eventWith(sequence, msisdn, "DATA_10GB", Channel.APP, DATA);
    }

    private static ProvisioningEvent eventWith(long sequence, String msisdn, String service, Channel channel,
                                               String offerName) {
        return new ProvisioningEvent(sequence, warsaw("2026-03-10T10:00"), msisdn, service, EventType.ACTIVATE,
                channel, offerName);
    }

    private static String sentName(String service, Channel channel) {
        String name = OFFER_NAMES.get(service);
        return channel == Channel.SHOP ? readAsLatin1(name) : name;
    }

    private static Instant warsaw(String localTime) {
        return LocalDateTime.parse(localTime).atZone(WARSAW).toInstant();
    }

    private static String readAsLatin1(String text) {
        return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    private static String randomPolishText(Random random) {
        StringBuilder text = new StringBuilder();
        for (int i = random.nextInt(12); i > 0; i--) {
            text.append(randomPolishCharacter(random));
        }
        return text.toString();
    }

    private static String randomPolishCharacter(Random random) {
        if (random.nextInt(10) == 0) {
            return "🎉";
        }
        return String.valueOf(POLISH_ALPHABET.charAt(random.nextInt(POLISH_ALPHABET.length())));
    }
}
