# Backfill usług abonenta: partycje miesięczne, walidacja, podmiana i powiadomienia

Kod: pakiet [`collection.backfill`](../../src/java/collection/backfill), główna klasa
[`ServiceBackfill.java`](../../src/java/collection/backfill/ServiceBackfill.java),
testy: [`ServiceBackfillTest.java`](../../test/java/collection/backfill/ServiceBackfillTest.java).

| Pakiet | Co zawiera |
|---|---|
| `collection.backfill` | algorytm (`ServiceBackfill`), zasady (`Policy`), plan (`PartitionPlan`, `Action`), raport (`Report`), demo |
| `collection.backfill.model` | zdarzenia i wiersze: `ProvisioningEvent`, `ServiceRow`, `LegacyServiceRow`, `ServiceKey`, `Status`, `Channel`, `EventType` |
| `collection.backfill.table` | partycje (`Partition`, `LegacyPartition`, `CurrentPartition`) i tabela z podmianą (`Table`) |
| `collection.backfill.notification` | `StatusCorrection` i `Notification` |
| `collection.backfill.text` | naprawa kodowania (`TextRepair`) |

Każda klasa, rekord i typ wyliczeniowy ma własny plik.

Ta notatka tłumaczy pojęcia na jednym przykładzie z telekomunikacji: czym jest backfill, jak ustalić
z góry, których partycji, kolumn i abonentów dotyczy, jak sprawdzić wynik przed zapisem, jak
podmienić dane, żeby nikt nie zobaczył ich w połowie zmiany, i kogo o tym powiadomić.

## Czym jest backfill

Dane pochodne (tabele raportowe, migawki stanu, projekcje) liczymy ze **źródła prawdy**. Gdy
zmienia się logika liczenia (poprawka błędu, nowa kolumna) albo w danych jest luka, nowe dane już
powstają dobrze, ale stare zostają takie, jakie były. **Backfill** przelicza je za przeszłość.

Cały kod opiera się na trzech zasadach:

1. **Źródło prawdy się nie zmienia.** Backfill czyta dziennik zdarzeń i przepisuje tylko dane
   pochodne. Gdyby błąd był w samym źródle, przeliczenie odtworzyłoby go jeszcze raz.
1. **Najpierw wdrożenie poprawionej logiki, potem backfill.** W odwrotnej kolejności proces na żywo
   dalej pisze złe dane do bieżącego miesiąca i backfill nigdy się nie kończy.
1. **Backfill można powtórzyć bez szkody.** Drugi przebieg nic nie zmienia i nikogo nie
   powiadamia (test `backfillIsIdempotent`).

Dlaczego nie pieniądze: saldo konta to suma wszystkich operacji, a operacje już się wydarzyły
(przelewy wyszły, odsetki naliczono). Przeliczenie salda za marzec zmienia wszystko, co od niego
zależało później, a nie da się tego cofnąć. Status usługi da się odtworzyć ze zdarzeń bez skutków
ubocznych: to opis stanu, a nie ruch pieniędzy.

## Sytuacja

Operator ma usługi dodatkowe: roaming, pakiety danych, eSIM w zegarku. Każda zmiana usługi to
**zdarzenie provisioningu** w dzienniku:

| Pole | Przykład | Uwagi |
|---|---|---|
| `sequence` | `21` | numer nadany przy przyjęciu, czyli kolejność przyjścia |
| `occurredAt` | 2 marca 2026, 10:00 | kiedy to się naprawdę stało |
| `msisdn` | `48600100400` | numer abonenta |
| `serviceCode` | `ESIM_WATCH` | |
| `type` | `ACTIVATE`, `SUSPEND`, `RESUME`, `CANCEL` | |
| `channel` | `APP`, `SHOP`, `CALL_CENTER` | było w zdarzeniu od zawsze, nikt go nie zapisywał |
| `offerName` | eSIM w zegarku – łączność bez telefonu | |

