package collection.backfill;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import collection.backfill.model.EventType;
import collection.backfill.model.LegacyServiceRow;
import collection.backfill.model.ProvisioningEvent;
import collection.backfill.model.ServiceKey;
import collection.backfill.model.ServiceRow;
import collection.backfill.model.Status;
import collection.backfill.notification.Notification;
import collection.backfill.notification.StatusCorrection;
import collection.backfill.table.CurrentPartition;
import collection.backfill.table.LegacyPartition;
import collection.backfill.table.Partition;
import collection.backfill.table.Table;
import collection.backfill.text.TextRepair;

/// Backfill usług dodatkowych abonenta (roaming, pakiety danych, eSIM): przeliczenie
/// miesięcznych partycji od nowa, z poprawioną logiką, bez ruszania dziennika zdarzeń.
///
/// **Źródło prawdy** to dziennik zdarzeń provisioningu ([ProvisioningEvent]): aktywacja,
/// zawieszenie, wznowienie, anulowanie. Nigdy go nie zmieniamy. **Dane pochodne** to tabela
/// [Table] podzielona na partycje miesięczne: partycja `2026-03` to stan każdej usługi na koniec
/// marca czasu warszawskiego.
///
/// **Co było źle (stara logika, [#legacySnapshot]).** Zdarzenia ze sklepów przychodzą nocną paczką,
/// więc później niż zdarzenia z aplikacji. Stara logika stosowała je w kolejności przyjścia (numer
/// `sequence`), a nie wystąpienia, i kopiowała nazwę oferty bez zmian, choć kasy w sklepach wysyłają
/// UTF-8 odczytany jako ISO-8859-1 (`"Å¼"` zamiast `"ż"`, naprawia [TextRepair]). Kanał aktywacji był w zdarzeniu, ale go
/// nie zapisywano, a kolumna `legacyStatusCode` służyła wyłączonemu już systemowi rozliczeń.
///
/// **Nowa logika ([#currentSnapshot]).** Kolejność wystąpienia (`occurredAt`, przy remisie
/// `sequence`), naprawione kodowanie nazwy, nowa kolumna `activationChannel` z nieużywanego dotąd
/// pola zdarzenia, `planName` przemianowane na `offerName`, `legacyStatusCode` usunięte.
///
/// **Backfill, krok po kroku.**
///
/// 1. [#plan] na sucho przelicza każdą partycję z zakresu i porównuje ją z obecną: co się zmieni,
///    czy wolno ją ruszyć i czy przechodzi walidację. Niczego nie zapisuje.
/// 1. [#backfill] dla wskazanych miesięcy (zwykle tylko tych z `CORRECTION`) buduje nową wersję obok,
///    waliduje ją, podmienia całą partycję jedną operacją i zachowuje starą wersję.
/// 1. Po wszystkich partycjach wysyła po jednym powiadomieniu tylko tym abonentom, którym zmienił się
///    status którejś usługi. Poprawa nazwy albo samego schematu nikogo nie powiadamia.
/// 1. [Table#dropReplaced] usuwa starą wersję, gdy wynik jest sprawdzony; do tego czasu
///    [Table#rollback] przywraca ją jedną operacją.
///
/// **Które partycje wolno ruszyć.** Tylko zamknięte miesiące z okna `Policy.backfillWindowMonths`
/// (domyślnie 12 ostatnich). Bieżący miesiąc jest otwarty, pisze do niego proces na żywo. Starsze są
/// zamrożone: rozliczenie zamknięte, faktury wystawione, okres reklamacji minął.
///
/// **Założenia.**
///
/// | | |
/// |---|---|
/// | Numer abonenta (MSISDN) | 9–15 cyfr ASCII, bez `+` i spacji; inaczej `IllegalArgumentException` |
/// | Numer zdarzenia `sequence` | ≥ 0 i niepowtarzalny w dzienniku; inaczej `IllegalArgumentException` |
/// | Kanał zdarzenia | `APP`, `SHOP` albo `CALL_CENTER`; `UNKNOWN` ma tylko wiersz odczytany ze starego schematu |
/// | Granica partycji | północ pierwszego dnia miesiąca w `Europe/Warsaw` |
/// | Zawieszenie, wznowienie, anulowanie przed pierwszą aktywacją | pomijane |
/// | Ponowna aktywacja po anulowaniu | dozwolona, nazwa i kanał z nowej aktywacji |
/// | Brak partycji w tabeli | backfill ją tworzy (wypełnia lukę) |
/// | Pusty dziennik, pusta tabela, pusta lista miesięcy | poprawne wejście |
/// | Powtórzony miesiąc na liście do backfillu | liczony raz |
/// | Miesiąc zamrożony albo otwarty na liście do backfillu | `IllegalArgumentException`, nic nie jest zmienione |
/// | Partycja nie przechodzi walidacji | odrzucona i nietknięta, pozostałe z tej samej listy idą dalej |
/// | Partycję zmienił w tym czasie ktoś inny | odrzucona, nic nie nadpisane, pozostałe idą dalej |
/// | Wysyłka powiadomienia się nie udała | pozostałe są wysyłane dalej, nieudane trafiają do `Report.undelivered` |
/// | `null` jako parametr albo element listy | `NullPointerException` |
///
/// **Złożoność.** Przeliczenie partycji to O(z log z) dla `z` zdarzeń, porównanie O(w) dla `w`
///
/// **Pakiety.** `model`: zdarzenia i wiersze; `table`: partycje i tabela z podmianą; `notification`:
/// poprawki statusu i powiadomienia; `text`: naprawa kodowania ([TextRepair]). Tu zostaje sam
/// algorytm, zasady ([Policy]), plan ([PartitionPlan]) i raport ([Report]).
public class ServiceBackfill {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private static final Comparator<ProvisioningEvent> ARRIVAL_ORDER =
            Comparator.comparingLong(ProvisioningEvent::sequence);
    private static final Comparator<ProvisioningEvent> OCCURRENCE_ORDER =
            Comparator.comparing(ProvisioningEvent::occurredAt).thenComparing(ARRIVAL_ORDER);

