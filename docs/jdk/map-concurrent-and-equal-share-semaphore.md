# `Gatherers.mapConcurrent` i semafor, który dzieli po równo

Notatka do [`test/java/jdk/EqualShareSemaphore.java`](../../test/java/jdk/EqualShareSemaphore.java) i
trzech zestawów testów obok: [`MapConcurrentTest`](../../test/java/jdk/MapConcurrentTest.java),
[`SemaphoreTest`](../../test/java/jdk/SemaphoreTest.java) i
[`EqualShareSemaphoreTest`](../../test/java/jdk/EqualShareSemaphoreTest.java).

Ten pakiet trzyma implementację w `src`, a wszystko inne w testach jednostkowych, bez klas z
przykładami do uruchamiania. Każdą liczbę tutaj zmierzono na JDK 25.0.1 (Temurin), 24 procesory,
Intel i7-14650HX, albo tymi testami, albo celowo uszkodzoną kopią kodu (zob. sekcję „Wyniki mutacji”
niżej); nieliczne liczby z benchmarków przepustowości są oznaczone tam, gdzie się pojawiają, bo
benchmark to nie test jednostkowy i nie leży w repozytorium.

**Jedno pytanie.** Współbieżne przetwarzanie elementów strumienia jest łatwe. Pytanie, które decyduje,
czy to zadziała na produkcji, jest węższe: _ile elementów jest naraz w locie i kto wybrał tę liczbę?_
`Gatherers.mapConcurrent` odpowiada na nie liczbą, którą przekazujesz. Druga połowa tej notatki dotyczy
przypadku, gdy odpowiedź ma być **na wywołującego**, czyli jedynej rzeczy, której ta liczba nie wyrazi.

## `mapConcurrent` w jednym akapicie

`Gatherers.mapConcurrent(maxConcurrency, mapper)` to gatherer **sekwencyjny**, ostateczny od JDK 24
(JEP 485), więc na JDK 25, na który celuje ten projekt, nie potrzeba flagi preview. Uruchamia mapper
każdego elementu na osobnym **wątku wirtualnym**, za oknem `maxConcurrency` zezwoleń, i oddaje wyniki
w kolejności napotkania. To połączenie czyni go właściwym dla pracy blokującej (wejście/wyjście,
zapytania, wywołania zdalne), gdzie użyteczna szerokość nie ma nic wspólnego z liczbą rdzeni.

```java
List<Response> out = urls.stream()
        .gather(Gatherers.mapConcurrent(500, this::httpGet))   // dokładnie 500 w locie
        .toList();                                             // w kolejności urls
```

| Własność | Zmierzone |
|---|---|
| Szczytowa współbieżność | **dokładnie** `maxConcurrency`: 1, 8 i 500 osiągają swoją liczbę |
| Wątek na element | za każdym razem wątek wirtualny; wątek wywołujący zostaje nietknięty i to on jest blokowany przez przeciwciśnienie |
| Kolejność napotkania | zachowana, nawet gdy praca kończy się od końca |
| Czytanie z wyprzedzeniem | dokładnie `maxConcurrency` elementów, więc nieskończone źródło jest bezpieczne |
| Błąd | własny wyjątek mappera, nieopakowany, dostarczony **w kolejności napotkania** |
| Przerwanie (short-circuit) | wraca bez opróżniania okna |
| `maxConcurrency <= 0` | `IllegalArgumentException: 'maxConcurrency' must be greater than 0` |
| wyniki `null` | dozwolone, przechodzą dalej w dół strumienia |

## Szerokość to liczba, a nie własność maszyny

Trzy okna nad pracą blokującą, na maszynie z 24 rdzeniami:

| Okno | Elementy × praca | Szczyt w locie | Czas zegarowy |
|---:|---|---:|---:|
| 1 | 32 × 5 ms | 1 | — |
| 8 | 64 × 20 ms | 8 | 164–170 ms |
| 500 | 1 000 × 20 ms | **500** | 45–57 ms |

500 współbieżnych blokujących wywołań na 24 rdzeniach to nie sztuczka: wątek wirtualny zaparkowany w
`sleep` albo w odczycie z gniazda kosztuje obiekt na stercie, a nie rdzeń. Czas zegarowy wynika wprost
z szerokości: 1 000 elementów × 20 ms blokowania to 20 sekund pracy, które kończą się w około 50 ms.