Z dziennika powstaje **tabela usług podzielona na partycje miesięczne**. Partycja `2026-03` mówi,
jaki status miała każda usługa na koniec marca czasu warszawskiego. Korzystają z niej rozliczenia,
obsługa klienta i raporty.

W starym przetwarzaniu były dwa błędy:

1. Kasy w sklepach wysyłają zdarzenia nocną paczką, a po awarii integracji nawet z wielodniowym
   opóźnieniem, więc trafiają do dziennika później niż zdarzenia z aplikacji. Stara logika stosowała
   zdarzenia w **kolejności przyjścia** (`sequence`), a nie **wystąpienia** (`occurredAt`).
1. Kasy wysyłają nazwę oferty w UTF-8, a import czytał ją jako ISO-8859-1. W tabeli zamiast
   `zniżka` było `zniÅ¼ka`.

Skoro i tak przepisujemy partycje, przy okazji zmieniamy schemat:

- `planName` dostaje nazwę `offerName` (**zmiana nazwy kolumny**),
- dochodzi `activationChannel` z pola `channel`, które było w zdarzeniach, ale nikt go nie zapisywał
  (**nowa kolumna z nieużywanego dotąd pola**),
- znika `legacyStatusCode`, kolumna dla wyłączonego już systemu rozliczeń (**usunięcie starych
  danych**).

## Zmieniona logika

| | Stara: `legacySnapshot` | Nowa: `currentSnapshot` |
|---|---|---|
| Kolejność zdarzeń | przyjścia (`sequence`) | wystąpienia (`occurredAt`, przy remisie `sequence`) |
| Nazwa oferty | kopiowana bez zmian | z naprawionym kodowaniem |
| Kanał aktywacji | pomijany | kolumna `activationChannel` |
| Kolumny | `planName`, `legacyStatusCode` | `offerName`, `activationChannel` |

Automat stanów usługi jest w obu wersjach ten sam:

```
ACTIVATE  dowolny stan → aktywna        (nazwa i kanał z tej aktywacji)
SUSPEND   aktywna      → zawieszona
RESUME    zawieszona   → aktywna
CANCEL    aktywna albo zawieszona → anulowana
pozostałe przejścia nic nie zmieniają; zdarzenia przed pierwszą aktywacją są pomijane
```

Dlaczego sama kolejność zmienia wynik, na przykładzie abonenta `48600100400`:

| `sequence` | `occurredAt` | Zdarzenie | Kanał |
|---|---|---|---|
| 20 | 28 marca, 18:00 | `CANCEL` | aplikacja |
| 21 | 2 marca, 10:00 | `ACTIVATE` | sklep, przyszło po awarii integracji |

Stara logika bierze najpierw `CANCEL`. Usługa jeszcze nie istnieje, więc zdarzenie przepada. Potem
`ACTIVATE` i usługa jest **aktywna**. Nowa logika: aktywacja 2 marca, anulowanie 28 marca, więc
usługa jest **anulowana**. Abonent zrezygnował z eSIM, a rozliczenia widziały go jako aktywnego. To
go dotyczy, więc dostanie powiadomienie.

## Które dane są dotknięte

To jest główne pytanie przed każdym backfillem. Odpowiadamy na nie na dwa sposoby.

**Rozumowanie.** Partycja to stan na koniec miesiąca, więc błąd z marca zostaje w kwietniu, maju i
dalej, **dopóki późniejsze zdarzenie go nie przykryje**. Abonent `48600100400` aktywował eSIM jeszcze
raz w maju, w aplikacji: od maja obie logiki dają to samo. Dlatego zakres błędu to nie „od marca do
dziś”, tylko marzec i kwiecień. Tak samo z kolumnami i ludźmi:

| Co | Których dotyczy |
|---|---|
| Partycje | od miesiąca pierwszego zdarzenia w złej kolejności do miesiąca, w którym stany się zbiegają |
| Kolumny | `status` (poprawki), `offerName` (kodowanie), `activationChannel` (nowa), `legacyStatusCode` (usunięta) |
| Wiersze | usługi ze zdarzeniami w złej kolejności, jeśli ta kolejność zmienia wynik, i aktywacje ze sklepów |
| Abonenci | tylko ci, którym w poprawianej partycji zmienił się status |