    private final Table table;
    private final List<ProvisioningEvent> events;
    private final YearMonth currentMonth;
    private final Policy policy;
    private final Consumer<Notification> notifier;

    public ServiceBackfill(Table table, List<ProvisioningEvent> events, YearMonth currentMonth, Policy policy,
                           Consumer<Notification> notifier) {
        this.table = Objects.requireNonNull(table, "brak tabeli");
        this.events = requireEvents(events);
        this.currentMonth = Objects.requireNonNull(currentMonth, "brak bieżącego miesiąca");
        this.policy = Objects.requireNonNull(policy, "brak zasad backfillu");
        this.notifier = Objects.requireNonNull(notifier, "brak kanału powiadomień");
    }

    public static LegacyPartition legacySnapshot(List<ProvisioningEvent> events, YearMonth month) {
        Objects.requireNonNull(month, "brak miesiąca");
        Map<ServiceKey, ServiceState> states = replay(requireEvents(events), month, ARRIVAL_ORDER);
        return new LegacyPartition(states.entrySet().stream().map(ServiceBackfill::legacyRow).toList());
    }

    public static CurrentPartition currentSnapshot(List<ProvisioningEvent> events, YearMonth month) {
        Objects.requireNonNull(month, "brak miesiąca");
        return snapshot(requireEvents(events), month);
    }