Ograniczenie to okno zezwoleń, więc szczyt równa się oknu dokładnie, w każdym przebiegu. To nie pula
wątków, która może być zajęta czymś innym, i nie jest współdzielona z żadnym innym potokiem.

## Kolejność jest zachowana, opóźnienie nie

Mapper elementu 0 może być najwolniejszy w partii, a wynik i tak zaczyna się od elementu 0. Przykład
celowo odwraca opóźnienia (element _i_ śpi `5 × (n - i)` ms, więc kolejność kończenia jest dokładnie
odwrotna) i dostaje z powrotem `[0, 1, 2, … 15]`.

To własność, za którą płaci zachowanie przy błędach opisane niżej. Nie da się mieć naraz „wyników w
kolejności napotkania” i „pierwszy błąd od razu wszystko zatrzymuje”.

## Leniwość i co tu naprawdę znaczy przeciwciśnienie

Nieskończone źródło, okno 4, `limit(10)` dalej w strumieniu:

```java
Stream.iterate(0, i -> i + 1)
      .gather(Gatherers.mapConcurrent(4, this::work))
      .limit(10)
      .toList();
```

Zmierzone: 10 wziętych, **11 wygenerowanych, 12 uruchomionych mapperów**. Nic nie ucieka: okno _jest_
czytaniem z wyprzedzeniem. A gdy konsument celowo zatrzymał się po jednym elemencie (`Iterator`, potem
200 ms przerwy), za oknem 5 uruchomiło się dokładnie **5 mapperów**. Producent porusza się w tempie
konsumenta plus jedno okno.

To prawdziwe przeciwciśnienie i dlatego ten kształt przetrwa źródło, którego nie stać cię na
zmaterializowanie: kursor, plik, gniazdo.

## Błąd wychodzi w kolejności napotkania

To najmniej oczywista rzecz w `mapConcurrent` i najważniejsza do poznania przed postawieniem go na
ścieżce krytycznej. Wyjątek mappera **nie** jest zgłaszany wtedy, gdy zostaje rzucony. Jest zgłaszany,
gdy przychodzi _kolej_ tego elementu, po oddaniu każdego wcześniejszego elementu.

Okno 8, każdy element blokuje 100 ms:

| Mapper rzuca na | Wyjątek wychodzi po | Uruchomione mappery |
|---:|---:|---:|
| elemencie 0 | 2–5 ms | 8 |
| elemencie 20 | 301 ms | 24–25 |

301 ms to trzy okna po 100 ms: elementy 0–19 muszą zostać oddane najpierw. **Opóźnienie błędu to
opóźnienie jego pozycji**, a nie chwili, w której się zdarzył. Trzy konsekwencje:

1. Błąd głęboko w długim strumieniu to wolny błąd. Jeśli szybkie przerwanie jest ważniejsze niż
   kolejność, sprawdź warunek błędu _przed_ kosztownym wywołaniem albo niech błędy płyną jako wartości
   (`Either`/`Result`) w kolejności, jak wszystko inne.
2. Praca już w locie dalej działa: okno nie jest anulowane, tylko się opróżnia.
3. Wyjątek przychodzi **nieopakowany**: przykład dostaje swój własny `IllegalStateException` z własnym
   komunikatem, a nie owinięty w `ExecutionException` czy `CompletionException`.

I lustrzane odbicie: błąd, do którego konsument nigdy nie dojdzie, nigdy się nie zdarza. Z `limit(3)`
przed elementem, który rzuca na indeksie 30, wynik to `[0, 1, 2]` i uruchomiło się tylko 4–8 mapperów.

## Przerwanie nie opróżnia okna

`findFirst()` nad oknem 16, gdzie element 0 trwa 10 ms, a pozostałe 15 śpi po 1 500 ms, zwraca element 0
po **12–15 ms**. Maruderzy są porzucani, a nie oczekiwani. Uwaga na asymetrię wobec ścieżki błędu
wyżej: przerwanie jest darmowe, a błąd kosztuje wszystko, co stoi przed nim w kolejności napotkania.