**Pomiar.** Rozumowanie łatwo pomylić, więc `plan(od, do)` po prostu przelicza każdą partycję na
sucho, porównuje ją z obecną i nic nie zapisuje. Dla danych z demo:

| Miesiąc | Akcja | Wiersze | Statusy | Nazwy | Co dalej |
|---|---|---|---|---|---|
| 2025-09 | `FROZEN` | 1 | 0 | 0 | poza oknem |
| 2025-10 | `FROZEN` | 2 | 1 | 0 | błąd jest, ale poprawić nie wolno |
| 2025-11 – 2026-02 | `SCHEMA_ONLY` | 2 | 0 | 0 | tylko nowy schemat, bez powiadomień |
| 2026-03 | `CORRECTION` | 3 | 1 | 1 | backfill i SMS |
| 2026-04 | `CORRECTION` | 3 | 1 | 1 | backfill i SMS |
| 2026-05 | `SCHEMA_ONLY` | 3 | 0 | 0 | stany się zbiegły |
| 2026-06 | `CORRECTION` | 4 | 1 | 0 | backfill i SMS |
| 2026-07 – 2026-08 | `SCHEMA_ONLY` | 4 | 0 | 0 | |
| 2026-09 | `CORRECTION` | 5 | 0 | 1 | backfill bez SMS |
| 2026-10 | `SCHEMA_ONLY` | 5 | 0 | 0 | |
| 2026-11 | `OPEN` | 5 | 0 | 0 | pisze proces na żywo, już nową logiką |

Z 15 partycji poprawki danych potrzebują 4, zmiany samego schematu 8, dwie są zamrożone, jedna
otwarta. Z pięciu abonentów powiadomienie dostaje dwóch.

![Macierz: kolumny to partycje od 2025-09 do 2026-11 z akcją planu, wiersze to usługi pięciu abonentów. Zmiany statusu są w 2026-03 i 2026-04 (abonent 400) oraz 2026-06 (abonent 500), sama nazwa w 2026-09 (abonent 600), błąd w zamrożonej partycji 2025-10 (abonent 300) zostaje. SMS dostają abonenci 400 i 500](service-backfill-partitions.svg)

Znaczenie akcji:

| Akcja | Kiedy | Co robi backfill |
|---|---|---|
| `FROZEN` | miesiąc starszy niż okno | odmawia |
| `OPEN` | bieżący albo przyszły miesiąc | odmawia |
| `UP_TO_DATE` | już w nowym schemacie i taka sama jak przeliczona; albo jej brak, a danych też brak | pomija |
| `SCHEMA_ONLY` | te same dane, inny tylko schemat | przepisuje, nikogo nie powiadamia |
| `CORRECTION` | zmienia się status, nazwa albo zestaw wierszy | przepisuje i powiadamia o zmianach statusu |

**Izolacja.** Operator podaje listę miesięcy. Rozsądna kolejność: najpierw tylko `CORRECTION`, czyli
mały zakres, który da się sprawdzić ręcznie, a schemat później, osobnym przebiegiem, nawet partycja
po partycji. Każda partycja jest liczona wprost z dziennika, a nie z sąsiedniej partycji, więc
poprawa kwietnia nie wymaga najpierw poprawy marca. Ceną jest odtwarzanie całego dziennika do końca
miesiąca dla każdej partycji. Wariant przyrostowy (marzec = luty + zdarzenia z marca) byłby tańszy,
ale wiąże partycje w łańcuch: poprawka w marcu wymusza przeliczenie po kolei wszystkich następnych.

## Krok po kroku

![Przebieg: plan na sucho, sprawdzenie okna (poza oknem odmowa całej listy), nowa wersja obok starej, walidacja (odrzucona partycja zostaje nietknięta), podmiana jedną operacją, powiadomienia po całym przebiegu; później rollback albo dropReplaced](service-backfill-flow.svg)

