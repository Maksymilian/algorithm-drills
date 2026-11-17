package collection;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

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
/// UTF-8 odczytany jako ISO-8859-1 (`"Å¼"` zamiast `"ż"`). Kanał aktywacji był w zdarzeniu, ale go
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
/// | `null` jako parametr albo element listy | `NullPointerException` |
///
/// **Złożoność.** Przeliczenie partycji to O(z log z) dla `z` zdarzeń, porównanie O(w) dla `w`
/// wierszy; plan i backfill `p` partycji kosztują O(p · (z log z + w)).
public class ServiceBackfill {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final Pattern MSISDN = Pattern.compile("\\d{9,15}");
    private static final int PERCENT = 100;
    private static final int LAST_LATIN1 = 0xFF;
    private static final int FIRST_NON_ASCII = 0x80;
    private static final int DEFAULT_WINDOW_MONTHS = 12;
    private static final int DEFAULT_MAX_STATUS_CHANGE_PERCENT = 20;

    private static final Comparator<ProvisioningEvent> ARRIVAL_ORDER =
            Comparator.comparingLong(ProvisioningEvent::sequence);
    private static final Comparator<ProvisioningEvent> OCCURRENCE_ORDER =
            Comparator.comparing(ProvisioningEvent::occurredAt).thenComparing(ARRIVAL_ORDER);

    public enum EventType { ACTIVATE, SUSPEND, RESUME, CANCEL }

    public enum Channel { APP, SHOP, CALL_CENTER, UNKNOWN }

    public enum Status {
        ACTIVE(1, "aktywna"),
        SUSPENDED(2, "zawieszona"),
        CANCELLED(3, "anulowana");

        private final int legacyCode;
        private final String label;

        Status(int legacyCode, String label) {
            this.legacyCode = legacyCode;
            this.label = label;
        }
    }

    public enum Action { FROZEN, OPEN, UP_TO_DATE, SCHEMA_ONLY, CORRECTION }

    private enum Outcome { APPLIED, SKIPPED, REJECTED }

    public record ProvisioningEvent(long sequence, Instant occurredAt, String msisdn, String serviceCode,
                                    EventType type, Channel channel, String offerName) {
        public ProvisioningEvent {
            Objects.requireNonNull(occurredAt, "brak czasu zdarzenia");
            Objects.requireNonNull(msisdn, "brak numeru abonenta");
            Objects.requireNonNull(serviceCode, "brak kodu usługi");
            Objects.requireNonNull(type, "brak rodzaju zdarzenia");
            Objects.requireNonNull(channel, "brak kanału");
            Objects.requireNonNull(offerName, "brak nazwy oferty");
            requireEventFields(sequence, msisdn, serviceCode, channel, offerName);
        }

        ServiceKey key() {
            return new ServiceKey(msisdn, serviceCode);
        }
    }

    public record LegacyServiceRow(String msisdn, String serviceCode, String planName, Status status,
                                   int legacyStatusCode) {
        public LegacyServiceRow {
            Objects.requireNonNull(msisdn, "brak numeru abonenta");
            Objects.requireNonNull(serviceCode, "brak kodu usługi");
            Objects.requireNonNull(planName, "brak nazwy planu");
            Objects.requireNonNull(status, "brak statusu");
        }

        public ServiceRow upcast() {
            return new ServiceRow(msisdn, serviceCode, planName, status, Channel.UNKNOWN);
        }
    }

    public record ServiceRow(String msisdn, String serviceCode, String offerName, Status status,
                             Channel activationChannel) {
        public ServiceRow {
            Objects.requireNonNull(msisdn, "brak numeru abonenta");
            Objects.requireNonNull(serviceCode, "brak kodu usługi");
            Objects.requireNonNull(offerName, "brak nazwy oferty");
            Objects.requireNonNull(status, "brak statusu");
            Objects.requireNonNull(activationChannel, "brak kanału aktywacji");
        }

        ServiceKey key() {
            return new ServiceKey(msisdn, serviceCode);
        }
    }

    public sealed interface Partition permits LegacyPartition, CurrentPartition {
        List<ServiceRow> read();

        int size();
    }

    public record LegacyPartition(List<LegacyServiceRow> rows) implements Partition {
        public LegacyPartition {
            rows = requireRows(rows);
            requireUniqueKeys(rows.stream().map(LegacyServiceRow::upcast).toList());
        }

        @Override
        public List<ServiceRow> read() {
            return rows.stream().map(LegacyServiceRow::upcast).toList();
        }

        @Override
        public int size() {
            return rows.size();
        }
    }