Druga połowa tej asymetrii to zagrożenie. Operacja końcowa, która _nie_ przerywa (`toList`, `forEach`,
`reduce`), musi skonsumować każdy element, więc mapper, który nigdy nie wraca, parkuje potok na zawsze,
a przerwanie wątku, który wywołał operację końcową, go nie uwalnia. Odkryte w bolesny sposób: zawiesiło
przebieg testów na 900 s (zob. sekcję „Wyniki mutacji”). Wszystko, co może blokować bez końca, wymaga
limitu czasu _wewnątrz_ mappera, a nie na zewnątrz strumienia.

## Czego okno nie wyrazi

`maxConcurrency` to **jedna liczba dla całego potoku**. Nie wyrazi:

- _„najemca A może mieć trzy takie, najemca B trzy i nikt nie może być głodzony”_: okno jest jedno i
  działa według kolejności przyjścia;
- _„ten wywołujący przekroczył swój budżet”_: okno w ogóle nie zna pojęcia wywołującego.

Jedno zachłanne źródło elementów wypełnia całe okno, a wszystko inne czeka za nim w kolejce. Tę lukę
wypełnia druga połowa notatki: nie jako zamiennik `mapConcurrent`, tylko jako coś, co wkłada się _do_
mappera.

## Ostre krawędzie zwykłego `Semaphore`

Zanim cokolwiek na nim zbudujemy: części `java.util.concurrent.Semaphore`, które nie zachowują się jak
blokada. Wszystkie sprawdzone w `SemaphoreTest`:

| Zachowanie | Zmierzone |
|---|---|
| `release()` bez pasującego `acquire()` | **tworzy** zezwolenie: `Semaphore(2)` plus 3 gołe zwolnienia ma **5** |
| Kto może zwolnić | ktokolwiek: zwalniający wątek nie musi być tym, który zajął |
| 2 wywołujących × dwa razy `acquire(2)`, 4 zezwolenia | **zakleszczenie**: 0 wolnych, oba zaparkowane, nie ma wątku, który mógłby zwolnić |
| to samo z `tryAcquire(2, timeout)` | przechodzi **najwyżej jeden** (0 albo 1, wyścig), nigdy oba |
| `acquire(4)` w jednym wywołaniu | bez trzymania i czekania; obaj kończą, a pula wraca |
| `tryAcquire()` na semaforze **sprawiedliwym** | **wpycha się** przed zaparkowane `acquire(5)`: sprawiedliwość go nie dotyczy |
| `tryAcquire(1, 50 ms)` na semaforze sprawiedliwym | staje w kolejce za nim (`false`), gdzie niesprawiedliwy oddaje zezwolenie (`true`) |
| `drainPermits()` przy 2 z 5 zajętych | bierze 3 wolne i zamyka bramę; 2 zajęte zostają nietknięte |
| ponowne otwarcie po opróżnieniu | `release(taken)`: oddajesz to, co wziąłeś, a nie pierwotną liczbę |
| `new Semaphore(0)` + `acquire(n)` | zatrzask „czekaj na n zakończeń”, i w odróżnieniu od `CountDownLatch` wielokrotnego użytku |
| `new Semaphore(-2)` | zaczyna z długiem: trzy zwolnienia, zanim jedno zajęcie się uda |

Trzy z tych wierszy to powód istnienia następnej sekcji:

- **Zezwolenia nie mają właściciela.** Podwójne zwolnienie to nie błąd, tylko cicha zmiana rozmiaru
  puli. `EqualShareSemaphore` wydaje `Ticket` typu `AutoCloseable`, którego `close()` jest idempotentne,
  więc tego samego zezwolenia nie da się oddać dwa razy; to własność opakowania, a nie semaforów.
- **Dwukrotne zajmowanie prowadzi do zakleszczenia.** `acquire(n)` działa na zasadzie wszystko albo nic,
  więc proś o wszystko w jednym wywołaniu. Deterministyczna demonstracja wymusza przeplot zatrzaskiem;
  bez tego dwaj wywołujący zwykle się mijają i błąd się chowa, i dokładnie tak trafia na produkcję.
- **`tryAcquire()` ignoruje sprawiedliwość.** Flaga sprawiedliwości porządkuje `acquire` i
  `tryAcquire(timeout)`, a bezargumentowe `tryAcquire()` według dokumentacji wpycha się niezależnie od
  niej. To najostrzejsza krawędź tej klasy i wzmacnia tezę następnej sekcji: sprawiedliwość dotyczy
  _kolejności_, nigdy tego, _ile_ może trzymać jeden wywołujący.