1. **Plan na sucho.** `plan(od, do)` dla każdego miesiąca robi to samo co backfill, poza zapisem.
1. **Okno.** Miesiąc zamrożony albo otwarty na liście to błąd operatora: `IllegalArgumentException`
   dla całej listy, zanim cokolwiek się zmieni. Błąd w parametrach to wszystko albo nic.
1. **Nowa wersja obok starej.** Nowa partycja powstaje w pamięci, stara dalej jest tą, którą
   wszyscy czytają.
1. **Walidacja.** Gdy nowa wersja nie przechodzi, partycja zostaje odrzucona i nietknięta, problemy
   trafiają do raportu, a reszta listy idzie dalej. Błąd w danych dotyczy jednej partycji.
1. **Podmiana.** Cała partycja zmienia się w jednej operacji, stara wersja zostaje odłożona.
1. **Powiadomienia** po wszystkich partycjach, po jednym na abonenta.
1. **Później:** `dropReplaced(miesiąc)` usuwa starą wersję albo `rollback(miesiąc)` ją przywraca.

## Walidacja: co sprawdzamy i czego nie

Sprawdzanie nowego statusu tym samym kodem, który go policzył, niczego nie dowodzi. Sprawdzamy
niezmienniki, które wykryją błąd nowej logiki albo niepełne źródło:

1. **Nie gubimy wierszy.** Każda usługa ze starej partycji musi być w nowej. Brak oznacza, że w
   dzienniku nie ma jej zdarzeń (na przykład usunięto je po okresie przechowywania). Przeliczenie
   skasowałoby wtedy wiersz, który klient już widział.
1. **Próg zmian statusu.** Jeśli status zmienia się w więcej niż `maxStatusChangePercent` wierszy
   (domyślnie 20%), bardziej prawdopodobne jest, że myli się nowa logika, a nie stara. Na granicy
   (dokładnie 20%) partycja przechodzi. W demo próg wynosi 50%, bo partycje mają po 3–5 wierszy i
   jedna poprawka to już 20–33%. Procent na małych partycjach jest bardzo niestabilny; w praktyce
   dodaje się minimalną liczbę wierszy albo limit bezwzględny.
1. **Żadna nazwa nie ma już zepsutego kodowania.** Jeśli nazwę zepsuto dwa razy, jedna naprawa
   zostawia ją nadal zepsutą. Nie zgadujemy, ile warstw zdjąć: zatrzymujemy partycję, a ktoś to
   ogląda.

## Ewolucja schematu

| Operacja | Stary schemat (v1) | Nowy schemat (v2) | Skąd dane |
|---|---|---|---|
| Zmiana nazwy | `planName` | `offerName` | ta sama wartość, po naprawie kodowania |
| Nowa kolumna | brak | `activationChannel` | `channel` zdarzenia `ACTIVATE`, wcześniej pomijany |
| Usunięcie | `legacyStatusCode` | brak | wyłączony system rozliczeń |

**Odczyt w nowym schemacie (_schema-on-read_).** Partycje v1 i v2 istnieją obok siebie.
`Partition.read()` zwraca wiersze v2 dla obu: wiersz v1 jest podnoszony (`upcast`), czyli `planName`
staje się `offerName`, kanał to `UNKNOWN`, a `legacyStatusCode` jest pomijany. Dzięki temu
czytelnicy nie muszą wiedzieć, które partycje są już przepisane, a backfill nie musi przepisać
wszystkich naraz. To właśnie pozwala poprawić tylko 4 partycje z 15.

Nową kolumnę da się wypełnić za przeszłość tylko wtedy, gdy informacja jest w źródle. Kanał był w
każdym zdarzeniu, tylko nikt go nie zapisywał. Gdyby go tam nie było, backfill nie miałby go skąd
wziąć i za przeszłość zostałoby `UNKNOWN`. Dlatego `UNKNOWN` mają wyłącznie wiersze odczytane ze
starego schematu, a zdarzenie z nieznanym kanałem jest odrzucane.