    public record CurrentPartition(List<ServiceRow> rows) implements Partition {
        public CurrentPartition {
            rows = requireRows(rows);
            requireUniqueKeys(rows);
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

    public record Policy(int backfillWindowMonths, int maxStatusChangePercent) {
        public static final Policy DEFAULT = new Policy(DEFAULT_WINDOW_MONTHS, DEFAULT_MAX_STATUS_CHANGE_PERCENT);

        public Policy {
            if (backfillWindowMonths < 1) {
                throw new IllegalArgumentException("okno backfillu to co najmniej miesiąc: " + backfillWindowMonths);
            }
            if (maxStatusChangePercent < 0 || maxStatusChangePercent > PERCENT) {
                throw new IllegalArgumentException("próg zmian statusu to 0–100%: " + maxStatusChangePercent);
            }
        }
    }

    public record StatusCorrection(YearMonth month, String msisdn, String serviceCode, String offerName,
                                   Status before, Status after) {

        String describe() {
            return "%s w %s: %s → %s".formatted(offerName, month, before.label, after.label);
        }
    }

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

    public record Report(List<PartitionPlan> applied, List<PartitionPlan> skipped, List<PartitionPlan> rejected,
                         List<Notification> notifications) {
        public Report {
            applied = List.copyOf(applied);
            skipped = List.copyOf(skipped);
            rejected = List.copyOf(rejected);
            notifications = List.copyOf(notifications);
        }
    }

    private record ServiceKey(String msisdn, String serviceCode) implements Comparable<ServiceKey> {
        private static final Comparator<ServiceKey> ORDER =
                Comparator.comparing(ServiceKey::msisdn).thenComparing(ServiceKey::serviceCode);

        @Override
        public int compareTo(ServiceKey other) {
            return ORDER.compare(this, other);
        }

        @Override
        public String toString() {
            return msisdn + "/" + serviceCode;
        }
    }

    private record ServiceState(Status status, String offerName, Channel channel) {
        ServiceState withStatus(Status next) {
            return new ServiceState(next, offerName, channel);
        }
    }

    private record RowPair(ServiceRow before, ServiceRow after) {
        boolean nameChanged() {
            return !before.offerName().equals(after.offerName());
        }

        boolean statusChanged() {
            return before.status() != after.status();
        }

        StatusCorrection correction(YearMonth month) {
            return new StatusCorrection(month, after.msisdn(), after.serviceCode(), after.offerName(),
                    before.status(), after.status());
        }
    }

    private record Changes(int nameFixes, List<StatusCorrection> corrections, int addedRows,
                           List<ServiceKey> lostRows) {
        boolean touchData() {
            return nameFixes > 0 || !corrections.isEmpty() || addedRows > 0 || !lostRows.isEmpty();
        }
    }

    private record Draft(PartitionPlan plan, Optional<Partition> before, CurrentPartition after) {
    }

    private record Result(Outcome outcome, PartitionPlan plan) {
    }

    public static final class Table {
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

        private boolean swap(YearMonth month, Optional<Partition> expected, Partition replacement) {
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

    public static String repairText(String text) {
        Objects.requireNonNull(text, "brak napisu");
        if (!isLatin1WithHighBytes(text)) {
            return text;
        }
        return decodeStrictUtf8(text.getBytes(StandardCharsets.ISO_8859_1)).orElse(text);
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
        List<PartitionPlan> applied = outcomes.getOrDefault(Outcome.APPLIED, List.of());
        return new Report(applied, outcomes.getOrDefault(Outcome.SKIPPED, List.of()),
                outcomes.getOrDefault(Outcome.REJECTED, List.of()), notifyConcerned(applied));
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
        return new ServiceRow(key.msisdn(), key.serviceCode(), repairText(state.offerName()), state.status(),
                state.channel());
    }

    private static LegacyServiceRow legacyRow(Map.Entry<ServiceKey, ServiceState> entry) {
        ServiceKey key = entry.getKey();
        ServiceState state = entry.getValue();
        return new LegacyServiceRow(key.msisdn(), key.serviceCode(), state.offerName(), state.status(),
                state.status().legacyCode);
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
        if ((long) changed * PERCENT <= (long) policy.maxStatusChangePercent() * rowsBefore) {
            return Stream.empty();
        }
        return Stream.of("zmiana statusu w %d z %d wierszy przekracza próg %d%%"
                .formatted(changed, rowsBefore, policy.maxStatusChangePercent()));
    }

    private static Stream<String> unrepairedNames(CurrentPartition after) {
        return after.rows().stream()
                .filter(row -> isMangled(row.offerName()))
                .map(row -> "nazwa oferty nadal z zepsutym kodowaniem: " + row.key());
    }

    private List<Notification> notifyConcerned(List<PartitionPlan> applied) {
        Map<String, List<StatusCorrection>> bySubscriber = applied.stream()
                .flatMap(plan -> plan.corrections().stream())
                .collect(Collectors.groupingBy(StatusCorrection::msisdn, TreeMap::new, Collectors.toList()));
        List<Notification> notifications = bySubscriber.entrySet().stream()
                .map(entry -> new Notification(entry.getKey(), entry.getValue()))
                .toList();
        notifications.forEach(notifier);
        return notifications;
    }

    private static boolean isMangled(String text) {
        return !repairText(text).equals(text);
    }

    private static boolean isLatin1WithHighBytes(String text) {
        return text.chars().allMatch(c -> c <= LAST_LATIN1) && text.chars().anyMatch(c -> c >= FIRST_NON_ASCII);
    }

    private static Optional<String> decodeStrictUtf8(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
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

    private static void requireEventFields(long sequence, String msisdn, String serviceCode, Channel channel,
                                           String offerName) {
        requireFormat(sequence >= 0, "numer zdarzenia nie może być ujemny: " + sequence);
        requireFormat(MSISDN.matcher(msisdn).matches(), "numer abonenta to 9–15 cyfr: " + msisdn);
        requireFormat(!serviceCode.isBlank(), "pusty kod usługi");
        requireFormat(channel != Channel.UNKNOWN, "zdarzenie musi mieć znany kanał");
        requireFormat(!offerName.isBlank(), "pusta nazwa oferty");
    }

    private static void requireFormat(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <T> List<T> requireRows(List<T> rows) {
        Objects.requireNonNull(rows, "brak wierszy partycji");
        for (T row : rows) {
            Objects.requireNonNull(row, "brak wiersza w partycji");
        }
        return List.copyOf(rows);
    }

    private static void requireUniqueKeys(List<ServiceRow> rows) {
        Set<ServiceKey> keys = new HashSet<>();
        for (ServiceRow row : rows) {
            if (!keys.add(row.key())) {
                throw new IllegalArgumentException("powtórzony wiersz w partycji: " + row.key());
            }
        }
    }

    static void main() {
        List<ProvisioningEvent> events = Demo.events();
        Table table = Demo.legacyTable(events);
        ServiceBackfill job = new ServiceBackfill(table, events, Demo.NOW, Demo.POLICY, Demo::sendSms);
        List<PartitionPlan> plan = Demo.printPlan("Plan na sucho, nic jeszcze nie zapisano:", job);
        Report corrections = Demo.printRun("Backfill tylko partycji z poprawkami danych:", job,
                Demo.monthsWith(plan, Action.CORRECTION));
        Report schema = Demo.printRun("Sama zmiana schematu, bez powiadomień:", job,
                Demo.monthsWith(plan, Action.SCHEMA_ONLY));
        Demo.printPlan("Plan po backfillu:", job);
        Demo.dropReplaced(table, corrections, schema);
    }

    private static final class Demo {
        static final YearMonth FIRST = YearMonth.of(2025, 9);
        static final YearMonth NOW = YearMonth.of(2026, 11);
        // w demo partycje mają po kilka wierszy, więc jedna poprawka to już 20–33% partycji
        static final Policy POLICY = new Policy(DEFAULT_WINDOW_MONTHS, 50);
        static final Map<String, String> OFFER_NAMES = Map.of(
                "ROAMING_EU", "Roaming Europa – zniżka świąteczna",
                "ESIM_WATCH", "eSIM w zegarku – łączność bez telefonu",
                "DATA_10GB", "Pakiet danych 10 GB – ważny 30 dni");

        static List<ProvisioningEvent> events() {
            return Stream.of(untouchedSubscriber(), frozenOutOfOrder(), shopActivationArrivedAfterCancel(),
                            shopSuspendArrivedAfterResume(), shopNameReplacedNextMonth())
                    .flatMap(List::stream)
                    .toList();
        }

        static Table legacyTable(List<ProvisioningEvent> events) {
            Table table = new Table();
            for (YearMonth month = FIRST; month.isBefore(NOW); month = month.plusMonths(1)) {
                table.load(month, legacySnapshot(events, month));
            }
            table.load(NOW, currentSnapshot(events, NOW));
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
            Instant occurredAt = LocalDateTime.parse(warsawTime).atZone(ZONE).toInstant();
            String offerName = OFFER_NAMES.get(service);
            String sent = channel == Channel.SHOP ? readAsLatin1(offerName) : offerName;
            return new ProvisioningEvent(sequence, occurredAt, msisdn, service, type, channel, sent);
        }

        // tak kasa w sklepie psuje nazwę: bajty UTF-8 odczytane jako ISO-8859-1
        private static String readAsLatin1(String text) {
            return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        }
    }
}