## `EqualShareSemaphore`: gwarancja, a nie kolejność

Problem w najmniejszej postaci: `Semaphore(10)` współdzielony przez trzech najemców, najemca 0 bierze
wszystkie dziesięć i je trzyma. Najemcy 1 i 2 nie dostają nic tak długo, jak najemca 0 chce. Zmierzone
w przykładzie: _„druga strona dostaje 0 z 10 zezwoleń”_.

Przy `total` zezwoleniach i `parties` stronach:

```
share   = total / parties           // dzielenie całkowite: gwarantowana rezerwacja
surplus = total - parties * share   // reszta: według kolejności przyjścia
```

Żądanie strony _p_ jest wpuszczane, gdy **albo**

1. `held[p] < share`: wydaje własną rezerwację, **albo**
2. `surplusHeld < surplus`: jest ponad swoim udziałem, a zapas jest dostępny.

To cały algorytm; w kodzie to dwie linie. `held[p]` to bieżący stan strony, `surplusHeld` to suma
`max(0, held[q] - share)` po wszystkich stronach.

### Dlaczego reguła 1 nie sprawdza sumy

Reguła 1 wpuszcza bez patrzenia na `totalHeld`, co wygląda niebezpiecznie, a takie nie jest. Każda
strona ponad swoim udziałem bierze z puli zapasu, więc _pozostałe_ strony razem mogą trzymać najwyżej
`(parties-1)*share + surplus`. Zatem zawsze, gdy `held[p] < share`:

```
totalHeld = others + held[p] <= (parties-1)*share + surplus + held[p] < parties*share + surplus = total
```

Wolne zezwolenie na pewno istnieje. To jest gwarancja, i to mocna:

> **Strona poniżej swojego udziału nigdy nie czeka**, niezależnie od tego, co robią wszystkie inne.

Dowód jest sprawdzany w czasie działania, a nie przyjmowany na wiarę: `requireCapacity` rzuca wyjątek,
jeśli strona zostanie wpuszczona poniżej udziału, gdy wszystkie zezwolenia są wydane, a `tryAcquire`
liczy każdą odmowę daną wywołującemu poniżej udziału. Przy 32 wątkach walczących o 12 zezwoleń między 4
stronami przez 300 ms: 116 000–255 000 zajęć zależnie od obciążenia maszyny, szczyt 12/12 w locie, 0
ponad limit, **0 odmów poniżej udziału**.

### Ile to kosztuje: nie wykorzystuje całej pojemności

Rezerwacja to prawdziwa pojemność, odłożona na bok. Strona nigdy nie przekroczy `share + surplus` (4 z
10 w przykładzie), nawet gdy wszystkie inne są bezczynne. Oddanie rezerwacji bezczynnej strony komuś
innemu i odebranie jej na żądanie wymaga **wywłaszczania**, a wydanego zezwolenia nie da się cofnąć:
trzymający jest w środku wywołania bazy danych. Wszystkie alternatywy są gorsze na konkretny sposób:

| Projekt | Gwarancja | Wykorzystuje całą pojemność | Koszt |
|---|---|---|---|
| samo `mapConcurrent(n)` | brak: jedno zachłanne źródło wypełnia okno | tak | nic |
| `Semaphore(total)` | brak: głodzenie | tak | nic |
| `Semaphore(share)` na stronę, bez dzielenia | tak | nie | reszta jest bezużyteczna |
| **Ta brama** | tak | nie | jedna blokada, `signalAll` przy każdym zwolnieniu |
| Dzierżawa z odwołaniem / ważona sprawiedliwa kolejka | tak | tak | wywołujący muszą dać się przerwać w trakcie pracy |

### Czy nie wystarczą dwa złożone `Semaphore`?

Prawie. `Semaphore own = new Semaphore(share)` na stronę plus wspólny `Semaphore(surplus)` daje te same
reguły wpuszczania: spróbuj własnego, w razie czego weź z zapasu. Psuje się na _czekaniu_: strona ponad
udziałem musi czekać, aż zwolni się **albo** jej własna pula, **albo** zapas, a nie da się czekać na dwa
semafory naraz. Trzeba by odpytywać jeden z nich z limitem czasu. Jedna blokada z jednym warunkiem, czyli
to, co robi klasa, to wersja bez kręcenia się w pętli.