## Naprawa kodowania

Litera `ż` w UTF-8 to dwa bajty: `C5 BC`. Odczytane jako ISO-8859-1 dają dwa znaki: `Å` (`C5`) i
`¼` (`BC`). Naprawa odwraca ten krok: zamienia znaki z powrotem na bajty ISO-8859-1 i dekoduje je
jako UTF-8. To się zawsze udaje bez strat, bo ISO-8859-1 przypisuje każdej z 256 wartości bajtu
dokładnie jeden znak.

Napis naprawiamy tylko wtedy, gdy spełnia trzy warunki naraz:

1. każdy znak mieści się w ISO-8859-1 (≤ `U+00FF`); `ł` to `U+0142`, więc `Łódź` nie powstało z
   takiego odczytu,
1. jest co najmniej jeden znak ≥ `U+0080`, bo tekst czysto ASCII jest taki sam w obu kodowaniach,
1. bajty są poprawnym UTF-8, sprawdzanym ścisłym dekoderem (`CodingErrorAction.REPORT`).

| Napis | Po naprawie | Dlaczego |
|---|---|---|
| `zniÅ¼ka` | `zniżka` | wszystkie trzy warunki spełnione |
| `Łódź` | bez zmian | `Ł` nie należy do ISO-8859-1 |
| `Café` | bez zmian | samotny bajt `E9` nie jest poprawnym UTF-8 |
| `ASCII` | bez zmian | brak bajtów ≥ `0x80` |
| zepsuty dwa razy | zepsuty raz | walidacja zatrzymuje partycję |

Emoji też przechodzą w obie strony (cztery bajty UTF-8 na jeden znak). Ryzyko: poprawny napis w
ISO-8859-1, który przypadkiem jest też poprawnym UTF-8 (na przykład celowo wpisane `Ã³`), zostałby
„naprawiony”. W nazwach ofert to się nie zdarza. W danych ogólnych naprawę zawęża się do wierszy z
wadliwego źródła, tutaj zdarzeń z kanału `SHOP`.

W terminalu zepsute `ą` wygląda zwykle jak `Ä…`, a nie `Ä` i niewidoczny znak. To Windows-1252, w
którym bajt `85` to `…`. Naprawa zakłada ISO-8859-1, bo tego użył import. W Windows-1252 pięć bajtów
(`81`, `8D`, `8F`, `90`, `9D`) nie ma znaku, więc tam odwrócenie nie zawsze jest bezstratne.

## Podmiana zamiast nadpisywania

Nadpisywanie wiersz po wierszu zostawia partycję w połowie starą, w połowie nową. Fakturowanie, które
przeczyta ją w tej chwili, dostaje dane, których nigdy nie było. Zamiast tego budujemy całą nową
wersję obok, sprawdzamy ją i zmieniamy wskazanie na partycję jedną operacją:
`ConcurrentMap.replace(miesiąc, oczekiwana, nowa)`. Czytelnik widzi całą starą albo całą nową
partycję (test `readersSeeEitherTheWholeOldOrTheWholeNewPartition`). W bazach i jeziorach danych to
samo robi `ALTER TABLE … EXCHANGE PARTITION`, `INSERT OVERWRITE` partycji albo atomowy commit nowej
wersji tabeli.

**Compare-and-swap.** Podmieniamy tylko wtedy, gdy partycja jest wciąż tą, którą przeczytaliśmy.
Jeśli w międzyczasie zmienił ją ktoś inny (drugi backfill), nic nie zostaje nadpisane: partycja
trafia do odrzuconych z problemem „partycja zmieniła się w trakcie backfillu”, a pozostałe idą dalej.
Wyjątek w tym miejscu byłby gorszy: przerwałby przebieg po części podmian i nikt nie dostałby
powiadomień o tych, które już się udały (test `concurrentBackfillsOfOnePartitionApplyItExactlyOnce`).