    public List<PartitionPlan> plan(YearMonth from, YearMonth to) {
        Objects.requireNonNull(from, "brak początku zakresu");
        Objects.requireNonNull(to, "brak końca zakresu");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("koniec zakresu %s jest przed początkiem %s".formatted(to, from));
        }
        long months = from.until(to, ChronoUnit.MONTHS) + 1;
        return LongStream.range(0, months).mapToObj(from::plusMonths).map(month -> draft(month).plan()).toList();
    }

    public Report backfill(Collection<YearMonth> months) {
        Map<Outcome, List<PartitionPlan>> outcomes = new EnumMap<>(Outcome.class);
        for (YearMonth month : requireBackfillable(months)) {
            Result result = execute(draft(month));
            outcomes.computeIfAbsent(result.outcome(), outcome -> new ArrayList<>()).add(result.plan());
        }
        return report(outcomes);
    }

    private Report report(Map<Outcome, List<PartitionPlan>> outcomes) {
        List<PartitionPlan> applied = outcomes.getOrDefault(Outcome.APPLIED, List.of());
        List<Notification> notifications = concerned(applied);
        List<Notification> undelivered = notifications.stream().filter(notification -> !deliver(notification)).toList();
        return new Report(applied, outcomes.getOrDefault(Outcome.SKIPPED, List.of()),
                outcomes.getOrDefault(Outcome.REJECTED, List.of()), notifications, undelivered);
    }

    // błąd jednej wysyłki nie zatrzymuje pozostałych; nieudane trafiają do raportu do ponowienia
    private boolean deliver(Notification notification) {
        try {
            notifier.accept(notification);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Result execute(Draft draft) {
        PartitionPlan plan = draft.plan();
        if (!plan.problems().isEmpty()) {
            return new Result(Outcome.REJECTED, plan);
        }
        if (plan.action() == Action.UP_TO_DATE) {
            return new Result(Outcome.SKIPPED, plan);
        }
        return swap(draft);
    }

    private Result swap(Draft draft) {
        PartitionPlan plan = draft.plan();
        if (table.swap(plan.month(), draft.before(), draft.after())) {
            return new Result(Outcome.APPLIED, plan);
        }
        return new Result(Outcome.REJECTED, plan.withProblem("partycja zmieniła się w trakcie backfillu"));
    }

    private Draft draft(YearMonth month) {
        Optional<Partition> before = table.partition(month);
        CurrentPartition after = snapshot(events, month);
        Changes changes = compare(month, index(before.map(Partition::read).orElse(List.of())), after.rows());
        int rowsBefore = before.map(Partition::size).orElse(0);
        PartitionPlan plan = new PartitionPlan(month, action(month, before, after, changes), rowsBefore,
                after.size(), changes.nameFixes(), changes.corrections(), problems(changes, rowsBefore, after));
        return new Draft(plan, before, after);
    }

    private Action action(YearMonth month, Optional<Partition> before, CurrentPartition after, Changes changes) {
        if (isFrozen(month)) {
            return Action.FROZEN;
        }
        if (isOpen(month)) {
            return Action.OPEN;
        }
        return dataAction(before, after, changes);
    }

    private static Action dataAction(Optional<Partition> before, CurrentPartition after, Changes changes) {
        if (changes.touchData()) {
            return Action.CORRECTION;
        }
        return isUpToDate(before, after) ? Action.UP_TO_DATE : Action.SCHEMA_ONLY;
    }

    private static boolean isUpToDate(Optional<Partition> before, CurrentPartition after) {
        return before.map(partition -> partition instanceof CurrentPartition current
                        && index(current.rows()).equals(index(after.rows())))
                .orElse(after.rows().isEmpty());
    }

    private boolean isFrozen(YearMonth month) {
        return month.isBefore(currentMonth.minusMonths(policy.backfillWindowMonths()));
    }

    private boolean isOpen(YearMonth month) {
        return !month.isBefore(currentMonth);
    }

    private SortedSet<YearMonth> requireBackfillable(Collection<YearMonth> months) {
        Objects.requireNonNull(months, "brak listy miesięcy");
        SortedSet<YearMonth> ordered = new TreeSet<>();
        for (YearMonth month : months) {
            Objects.requireNonNull(month, "brak miesiąca na liście");
            requireNotBlocked(month);
            ordered.add(month);
        }
        return ordered;
    }

    private void requireNotBlocked(YearMonth month) {
        if (isFrozen(month)) {
            throw new IllegalArgumentException("partycja %s jest zamrożona: starsza niż %d ostatnich miesięcy"
                    .formatted(month, policy.backfillWindowMonths()));
        }
        if (isOpen(month)) {
            throw new IllegalArgumentException("partycja %s jest otwarta: pisze do niej bieżący proces"
                    .formatted(month));
        }
    }

    private static Map<ServiceKey, ServiceState> replay(List<ProvisioningEvent> events, YearMonth month,
                                                        Comparator<ProvisioningEvent> order) {
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        Map<ServiceKey, ServiceState> states = new TreeMap<>();
        events.stream()
                .filter(event -> event.occurredAt().isBefore(end))
                .sorted(order)
                .forEach(event -> states.compute(event.key(), (key, state) -> apply(state, event)));
        return states;
    }

    private static ServiceState apply(ServiceState state, ProvisioningEvent event) {
        if (event.type() == EventType.ACTIVATE) {
            return new ServiceState(Status.ACTIVE, event.offerName(), event.channel());
        }
        return state == null ? null : state.withStatus(transition(state.status(), event.type()));
    }

    private static Status transition(Status status, EventType type) {
        return switch (type) {
            case ACTIVATE -> Status.ACTIVE;
            case SUSPEND -> status == Status.ACTIVE ? Status.SUSPENDED : status;
            case RESUME -> status == Status.SUSPENDED ? Status.ACTIVE : status;
            case CANCEL -> Status.CANCELLED;
        };
    }

    private static CurrentPartition snapshot(List<ProvisioningEvent> events, YearMonth month) {
        Map<ServiceKey, ServiceState> states = replay(events, month, OCCURRENCE_ORDER);
        return new CurrentPartition(states.entrySet().stream().map(ServiceBackfill::currentRow).toList());
    }

    private static ServiceRow currentRow(Map.Entry<ServiceKey, ServiceState> entry) {
        ServiceKey key = entry.getKey();
        ServiceState state = entry.getValue();
        return new ServiceRow(key.msisdn(), key.serviceCode(), TextRepair.repair(state.offerName()), state.status(),
                state.channel());
    }

    private static LegacyServiceRow legacyRow(Map.Entry<ServiceKey, ServiceState> entry) {
        ServiceKey key = entry.getKey();
        ServiceState state = entry.getValue();
        return new LegacyServiceRow(key.msisdn(), key.serviceCode(), state.offerName(), state.status(),
                state.status().legacyCode());
    }

    private static Changes compare(YearMonth month, Map<ServiceKey, ServiceRow> before, List<ServiceRow> after) {
        List<RowPair> kept = keptRows(before, after);
        long nameFixes = kept.stream().filter(RowPair::nameChanged).count();
        List<StatusCorrection> corrections = kept.stream()
                .filter(RowPair::statusChanged)
                .map(pair -> pair.correction(month))
                .toList();
        return new Changes(Math.toIntExact(nameFixes), corrections, after.size() - kept.size(),
                lostRows(before, after));
    }

    private static List<RowPair> keptRows(Map<ServiceKey, ServiceRow> before, List<ServiceRow> after) {
        return after.stream()
                .filter(row -> before.containsKey(row.key()))
                .map(row -> new RowPair(before.get(row.key()), row))
                .toList();
    }

    private static List<ServiceKey> lostRows(Map<ServiceKey, ServiceRow> before, List<ServiceRow> after) {
        Set<ServiceKey> rebuilt = after.stream().map(ServiceRow::key).collect(Collectors.toSet());
        return before.keySet().stream().filter(key -> !rebuilt.contains(key)).sorted().toList();
    }

    private static Map<ServiceKey, ServiceRow> index(List<ServiceRow> rows) {
        return rows.stream().collect(Collectors.toMap(ServiceRow::key, Function.identity()));
    }

    private List<String> problems(Changes changes, int rowsBefore, CurrentPartition after) {
        return Stream.of(lostRowProblems(changes), tooManyStatusChanges(changes, rowsBefore), unrepairedNames(after))
                .flatMap(Function.identity())
                .toList();
    }

    private static Stream<String> lostRowProblems(Changes changes) {
        return changes.lostRows().stream()
                .map(key -> "wiersz zniknąłby z partycji, bo w dzienniku nie ma jego zdarzeń: " + key);
    }

    private Stream<String> tooManyStatusChanges(Changes changes, int rowsBefore) {
        int changed = changes.corrections().size();
        if (policy.allowsStatusChanges(changed, rowsBefore)) {
            return Stream.empty();
        }
        return Stream.of("zmiana statusu w %d z %d wierszy przekracza próg %d%%"
                .formatted(changed, rowsBefore, policy.maxStatusChangePercent()));
    }

    private static Stream<String> unrepairedNames(CurrentPartition after) {
        return after.rows().stream()
                .filter(row -> TextRepair.isMangled(row.offerName()))
                .map(row -> "nazwa oferty nadal z zepsutym kodowaniem: " + row.key());
    }

    private static List<Notification> concerned(List<PartitionPlan> applied) {
        Map<String, List<StatusCorrection>> bySubscriber = applied.stream()
                .flatMap(plan -> plan.corrections().stream())
                .collect(Collectors.groupingBy(StatusCorrection::msisdn, TreeMap::new, Collectors.toList()));
        return bySubscriber.entrySet().stream()
                .map(entry -> new Notification(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<ProvisioningEvent> requireEvents(List<ProvisioningEvent> events) {
        Objects.requireNonNull(events, "brak dziennika zdarzeń");
        Set<Long> sequences = new HashSet<>();
        for (ProvisioningEvent event : events) {
            Objects.requireNonNull(event, "brak zdarzenia w dzienniku");
            if (!sequences.add(event.sequence())) {
                throw new IllegalArgumentException("powtórzony numer zdarzenia: " + event.sequence());
            }
        }
        return List.copyOf(events);
    }

    static void main() {
        ServiceBackfillDemo.run();
    }
}