To samo rozumowanie tłumaczy `signalAll()` w `close()`. `signal()` kusi, bo wszyscy czekający to wątki
ponad udziałem czekające na ten sam warunek, ale `tryAcquire` z limitem czasu, który dostanie sygnał
nanosekundę po tym, jak się poddał, połknie ten sygnał, a inny czekający prześpi dostępne zezwolenie.
Całkowite usunięcie sygnału zawiesza przykład; zamiana na `signal()` **nie** jest łapana przez żadne
sprawdzenie tutaj i właśnie dlatego jest zapisana w bezpieczny sposób.

Blokada to `ReentrantLock`, a nie `synchronized`, co ma tu znaczenie: mapper w `mapConcurrent` działa na
wątku wirtualnym, a `ReentrantLock`/`Condition` parkują go bez trzymania wątku nośnego.

## Sprawiedliwość to kolejność; udział to ilość

`new Semaphore(1)` się wpycha: wątek, który zwalnia zezwolenie, może je ponownie zająć, zanim czekający
w kolejce w ogóle zostanie zaplanowany. `new Semaphore(1, true)` oddaje je zamiast tego najdłużej
czekającemu. Osiem wątków w ciasnej pętli zajmij/zwolnij przez 300 ms, uruchomionych z zatrzasku, żeby
okno było identyczne dla wszystkich:

| | Zajęcia | Przekazanie samemu sobie |
|---|---:|---:|
| `Semaphore(1)` (wpychanie) | 0.87–3.6 M | **97.6–98.1 %** |
| `Semaphore(1, true)` (sprawiedliwy) | 28–66 K | **0.00 %** |

Przekazanie samemu sobie to odsetek zajęć, w których zezwolenie wróciło prosto do wątku, który je
właśnie zwolnił. Przy 98% niesprawiedliwy semafor prawie przestaje być zasobem współdzielonym: jeden
wątek wykonuje prywatną sekcję krytyczną, a siedem czeka. Kolejność usuwa to całkowicie i kosztuje 18–62
razy, bo każde przekazanie staje się parkowaniem i budzeniem, zamiast zostać w pamięci podręcznej
zwalniającego wątku.

**0.00% to wynik na spokojnej maszynie, a nie gwarancja.** Pod pełnym obciążeniem procesorów (cały
zestaw testów naraz albo 24 zajęte rdzenie) sprawiedliwy semafor oddawał zezwolenie samemu sobie w
23–24% zajęć i test z progiem 10% padał. To nie złamana sprawiedliwość: system wywłaszcza pozostałe
wątki, gdy są między `release()` a kolejnym `acquire()`, więc kolejka jest pusta, a zwolnione zezwolenie
legalnie wraca do jedynego chętnego. Gwarancja jest węższa: **zezwolenie zwolnione, gdy ktoś czeka w
kolejce, nie wraca do zwalniającego wątku przed tym czekającym.** Test liczy więc tylko takie
przypadki (`hasQueuedThreads()` sprawdzane tuż przed `release()`) i wymaga dokładnie zera: dla
sprawiedliwego semafora to 0 w 10 na 10 przebiegów pod pełnym obciążeniem, a dla niesprawiedliwego
podstawionego w tym samym teście 3 354 682 z 3 422 311.

**Jak tego nie mierzyć.** Dwie oczywiste miary niczego tu nie mierzą, a pierwsza wersja tego przykładu
używała obu:

- _Stała liczba zajęć na wątek._ Liczby wychodzą równe z samej konstrukcji, cokolwiek robi semafor.
- _Liczby na wątek w stałym oknie czasu._ Wygląda rozsądnie, a dominuje w niej rozrzut startu: przebieg
  sprawiedliwy dostaje dziesiątki tysięcy kolejek wobec milionów w niesprawiedliwym, więc wątek, który
  wystartował później, wygląda na dużą nierównowagę. Zmierzone dla 4, 8, 24 i 48 wątków, rozrzut
  **sprawiedliwego** przebiegu często był gorszy z dwóch (46% wobec 14% niesprawiedliwego przy 8
  wątkach, 88% wobec 54% przy 24), a wahał się od 0% do 89% między przebiegami. Miara, która ocenia
  sprawiedliwy jako mniej sprawiedliwy i nie zgadza się sama ze sobą, to szum.