**Stara wersja zostaje odłożona.** `rollback(miesiąc)` przywraca ją jedną operacją,
`dropReplaced(miesiąc)` usuwa ją na dobre i zwraca liczbę usuniętych wierszy. Usuwamy dopiero po
sprawdzeniu wyniku (raporty, wyrywkowa kontrola); potem wycofania już nie ma. Gdy tę samą partycję
poprawimy drugi raz przed usunięciem, zostaje najstarsza wersja, więc wycofanie wraca do stanu sprzed
pierwszego backfillu.

## Kogo powiadamiamy

| Sytuacja | Powiadomienie |
|---|---|
| Zmienił się status usługi w poprawianej partycji | tak |
| Poprawiona tylko nazwa oferty | nie, to kosmetyka |
| Sama zmiana schematu | nie, klient niczego nie widzi |
| Błąd w zamrożonej partycji | nie, partycja się nie zmienia; to sprawa dla procesu reklamacji |
| Utworzona brakująca partycja | nie, nikt nie widział jej starej wersji |
| Drugi przebieg tego samego backfillu | nie, nic się nie zmieniło |

Wysyłka idzie po podmianie partycji, więc jej błąd nie może cofnąć backfillu. Każde powiadomienie
wysyłamy osobno: gdy bramka SMS rzuci wyjątek przy jednym abonencie, pozostali i tak dostają swoje,
a nieudane trafiają do `Report.undelivered`, skąd można je wysłać ponownie.

Jeden abonent dostaje **jedno** powiadomienie na przebieg, ze wszystkimi poprawkami. Abonent
`48600100400` ma poprawki w marcu i w kwietniu i dostaje jeden SMS, a nie dwa:

```
Poprawiliśmy status Twoich usług: eSIM w zegarku – łączność bez telefonu w 2026-03: aktywna → anulowana; eSIM w zegarku – łączność bez telefonu w 2026-04: aktywna → anulowana.
```

Powiadomienia wychodzą po wszystkich podmianach, a nie po każdej, i tylko dla partycji, które
naprawdę podmieniono. Wiadomość nie może obiecać poprawki, którą chwilę później odrzuci walidacja.

## Dlaczego nie wolno ruszać starych partycji

Okno backfillu to `Policy.backfillWindowMonths` ostatnich zamkniętych miesięcy, domyślnie 12. Przy
bieżącym miesiącu 2026-11 wolno ruszać 2025-11 … 2026-10. Starsze są `FROZEN`:

- rozliczenie jest zamknięte, a faktury wystawione; zmiana statusu wstecz rozjeżdża tabelę z
  dokumentami, które klient ma w ręku,
- minął czas na reklamacje; korekty idą przez proces reklamacyjny, a nie po cichu,
- źródło za odległe okresy bywa niepełne (dziennik ma swój czas przechowywania), więc przeliczenie
  mogłoby zgubić wiersze,
- zamknięte okresy mają być niezmienne dla audytu.

Plan i tak pokazuje, co zmieniłoby się w zamrożonej partycji: 2025-10 ma jedną zmianę statusu.
Wiemy więc, że błąd tam sięga, i możemy na przykład przejrzeć ten przypadek ręcznie.

Bieżący miesiąc jest `OPEN`: pisze do niego proces na żywo, już z nową logiką. Backfill ścigałby się
z nim o tę samą partycję.

## Założenia

| | |
|---|---|
| Numer abonenta (MSISDN) | 9–15 cyfr ASCII, bez `+` i spacji; inaczej `IllegalArgumentException` |
| Numer zdarzenia `sequence` | ≥ 0 i niepowtarzalny w dzienniku; inaczej `IllegalArgumentException` |
| Kanał zdarzenia | `APP`, `SHOP` albo `CALL_CENTER`; `UNKNOWN` ma tylko wiersz odczytany ze starego schematu |
| Granica partycji | północ pierwszego dnia miesiąca w `Europe/Warsaw` |
| Zdarzenia o tym samym czasie | w kolejności `sequence` |
| Zawieszenie, wznowienie, anulowanie przed pierwszą aktywacją | pomijane |
| Ponowna aktywacja po anulowaniu | dozwolona, nazwa i kanał z nowej aktywacji |
| Brak partycji w tabeli | backfill ją tworzy (wypełnia lukę) |
| Pusty dziennik, pusta tabela, pusta lista miesięcy | poprawne wejście |
| Powtórzony miesiąc na liście | liczony raz |
| Miesiąc zamrożony albo otwarty na liście | `IllegalArgumentException`, nic nie jest zmienione |
| Partycja nie przechodzi walidacji | odrzucona i nietknięta, pozostałe idą dalej |
| Partycję zmienił w tym czasie ktoś inny | odrzucona, nic nie nadpisane, pozostałe idą dalej |
| `null` jako parametr albo element listy | `NullPointerException` |

## Złożoność

Przeliczenie jednej partycji to O(z log z) dla `z` zdarzeń (filtr i sortowanie dziennika), porównanie
ze starą wersją O(w) dla `w` wierszy, przez mapę po kluczu `(msisdn, serviceCode)`. Plan albo
backfill `p` partycji kosztuje O(p · (z log z + w)). Test `largeLogIsBackfilledWithTheDefaultPolicy`
przelicza 10 partycji, do 30 tysięcy usług w każdej, w ułamku sekundy.

## Pułapki

- **Poprawianie źródła zamiast danych pochodnych.** Dziennik zostaje taki, jaki przyszedł; inaczej
  tracimy możliwość policzenia czegokolwiek od nowa.
- **Granica miesiąca w UTC.** Zdarzenie z 1 kwietnia, 00:30 czasu warszawskiego to 31 marca, 22:30
  UTC. Liczone w UTC trafiłoby do marca (test `partitionBoundaryIsMidnightInWarsaw`).
- **Remis czasu bez drugiego klucza.** Dwa zdarzenia z tą samą sekundą muszą mieć ustaloną kolejność,
  inaczej wynik zależy od kolejności na wejściu.
- **Powiadamianie o każdej zmianie wiersza.** Poprawka nazwy albo schematu nie dotyczy klienta, a SMS
  o niczym uczy go ignorować wiadomości od operatora.
- **Powiadamianie po każdej partycji.** Jeden abonent dostaje wtedy kilka SMS-ów o tym samym.
- **Usunięcie starej wersji od razu.** Wtedy nie ma do czego wrócić, gdy wynik okaże się zły.
- **Porównanie partycji przez `equals`.** Compare-and-swap porównuje tu zawartość, czyli O(w).
  W prawdziwej bazie porównuje się numer wersji partycji (blokada optymistyczna).

## Czego model nie uwzględnia

- **Outbox.** Powiadomienia wychodzą w tym samym procesie po podmianie. Gdy proces padnie między
  jednym a drugim, dane są poprawione, a nikt o tym nie wie. W produkcji powiadomienia zapisuje się
  w tej samej transakcji co podmianę, a osobny proces je wysyła i ponawia.
- **Zdarzenia spóźnione po backfillu.** Jeśli zdarzenie z marca przyjdzie w maju, plan znów pokaże
  marzec jako `CORRECTION`; backfill trzeba powtórzyć (test `secondBackfillKeepsTheOriginalVersionForRollback`).
- **Duże partycje.** Tu cała partycja mieści się w pamięci. Przy milionach wierszy nową wersję pisze
  się strumieniowo do tabeli tymczasowej i dopiero ją podmienia.

## Uruchomienie

```sh
mvn -q compile && java -cp target/classes collection.backfill.ServiceBackfill
mvn test -Dtest='ServiceBackfillTest'
```

Demo drukuje plan, poprawia tylko partycje z `CORRECTION` (dwa SMS-y), potem osobnym przebiegiem
przepisuje schemat (bez SMS-ów), drukuje plan po backfillu (wszystko `UP_TO_DATE` poza `FROZEN` i
`OPEN`) i usuwa stare wersje: 39 wierszy w starym schemacie.