Wpychanie to pytanie o przekazanie, więc liczyć trzeba właśnie przekazanie. I zwróć uwagę, co kupuje
kolejność: równe kolejki wśród wątków, które **już czekają**. Nie mówi nic o tym, ile zezwoleń może
trzymać wywołujący, więc:

> Sprawiedliwy semafor nie powstrzyma jednego wywołującego przed wzięciem wszystkich zezwoleń i
> zatrzymaniem ich.

### Pokrętło `fairQueueing` i jego cena

Flaga konstruktora porządkuje tylko rywalizację o _zapas_; gwarancja od niej nie zależy, bo strona
poniżej udziału w ogóle nie staje w kolejce. Jej cena, 32 wątki na 12 zezwoleniach, pięć przebiegów:

| Brama | Przepustowość (doraźny benchmark, nie w repozytorium) | wobec zwykłego |
|---|---:|---:|
| `Semaphore(12)` | 8.5–10.5 M operacji/s | 1× |
| `EqualShareSemaphore(12, 4)` | 5.0–10.8 M operacji/s | 0.9–2.1× |
| `EqualShareSemaphore(12, 4, true)` | 31–59 K operacji/s | 148–286 razy wolniej |

Środkowy wiersz obejmuje 1×: między przebiegami brama bywa wolniejsza, a czasem szybsza niż zwykły
`Semaphore`, i tak rozsądnie czytać różnicę poniżej 2 razy w takim mikrobenchmarku: księgowość dla
gwarancji to jedna blokada, dwa porównania i `signalAll`, i tego nie widać. Cały koszt to flaga
`fairQueueing`, dwa rzędy wielkości, i dlatego domyślnie jest niesprawiedliwie.

## Obie połowy razem

Czterech najemców, cztery niezależne potoki `mapConcurrent`, każdy z oknem **40** (szerszym, niż
najemcy mogliby wykorzystać), i jeden `EqualShareSemaphore(12, 4)` zajmowany w mapperze:

```java
IntStream.range(0, itemsEach).boxed()
        .gather(Gatherers.mapConcurrent(40, i -> {          // tu nie ma użytecznego ograniczenia
            EqualShareSemaphore.Ticket permit = acquire(gate, tenant);
            try (permit) {                                  // ograniczenie jest tutaj
                return work(i);
            }
        }))
        .forEach(…);
```

Szczytowa współbieżność na najemcę: **`[3, 3, 3, 3]`**: okno wpuściło 40, brama 3.

Kontrola jest równie ważna jak wynik. Powtórzone z bramą zbyt szeroką, żeby ograniczała
(`EqualShareSemaphore(400, 4)`, udział 100), ten sam potok daje **`[40, 40, 40, 40]`**: przejmuje okno.
Bez tego drugiego pomiaru `[3, 3, 3, 3]` byłoby zgodne z tym, że robotę wykonuje okno, i sprawdzenie
przechodziłoby z niewłaściwego powodu.

`Ticket` jest `AutoCloseable`, a jego `close()` jest idempotentne, więc ponowienie, zabłąkana kopia albo
`finally`, które wykona się dwa razy, nie zwiększą liczby zezwoleń; usunięcie tej ochrony jest łapane.

## Czym to nie jest

- **Nie ogranicznikiem szybkości.** Zezwolenia ograniczają _współbieżność_ (rzeczy w locie), a nie
  _tempo_ (rzeczy na sekundę). 12 zezwoleń × 10 ms pracy to 1 200 operacji/s; te same 12 zezwoleń × 1 s
  pracy to 12 operacji/s. Do tempa używa się żetonów uzupełnianych według zegara.
- **Nie ważonym.** Każda strona dostaje `total / parties`. Wagi oznaczają zastąpienie liczby `share`
  tablicą na strony; reguły wpuszczania poza tym się nie zmieniają.
- **Nie planistą.** Brama decyduje, _czy_ strona może wziąć kolejne zezwolenie, nigdy _który_ z jej
  wątków idzie pierwszy; od tego są `fairQueueing` i `ReentrantLock`.
- **Nie wielowejściowym.** Wątek, który zajmie dwa razy, trzyma dwa zezwolenia, a dwa zagnieżdżone w
  jednej stronie z `share = 1` to zakleszczenie z samym sobą. Zezwolenia to nie blokady.
- **Nie zamiennikiem okna `mapConcurrent`.** Oba ograniczają co innego: okno ogranicza potok, brama
  najemcę. Zostaw oba.

## Wzorce w testach

`test/java/jdk/`: 70 testów w trzech zestawach, cztery niezależne wzorce, więc nic nie opiera się na
tym, że implementacja świadczy sama za siebie:

| Wzorzec | Skala | Co łapie |
|---|---|---|
| Wyczerpujące przejście stanów | każdy poprawny wektor stanu dla 7 konfiguracji, budowany w obu kolejnościach: 3 458 stanów | regułę wpuszczania błędną _gdziekolwiek_ w przestrzeni stanów |
| Scenariusze deterministyczne | zachłanna strona wobec głodzonej, budzenie, przerywanie, limity czasu, podwójne zamknięcie | udokumentowane zachowania, łącznie z tymi, które są kompromisami |
| Losowy test obciążeniowy | 24 wątki na 12 zezwoleniach, oba ustawienia sprawiedliwości | wyścigi, wycieki, zgubione pobudki |
| Semantyka JDK | każde twierdzenie wyżej o `mapConcurrent` i zwykłym `Semaphore` | przyszłe JDK, które je zmieni, albo błędne twierdzenie w tej notatce |

„Poprawny” w pierwszym wierszu jest zdefiniowany na podstawie _specyfikacji_ (żadna strona ponad
`share + surplus`, łącznie nie więcej niż `surplus` pożyczonych zezwoleń), a nie reguł wpuszczania, więc
przejście sprawdza kod względem specyfikacji, a nie powtarza kodu. W każdym stanie sprawdza gwarancję
wprost: każda strona poniżej udziału jest wpuszczana od razu, każda na swoim limicie dostaje odmowę, a
wszystkie zezwolenia wracają.

Tam, gdzie twierdzenie na to pozwala, test jest zatrzaskiem, a nie stoperem. „Błąd jeszcze nie wyszedł”
jest sprawdzane przez to, że wątek potoku wciąż czeka na zatrzasku kontrolowanym przez test, a nie
przez czas trwania, który obciążona maszyna CI mogłaby przekroczyć.

```
mvn test                                              # 150 testów, ~6 s
```

## Wyniki mutacji

Każde uszkodzenie jest nakładane na _kopię_ repozytorium, a zestaw testów uruchamiany na niej.

| Mutacja | Złapana przez |
|---|---|
| `EqualShareSemaphore`: usunięta reguła 2 (zapas bez ochrony) | **12 testów** |
| reguła 1 przesunięta o jeden (`held <= share`) | **11 testów** |
| `take` zapomina `surplusHeld++` | **5 testów** |
| `close` zapomina `surplusHeld--` | **3 testy** |
| `close` nie jest idempotentne | **1 test** |
| `tryAcquire` ignoruje limit czasu | **2 testy** |
| brak `signal` przy zwolnieniu | **5 testów**, po 214 s przekroczeń czasu |
| `signalAll` → `signal` | **nic** |
| ignorowana flaga `fairQueueing` | **nic** |
| `requireCapacity` nigdy nie rzuca | **nic** (celowo, zob. niżej) |
| okno na najemcę 40 → 3 | **1 test** (kontrola) |
| zezwolenie zwalniane przed pracą, a nie po | **1 test** |
| ponowne otwarcie po opróżnieniu zwalnia pierwotną liczbę | **1 test** |
| `acquire(4)` podzielone na dwa wywołania `acquire(2)` | **nic w powtarzalny sposób**, zob. niżej |

Złapano jedenaście z czternastu. Ciekawe są pozostałe trzy wiersze i ten jeden powolny.

Te liczby zebrano, gdy pakiet miał jeszcze klasy z przykładami do uruchamiania, które potem włączono do
zestawów testów wyżej; mutacje i łapiące je testy się nie zmieniły.

**`signalAll` → `signal` nie łapie nic**, w żadnej kolumnie. Wszyscy czekający to wątki ponad udziałem
czekające na ten sam warunek, więc `signal()` wygląda na wystarczające i przechodzi każdy test tutaj;
jest błędne tylko w wąskim przypadku, gdy `tryAcquire` z limitem czasu dostaje sygnał akurat w chwili
poddania się i połyka pobudkę. Poprawność tej linii opiera się na argumencie, a nie na dowodzie z
testów, i warto to powiedzieć wprost, zamiast zostawiać czytelnika w przekonaniu, że testy to pokrywają.

**Flaga `fairQueueing` nie jest sprawdzona.** Żaden test nie sprawdza kolejności, więc zastąpienie flagi
sztywnym `false` niczego obserwowalnego nie zmienia. To wynika z projektu (gwarancja nie zależy od
kolejności w kolejce), ale znaczy, że flaga to dokumentacja, a nie przetestowana funkcja.

**`requireCapacity` to czujnik, a nie zachowanie.** Dopóki algorytm jest poprawny, nigdy nie zadziała,
więc jego usunięcie jest niewidoczne; tak _powinna_ wyglądać asercja w czasie działania. Jego wartość
widać w połączeniu: z usuniętą regułą 2 _i_ usuniętym czujnikiem 11 testów nadal pada. Sprawia, że inne
błędy są głośniejsze, a zestaw testów od niego nie zależy.

**Zgubiona pobudka jest łapana, ale powoli, i do złapania jej w ogóle potrzebna była poprawka.**
Usunięcie sygnału przy zwolnieniu zawiesza przykłady całkowicie. Zestaw JUnit pada na tym po 214 s, z
pięcioma testami zgłaszającymi błąd, każdy po odczekaniu własnego limitu czasu: „zezwolenia, które nigdy
nie przychodzi”, nie da się wykryć szybciej, niż jest się gotowym na nie czekać, a hojne limity to
właściwy kompromis wobec niestabilności testów.

Dojście do tego wymagało jednej zmiany. W pierwszej wersji tych testów przebieg w ogóle się nie kończył
(wciąż trwał, gdy go zabito po 900 s), bo `theGateHoldsUpUnderManyVirtualThreads` się zawiesił. Powód
warto znać niezależnie od tego repozytorium: **mappera, który nigdy nie wraca, nie da się porzucić w
operacji końcowej, która nie przerywa.** `forEach` i `toList` muszą skonsumować każdy element, więc
czekają na zaparkowany mapper w nieskończoność, a domyślne `@Timeout` z JUnit nie ratuje testu: przerywa
wątek _testu_, który sam czeka w operacji końcowej, a nie wątki wirtualne zaparkowane w bramie.
Oznaczenie tych testów jako `@Timeout(threadMode = SEPARATE_THREAD)` pozwala zgłosić przekroczenie
czasu bez czekania na potok, co zamienia zawieszony fork w błąd po 62 s. Wątki testów są demonami z
tego samego powodu: zaparkowany wątek roboczy nie może przeżyć buildu.

**Podzielenie `acquire(4)` na dwa wywołania `acquire(2)` nie jest łapane w powtarzalny sposób** i to
jest lekcja, a nie luka. Zakleszczenie zdarza się tylko wtedy, gdy dwaj wywołujący się przeplotą;
zostawieni przypadkowi zwykle tego nie robią, test przechodzi, a błąd czeka na produkcję. Przypadek
deterministyczny wymusza przeplot zatrzaskiem, a to jedyny sposób, żeby celowo przetestować wyścig.

Jedna wcześniejsza wersja tych testów zamieniała tę mutację w _awarię z braku pamięci_ zamiast błędu
testu: funkcja pomocnicza opróżniająca zezwolenia strony kręciła się, dopóki nie dostała odmowy, co
nigdy nie następuje, gdy brama wpuszcza wszystko. Teraz sprawdza limit w każdym obiegu. Funkcja
pomocnicza w teście, która nie może się zakończyć na zepsutej implementacji, jest gorsza niż jej brak:
zamienia wyraźny błąd w martwy fork.

## Uruchomienie

```
mvn -q compile     # wymaga JDK 25 (pom celuje w release 25)
mvn test           # 150 testów, ~6 s, z tego 70 w test/java/jdk
```

Nie ma tu nic więcej do uruchamiania: ten pakiet trzyma implementację w `src/java/jdk`, a wszystko, co
da się pokazać, w `test/java/jdk`, więc zestaw testów jest zarazem dowodem dla dokumentacji i siatką
przeciw regresjom. Szczyty, liczby, kolejności i niezmienniki są sprawdzane asercjami; podane wyżej
czasy zegarowe i przepustowości są orientacyjne i zauważalnie zmieniły się między JDK 17 a 25, podczas
gdy każdy niezmiennik został na miejscu.
