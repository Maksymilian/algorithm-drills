# Trzy blokady: `ReentrantLock`, `StampedLock` i jedna zbudowana z `VarHandle`

Notatka do [`src/java/jdk/VarHandleLock.java`](../../src/java/jdk/VarHandleLock.java) i
[`src/java/jdk/VarHandleVector.java`](../../src/java/jdk/VarHandleVector.java) oraz do czterech
zestawów testów obok: [`ReentrantLockTest`](../../test/java/jdk/ReentrantLockTest.java),
[`StampedLockTest`](../../test/java/jdk/StampedLockTest.java),
[`VarHandleLockTest`](../../test/java/jdk/VarHandleLockTest.java) i
[`VarHandleVectorTest`](../../test/java/jdk/VarHandleVectorTest.java).

Uzupełnia [notatkę o `mapConcurrent`](map-concurrent-and-equal-share-semaphore.md), która omawia
`Semaphore`; kontrasty z nim są zaznaczone tam, gdzie się pojawiają.

Każdą liczbę tutaj zmierzono na JDK 25.0.1 (Temurin), 24 procesory, Intel i7-14650HX, albo tymi
testami, albo doraźnym benchmarkiem, którego **nie ma w repozytorium**: zgodnie z konwencją tego
pakietu przepustowość nie ma solidnej asercji, więc jest mierzona tutaj i podawana jako orientacyjna.
Liczby z benchmarku są oznaczone; wszystko inne sprawdza test. Wszystkie zakresy to rozrzut z
powtórzonych przebiegów, nigdy pojedynczy odczyt.

**Jedno pytanie.** Wszystkie trzy wykluczają piszących. Różni je to, co musi zrobić _czytelnik_:

| | Czytelnik musi… | Odczyty/s, 8 wątków |
|---|---|---|
| `ReentrantLock` | zająć blokadę, wykluczając każdego innego czytelnika | ~40 M |
| `ReentrantReadWriteLock` | zająć blokadę współdzieloną i zapłacić za księgowość | ~7–9 M |
| `StampedLock` optymistycznie | **nie zajmować niczego**, a potem sprawdzić | ~3 100 M |

Ostatni wiersz to nie lepsza blokada. To jej brak, a reszta tej notatki dotyczy tego, ile to kosztuje.

---

## Część 1: `ReentrantLock`, czyli czym nie jest zezwolenie

Wszystko to sprawdza `ReentrantLockTest`. Prawa kolumna pokazuje, co zamiast tego robi `Semaphore`, bo
po obie klasy często sięga się zamiennie, a nie zgadzają się prawie w niczym.

| Zachowanie | `ReentrantLock` | `Semaphore` |
|---|---|---|
| Dwukrotne zajęcie przez jeden wątek | wchodzi ponownie; liczba zajęć 2 | bierze **dwa zezwolenia** i może zakleszczyć się sam ze sobą |
| Zwolnienie bez trzymania | `IllegalMonitorStateException` | **tworzy zezwolenie**: pula po cichu rośnie |
| Zwolnienie z innego wątku | `IllegalMonitorStateException` | dozwolone i idiomatyczne |
| `getHoldCount()` | dla _bieżącego wątku_; 0, gdy trzyma inny wątek | nie ma takiego pojęcia |
| Warunki | `newCondition()`, ile chcesz | brak |

### `lock()` nie da się przerwać, ale przerwania nie zapomina

Wątek zaparkowany w `lock()` zostaje zaparkowany po przerwaniu: zmierzone przez przerwanie go,
obserwowanie, jak przez 200 ms pozostaje w `hasQueuedThread`, i dopiero potem zwolnienie blokady.
Czego _nie_ robi, to wyrzucenie przerwania: po zajęciu blokady ustawia flagę z powrotem, więc następne
blokujące wywołanie ją zobaczy. `lockInterruptibly()` to metoda, która się poddaje, a gdy to robi,
zostawia kolejkę czystą: `getQueueLength()` z powrotem na 0, właściciel niezakłócony.

Ta para to cały powód istnienia obu metod. `lock()` jest dla sekcji krytycznych, których nie wolno
porzucić w połowie; `lockInterruptibly()` dla wszystkiego, co wywołujący może zostać poproszony anulować.

### `Condition.await()` zwalnia _wszystkie_ zajęcia i zajmuje ponownie, zanim rzuci wyjątek

Dwa zachowania, które zaskakują, oba sprawdzone:

1. Wątek, który zajął blokadę dwa razy, a potem czeka, zwalnia **oba** zajęcia. Test to udowadnia,
   zajmując blokadę z zewnątrz, gdy czekający jest zaparkowany, i po sygnale znowu sprawdza
   `getHoldCount() == 2`. `Condition` zwalniający tylko jedno zajęcie zakleszczyłby się natychmiast, a
   nic w API nie sugeruje, że zwalnia wszystkie.
2. `await()` zawsze wraca z zajętą blokadą, _także wtedy, gdy rzuca `InterruptedException`_. Test
   trzyma blokadę, przerywa czekającego, obserwuje, jak przez 200 ms zostaje zaparkowany, i widzi
   wyjątek dopiero po zwolnieniu blokady. Przerwanie nie może więc wyciągnąć wątku z sekcji
   krytycznej; może tylko sprawić, że wyjdzie drzwiami frontowymi.

`awaitUninterruptibly()` to trzeci przypadek: całkowicie ignoruje przerwanie i oddaje flagę przy
powrocie.

`Condition` istnieje zamiast `wait`/`notify` dlatego, że daje kilka zbiorów czekających zamiast jednego.
Monitor ma dokładnie jeden, więc w ograniczonym buforze czekający na „nie pełny” i „nie pusty” dzielą go,
a każde `put` budzi też każde zablokowane `put`. Dwa warunki budzą dokładnie te wątki, które mogą
posunąć się dalej; po to jest `BoundedBuffer` w teście, sprawdzany przez zsumowanie 2 000 elementów
przepuszczonych przez bufor o pojemności 4.

### Sprawiedliwość dotyczy kolejności i kosztuje dwa rzędy wielkości

Najpierw deterministycznie: przy pięciu wątkach stających w kolejce **po jednym** (każdy sprawdzony w
`hasQueuedThread`, zanim wystartuje następny) sprawiedliwa blokada przydziela dokładnie w kolejności
przybycia. Niesprawiedliwa też obsłuży wszystkie pięć: wpychanie dotyczy _nowo przybyłych_, a nie
mieszania kolejki, która już się ustawiła.

Niesprawiedliwość widać przy przekazaniu. Osiem wątków w ciasnej pętli zajmij/zwolnij przez 300 ms,
uruchomionych z zatrzasku, żeby okno pomiaru było identyczne:

| | Zajęcia | Przekazanie samemu sobie |
|---|---:|---:|
| `ReentrantLock()` (wpychanie) | 3.2–4.1 M | **97.9–98.4 %** |
| `ReentrantLock(true)` (sprawiedliwa) | 101–107 K | **0.00–1.28 %** |

Przekazanie samemu sobie to odsetek zajęć, w których blokada wróciła prosto do wątku, który ją właśnie
zwolnił. Przy 98% niesprawiedliwa blokada prawie nie jest współdzielona: jeden wątek wykonuje prywatną
sekcję krytyczną, a siedem czeka, bo zwalniający wątek wciąż trzyma linię pamięci podręcznej i wygrywa
ponowne zajęcie, zanim zaparkowany wątek w ogóle zostanie zaplanowany. Kolejność usuwa to całkowicie i
kosztuje tutaj **30–40 razy**.

> **Jak tego nie mierzyć.** Liczby zajęć na wątek w stałym oknie czasu są zdominowane przez rozrzut
> startu i oceniają _sprawiedliwą_ blokadę jako mniej sprawiedliwą. Ta sama pułapka jest szczegółowo
> opisana w notatce o `Semaphore`; wpychanie to pytanie o przekazanie, więc liczyć trzeba właśnie
> przekazanie.

### `synchronized` wobec `ReentrantLock` na JDK 25

Zwykła rada („na wątkach wirtualnych używaj `ReentrantLock`, bo `synchronized` przypina wątek nośny”)
**jest nieaktualna na JDK, na który celuje ten projekt.** Zmierzone wprost, z jednym wątkiem nośnym
(`-Djdk.virtualThreadScheduler.parallelism=1 -Djdk.virtualThreadScheduler.maxPoolSize=1`) i wątkiem
wirtualnym blokującym w środku każdej konstrukcji:

| | JDK 21.0.8 | JDK 25.0.1 |
|---|---|---|
| blokada w środku `synchronized` | **przypięty**: drugi wątek wirtualny nigdy nie ruszył (3 s) | nieprzypięty |
| blokada w środku `ReentrantLock` | nieprzypięty | nieprzypięty |

To JEP 491, dostarczony w JDK 24. _(Notatka o `mapConcurrent` podaje przypinanie wątku nośnego jako
powód, by woleć `ReentrantLock`; na JDK 25 ten powód już nie obowiązuje, choć pozostałe powody z tamtej
notatki, czyli warunki i zajmowanie z limitem czasu, tak.)_

Poza przypinaniem obie nie są zamienne przy rywalizacji (doraźny benchmark, maleńka sekcja krytyczna):

| Wątki | `synchronized` | `ReentrantLock` |
|---:|---:|---:|
| 1 | 74–270 M operacji/s | 85–90 M operacji/s |
| 8 | 15.7–16.9 M | **43.2–44.4 M** |
| 24 | 16.5–28.8 M | **39.3–41.2 M** |

Bez rywalizacji `synchronized` jest szybszy z dwóch i skrajnie zmienny: odczyty 74 M i 270 M pochodzą z
tego samego przebiegu. Coś, co JIT robi z monitorem bez rywalizacji, przestaje działać, gdy pojawia się
drugi wątek; dokładnej optymalizacji nie zbadano, więc uczciwe podsumowanie to sam rozrzut. Przy
rywalizacji `ReentrantLock` jest 2.5 razy szybszy. Żadna z tych liczb nie jest powodem do przepisywania
działającego kodu; obie są powodem, żeby nie zakładać, że `synchronized` to „ten tani”.

---

## Część 2: `StampedLock`, czyli tryb, który nie jest blokadą

`tryOptimisticRead()` nie zajmuje **niczego**. Zwraca numer wersji; czytasz, co chcesz; `validate(stamp)`
po fakcie odpowiada na jedno pytanie: _czy w trakcie mojego czytania wszedł piszący?_

Najprostszy dowód, że niczego nie trzyma, nie wymaga w ogóle drugiego wątku i jest pierwszym testem w
`StampedLockTest`:

```java
long stamp = lock.tryOptimisticRead();
int x = point.x;                  // czyta 1

long write = lock.writeLock();    // _ten sam_ wątek bierze blokadę zapisu. Bez czekania: niczego nie trzyma
try {
    point.moveTo(50);
} finally {
    lock.unlockWrite(write);
}

int y = point.y;                  // czyta 100, już z nowego stanu
assertNotEquals(2 * x, y);        // rozdarte, wbrew niezmiennikowi, który pod blokadą zawsze zachodził
assertFalse(lock.validate(stamp));
```

Czytelnik naprawdę może zobaczyć śmieci, a cały kontrakt polega na tym, że dowiaduje się o tym po
fakcie. Idiom to więc trzy kroki w kolejności i każdy jest kluczowy:

```java
long stamp = lock.tryOptimisticRead();
int x = point.x;
int y = point.y;                  // 1. skopiuj do zmiennych LOKALNYCH: pola mogą się zmienić
if (!lock.validate(stamp)) {      // 2. sprawdzaj PO odczytach, nigdy przed
    stamp = lock.readLock();      // 3. wróć do prawdziwej blokady; nie kręć się na ścieżce optymistycznej
    try {
        x = point.x;
        y = point.y;
    } finally {
        lock.unlockRead(stamp);
    }
}
return x + y;                     // używane są tylko zmienne lokalne
```

Sprawdzanie przed odczytami niczego nie sprawdza. Używanie _pól_ po sprawdzeniu też niczego nie
sprawdza, bo mogą się zmienić między sprawdzeniem a użyciem. Sedno to kopia.

### Jak często naprawdę trzeba ponawiać?

Czytelnicy stosujący ten idiom przeciw jednemu piszącemu, który nigdy nie robi przerwy, 300 ms
(sprawdzone: nigdy nie przyjęto rozdartej pary; częstości są mierzone):

| Czytelnicy | Próby optymistyczne | Nieudane sprawdzenia | Przyjęte rozdarte pary |
|---:|---:|---:|---:|
| 1 | 9.6–22.4 M | 3.1–7.8 % | **0** |
| 8 | 546–757 M | 0.05–0.06 % | **0** |
| 24 | 3.67–3.72 G | ~0.00 % | **0** |

Odsetek niepowodzeń _maleje_ z dodawaniem czytelników, co wygląda na odwrót, dopóki nie zauważy się,
kto jest głodzony: 24 kręcących się czytelników prawie nie daje jedynemu piszącemu okazji do zajęcia
blokady, więc jest mniej zapisów, z którymi można przegrać wyścig. Odsetek to własność cyklu pracy
piszącego, a nie stała blokady, i dlatego jest podany tutaj, a w teście tylko ograniczony.

### Ile to kosztuje

`StampedLock` to worek ostrych krawędzi, a testy przypinają każdą z nich:

| | Zachowanie |
|---|---|
| Wielowejściowość | **brak**. Drugie `writeLock()` od trzymającego zakleszcza go z samym sobą; pokazane na demonie z `writeLockInterruptibly()`, żeby impas dało się zaobserwować, a potem z niego wyjść |
| Warunki | **brak**. `asReadLock().newCondition()` rzuca `UnsupportedOperationException` |
| Przerwania | `readLock()`/`writeLock()` ignorują przerwania; reagują tylko formy `…Interruptibly` |
| Własność | brak: nic nie śledzi, kto co trzyma; jest tylko znaczek |
| Podniesienie | `tryConvertToWriteLock` udaje się tylko jedynemu czytelnikowi; przy niepowodzeniu **wciąż trzymasz blokadę odczytu** i musisz ją zwolnić pierwotnym znaczkiem |
| Obniżenie | `tryConvertToOptimisticRead` zwalnia blokadę i oddaje znaczek obserwacji |
| Czytelnicy | nie unieważniają znaczka; _pusta_ sekcja zapisu tak: `validate` śledzi zajęcia, a nie zmiany |

Wiersz z nieudanym podniesieniem to ten, który gryzie na produkcji. `tryConvertToWriteLock` zwracające
`0` brzmi jak „nic się nie stało”, a naprawdę znaczy „podniesienie się nie udało **i** wciąż jesteś
czytelnikiem”. Porzucenie tam znaczka to wyciek blokady odczytu, czyli zakleszczenie później i gdzie
indziej.

### Znaczek odczytu to numer wersji, a nie poświadczenie

Zbadane, a nie założone, i potem sprawdzone asercjami, żeby zmiana została zauważona:

| | Wynik |
|---|---|
| `unlockRead(stamp + 1)` … `+ 126` | **udaje się** i zwalnia blokadę |
| `unlockRead(stamp + 127)`, `+ 128`, `- 1` | `IllegalMonitorStateException` |
| `unlockRead(stampFromAnotherStampedLock)` | **udaje się**, gdy obie blokady są w tym samym stanie |
| `unlockWrite(writeStamp + 1)` | `IllegalMonitorStateException` |
| `unlockRead(writeStamp)`, `unlockWrite(readStamp)` | `IllegalMonitorStateException` |

`unlockRead` sprawdza bity wersji powyżej 7-bitowego licznika czytelników i to, że _jakiś_ czytelnik
jest policzony, a nie to, że ten znaczek jest tym, który dostałeś. Dwie świeże blokady, każda z jednym
czytelnikiem, mają identyczne znaczki, więc jedna zwolni drugą. Znaczek **zapisu** jest sprawdzany
dokładnie, bo bit zapisu leży wewnątrz sprawdzanego obszaru.

Nic z tego nie jest wyspecyfikowane i na niczym z tego nie należy polegać. Praktyczny wniosek: znaczek
złapie część twoich błędów na ścieżce zapisu i prawie żadnego na ścieżce odczytu, więc `StampedLock`
dużo bardziej niż `ReentrantLock` potrzebuje `unlockRead` w `finally` tuż obok `readLock`.

---

## Część 3: wybór między nimi

Doraźny benchmark, czytelnicy biorą spójną parę dwóch pól, M odczytów/s, zakres z przebiegów:

**Bez żadnego piszącego**

| Czytelnicy | `synchronized` | `ReentrantLock` | `ReentrantRWLock` | `StampedLock.readLock` | optymistycznie |
|---:|---:|---:|---:|---:|---:|
| 1 | 73 | 78 | 67–70 | 72–73 | **456–462** |
| 8 | 4.6–5.0 | 39.9–41.6 | 7.0–8.8 | 16.9–19.1 | **3 045–3 147** |
| 24 | 4.5–4.9 | 34.7–36.5 | 6.4–7.4 | 10.9–12.3 | **4 323–4 436** |

**Jeden piszący na pełnych obrotach**

| Czytelnicy | `synchronized` | `ReentrantLock` | `ReentrantRWLock` | `StampedLock.readLock` | optymistycznie |
|---:|---:|---:|---:|---:|---:|
| 1 | 13.2–13.7 | 6.0–7.6 | 1.4–3.2 | 6.6–7.7 | **35–53** |
| 8 | 3.9–4.6 | 36.9–38.8 | 6.6–8.6 | 10.6–11.8 | **791–1 044** |
| 24 | 4.5 | 33.5–35.0 | 6.0–6.9 | 11.6–14.8 | **3 940–4 108** |

Wynikają z tego trzy rzeczy, a druga jest warta zapamiętania:

1. **Odczyt optymistyczny jest 100–400 razy szybszy niż blokada odczytu**, bo nie jest blokadą: żadna
   linia pamięci podręcznej nie jest zapisywana, więc ośmiu czytelników w ogóle ze sobą nie walczy.
   Stąd skalowanie ponadliniowe: 456 M przy jednym czytelniku, 3 100 M przy ośmiu.

2. **`ReentrantReadWriteLock` jest tu 4–5 razy _wolniejsza_ niż zwykła, wyłączna `ReentrantLock`**
   (7–9 M wobec 40 M przy 8 wątkach). Blokada odczytu/zapisu to nie darmowa współbieżność: każdy
   czytelnik i tak zapisuje wspólny licznik, żeby się zgłosić, więc czytelnicy walczą ze sobą dokładnie
   o tę linię pamięci podręcznej, której mieli unikać. Opłaca się dopiero, gdy sekcja odczytu jest na
   tyle długa, że kupiona równoległość przeważa nad tą księgowością, a sekcja krytyczna tutaj to dwa
   odczyty pól, dużo poniżej tej granicy. Sięganie po blokadę odczytu/zapisu, bo „odczyty dominują”, bez
   mierzenia, to sposób, żeby kod był naraz wolniejszy i bardziej skomplikowany.

3. **`StampedLock.readLock` też jest wolniejsza niż wyłączna `ReentrantLock`** przy 8+ czytelnikach, z
   tego samego powodu. Jeśli bierzesz `StampedLock` i używasz jej blokady odczytu, zapłaciłeś za
   złożoność i nic nie kupiłeś. Ścieżka optymistyczna to cały powód, żeby jej używać.

Blokowanie wyłączne, dla kompletności (doraźny benchmark, M operacji/s):

| Wątki | `synchronized` | `ReentrantLock` | `ReentrantLock(fair)` | `StampedLock.writeLock` | `VarHandleLock` | `VarHandleLock(fair)` |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 74–270 | 85–90 | 82–88 | 79–81 | 64–66 | 63–65 |
| 8 | 15.7–16.9 | 43.2–44.4 | 0.36–0.44 | **45.5–47.4** | 9.2–9.8 | 0.37–0.40 |
| 24 | 16.5–28.8 | 39.3–41.2 | 0.29–0.33 | **39.9–41.9** | 7.7–7.9 | 0.30–0.33 |

Blokada zapisu `StampedLock` to najszybsza blokada wyłączna w zestawie: nie ma właściciela ani licznika
zajęć do utrzymania. To ten sam brak księgowości, który czyni ją niewielowejściową.

**Procedura decyzji, po kolei.** Używaj `synchronized`, dopóki to nie jest zmierzony problem. Potem
`ReentrantLock`: dla limitu czasu, zajęcia z przerwaniem, warunku albo rywalizacji. Po `StampedLock`
sięgaj tylko wtedy, gdy odczyty dominują, sekcja odczytu jest krótka _i_ jesteś gotów napisać idiom
sprawdź-i-ponów; inaczej to wolniejsza `ReentrantLock` z ostrymi krawędziami. A blokada odczytu/zapisu
potrzebuje sekcji odczytu na tyle długiej, żeby zapłaciła za własną księgowość: mierz przed, nie po.

---

## Część 4: `VarHandleLock`, czyli co naprawdę jest w blokadzie

`src/java/jdk/VarHandleLock.java` to wielowejściowa, wyłączna `Lock` w około 120 liniach logiki: dwa
`VarHandle`, `ConcurrentLinkedQueue` i `LockSupport`. Istnieje, żeby odpowiedź na pytanie „co jest w
blokadzie?” była konkretna, a odpowiedź ma trzy części.

**1. Jedno atomowe słowo stanu.** `state` to liczba zajęć; `0` znaczy wolna; udane
`compareAndSet(0, 1)` _jest_ zajęciem. Blokada bez rywalizacji to jeden CAS i jeden zapis, dlatego
kolumna bez rywalizacji wyżej jest tak płaska dla każdej implementacji.

**2. Właściciel, żeby dało się liczyć zajęcia.** To cała wielowejściowość i cały powód, dla którego
`unlock` może odrzucić wątek, który nigdy nie zajął. To jedno pole odróżnia blokadę od zezwolenia
semafora.

**3. Kolejka i protokół parkowania** na wypadek, gdy CAS się nie uda. Tu mieszka wszystko, co trudne.

### Tryby uporządkowania pamięci, czyli powód, żeby w ogóle używać `VarHandle`

Pole `volatile` daje każdemu dostępowi to samo, najsilniejsze uporządkowanie. `VarHandle` czyni
uporządkowanie własnością _dostępu_, więc każda linia może powiedzieć, czego potrzebuje:

| Linia | Tryb | Dlaczego |
|---|---|---|
| `STATE.compareAndSet(this, 0, 1)` | CAS | zajęcie; pełna bariera, więc zapisy poprzedniego właściciela są widoczne |
| `STATE.set(this, n)` (ponowne wejście) | **zwykły** | należy do właściciela, dopóki zajęta: żaden inny wątek nie może go czytać, więc uporządkowanie nic nie daje |
| `STATE.setVolatile(this, 0)` | zapis volatile | zwolnienie; ten jeden zapis publikuje całą sekcję krytyczną |

Zwykły zapis to sedno ćwiczenia, a nie mikrooptymalizacja: jest poprawny tylko dzięki zapisanemu
niezmiennikowi, a zapisanie trybu czyni niezmiennik widocznym. Tyle kosztuje `volatile`: nie potrafi
wyrazić „tu zwykły, tam zwalniający”.

### Zgubione pobudki i argument o kolejności, który im zapobiega

Niebezpieczny przeplot: wątek przegrywa CAS, a właściciel zwalnia blokadę i budzi kolejkę, _zanim_ ten
wątek do niej trafi. Wątek parkuje wtedy i nikt go nie obudzi.

Ochroną jest kolejność, nie szczęście. `acquireQueued` **najpierw staje w kolejce, a potem sprawdza
ponownie**, więc zwolnienie, które przegapiło wejście do kolejki, łapie ponowne sprawdzenie, a
zwolnienie, które je widziało, budzi. Trzeciego przypadku nie ma. Resztę daje `LockSupport`: jego
zezwolenie się zachowuje, więc `unpark` przed `park` nie ginie, tylko sprawia, że `park` od razu wraca.

Ten argument wyjaśnia kształt głównego testu `VarHandleLockTest`. Miesza wszystkie cztery tryby
zajmowania z działającym wątkiem przerywającym, bo ścieżki **anulowania** to miejsca, gdzie chowa się
zgubiona pobudka: wątek, który porzuca czekanie, może nieść sygnał przeznaczony dla kogoś innego, więc
musi zarówno wyjść z kolejki, _jak i_ obudzić nową głowę. Test sprawdza też wprost „każdy wątek roboczy
skończył”, zamiast wnioskować to z sum: na zawsze zaparkowany wątek nie dokłada nic do żadnej strony
licznika, więc sprawdzenie samych sum przeszłoby przez zawieszenie.

### Ile to kosztuje

Z tabeli wyżej: poprawna i **4–5 razy wolniejsza niż `ReentrantLock` przy rywalizacji** (9.2–9.8 M wobec
43.2–44.4 M przy 8 wątkach), 1.4 razy wolniejsza bez rywalizacji. AQS zarabia na to ścieżką kręcenia
się przed parkowaniem, sprytniejszą kolejką wbudowaną w węzły i anulowaniem, które nie przechodzi po
`ConcurrentLinkedQueue`. Wariant sprawiedliwy ląduje w granicach szumu od `ReentrantLock(true)`
(0.37–0.40 M wobec 0.36–0.44 M), bo wtedy obie płacą za parkowanie i budzenie przy każdym przekazaniu i
nic innego się nie liczy.

`newCondition()` rzuca wyjątek. Poprawny `Condition` wymaga drugiej kolejki i protokołu przenoszenia
między nimi, co jest większym ćwiczeniem, a `StampedLock` odmawia z tego samego powodu.

---

## Część 5: obliczenia wektorowe z `VarHandle`

`VarHandle` daje dwie rzeczy, które wyglądają jak wektory. Żadna nie jest SIMD, a różnica między nimi
ma znaczenie.

### Osiem pasów na dostęp do pamięci (SWAR)

`MethodHandles.byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN)` czyta `byte[]` jako `long`i. Jeden
odczyt wciąga **osiem pasów bajtowych do jednego rejestru**, a zwykła arytmetyka całkowita działa na
wszystkich ośmiu naraz: SIMD Within A Register. Jedyna zasada: **pas nie może przenieść cyfry do
sąsiada**, a każda maska w `VarHandleVector` jest po to, żeby to wymusić.

Szczegół wart podkradnięcia to maska zerowych pasów. Słynna jednolinijkowa wersja

```java
(v - 0x0101010101010101L) & ~v & 0x8080808080808080L
```

jest dokładna dla pytania _„czy `v` ma zerowy pas”_ i **błędna dla ich liczenia**: zerowy pas pożycza od
sąsiada i też go zaznacza. Dla `v = 0xFFFFFFFFFFFF0100` zgłasza dwa zerowe pasy tam, gdzie jest jeden.
Wersja, która nie może pożyczyć, bo `(lane & 0x7F) + 0x7F` nigdy nie przekracza `0xFE`:

```java
~((((v & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL) | v) | 0x7F7F7F7F7F7F7F7FL)
```

Zmierzone na 1 MiB, jednowątkowo (doraźny benchmark). Prawa para powtarza przebieg z wyłączonym
autowektoryzatorem C2 (`-XX:-UseSuperWord`), co zamienia wyjaśnienie niżej ze zgadywania w pomiar:

| Operacja | Skalarnie | SWAR | | Skalarnie, bez SuperWord | SWAR, bez SuperWord |
|---|---:|---:|---|---:|---:|
| `count` wartości bajtu | 5.7–6.5 | **18.4–23.1** | 3.5× | 6.4 _(bez zmian)_ | 14.3–15.7 |
| `indexOf`, pełne przejście | 7.6–8.2 | **14.9–19.5** | 2.2× | 8.2 _(bez zmian)_ | 16.0–19.3 |
| `add` pas po pasie | **5.9–8.9** | 4.2–5.3 | **0.6×** | **3.7–3.9** | **4.6–5.2** |

Wszystko w GiB/s. Trzy rzeczy są teraz zmierzone, a nie założone:

1. **Wiersz `add` odwraca się po wyłączeniu autowektoryzacji**: wersja skalarna spada o połowę, z
   7.4–8.0 do 3.7–3.9, a wtedy wygrywa SWAR. SWAR nie konkuruje więc z kodem skalarnym, tylko z
   **autowektoryzatorem C2**, który zamienia naiwną pętlę `out[i] = (byte)(a[i] + b[i])` w prawdziwe
   SIMD, szersze niż 8 pasów i bez maskowania. Ręczny SWAR blokuje optymalizację, którą naśladował, i
   z nią przegrywa.

2. **Skalarna pętla `count` w ogóle nie jest autowektoryzowana**: 6.4 GiB/s z SuperWord i bez. Zatrzymuje
   ją rozgałęzienie zależne od danych i właśnie dlatego SWAR tam wygrywa: zastępuje rozgałęzienie
   arytmetyką.

3. **Pętla SWAR `count` sama jest autowektoryzowana**: 22.8 spada do 14.3 bez SuperWord. 3.5 razy SWAR to
   więc współpraca, a nie zastępstwo: ręczne maskowanie usuwa rozgałęzienie, a C2 poszerza powstałą
   pętlę bez rozgałęzień. Warto to wiedzieć, zanim napisze się SWAR „bo JIT tego nie zwektoryzuje”:
   czasem zwektoryzuje, gdy usunie się to, co go zatrzymywało.

SWAR zasługuje więc na miejsce przy kształtach, do których autowektoryzator sam nie dojdzie: szukanie,
dopasowywanie, liczenie, wszystko z rozgałęzieniem zależnym od danych. Dla prostej arytmetyki to
pogorszenie. Mierz w tej kolejności: najpierw zwykła pętla, SWAR tylko wtedy, gdy zwykła pętla
przegrywa, i sprawdź `-XX:-UseSuperWord`, z którym z dwóch naprawdę konkurujesz.

### Jeden element naraz, atomowo

`MethodHandles.arrayElementVarHandle` daje każdemu elementowi zwykłej tablicy pełny zestaw trybów
dostępu, bez opakowania `AtomicLongArray`. Zasady trybów dostępu, zbadane, a potem sprawdzone:

| | |
|---|---|
| widok tablicy bajtów | **tylko `get`/`set`**: każdy tryb atomowy rzuca `UnsupportedOperationException` |
| widok ByteBuffer, bufor na stercie | `IllegalStateException: Atomic access not supported for heap buffer` |
| widok ByteBuffer, bufor bezpośredni | tryby atomowe działają i wymagają wyrównania |
| `getAndAdd` na `double[]`/`float[]` | **obsługiwane**: tryby numeryczne nie są tylko dla typów całkowitych |
| `getAndBitwiseOr` na `double[]` | `UnsupportedOperationException`: tryby _bitowe_ są |
| `compareAndSet` na `double[]` | porównuje **surowe bity**: `-0.0` nie pasuje do `0.0`; `NaN` pasuje do siebie |

Dwa z nich warto powiedzieć wprost. To, że widok tablicy bajtów ma tylko zwykły dostęp, jest zaletą
dla SWAR: odczyty są z założenia niewyrównane, a tylko zwykły dostęp to toleruje. A porównanie bitowe
to czekający błąd _żywotności_: pętla CAS, której wartość oczekiwana pochodzi skądkolwiek poza odczytem
tego elementu, może kręcić się w nieskończoność wobec wartości, którą `==` uznaje za równą. To ta sama
asymetria `-0.0`/`NaN`, którą `ArrayComparisonTest` opisuje dla `Arrays.equals`, tylko objawiająca się
zawieszeniem zamiast złej odpowiedzi.

Nie ma _żadnego_ trybu dostępu dla czegoś, na co sprzęt nie ma instrukcji, na przykład maksimum.
`maxInto` to pętla ponawiania CAS, która wypełnia tę lukę, i używa `Double.compare` zamiast `>`, żeby
wynik zgadzał się z `Math.max` dla `NaN` i `-0.0`.

### Atomowość pasa to nie atomowość wektora

To jest pułapka. Czterech piszących dodaje tę samą wartość do wszystkich 256 pasów, jeden czytelnik
sprawdza, czy widziane pasy zgadzają się ze sobą, 300 ms:

| | Odczyty | Czytelnik widział częściową zmianę |
|---|---:|---:|
| `getAndAdd` na pas (bez blokad) | 558–682 K | **97.8–99.6 %** |
| cały wektor pod jedną blokadą | 159–190 K | **0** |

Atomowość pasa znaczy, że żadna _zmiana_ nie ginie. Nie mówi nic o tym, co widzi czytelnik, bo żadne
dwa pasy nie zmieniają się razem, więc czytelnik prawie za każdym razem obserwuje wektor, którego żaden
wątek nigdy nie zapisał. Dla worka niezależnych liczników to w porządku. Dla czegokolwiek z
niezmiennikiem między pasami to cichy błąd, niewidoczny w testach, bo każdy pas z osobna jest idealny.

A wcale nie jest szybciej (wektor 256 pasów, 8 wątków, zmiany wektora na sekundę):

| | |
|---|---:|
| `getAndAdd` na pas | 2.19–2.55 M |
| cały wektor pod `VarHandleLock` | 5.83–6.22 M |
| cały wektor pod `ReentrantLock` | **8.70–14.53 M** |

256 operacji CAS z rywalizacją kosztuje więcej niż jedno zajęcie blokady i 256 zwykłych zapisów, i to
4–6 razy. „Bez blokad jest szybciej” to twierdzenie o pojedynczych słowach _bez rywalizacji_, a odwraca
się, gdy tylko jedna logiczna zmiana obejmuje ich wiele.

---

## Czym to nie jest

- **`StampedLock` to nie zamiennik `ReentrantReadWriteLock`.** Brak wielowejściowości, warunków i
  właściciela, a podniesienie może się nie udać, zostawiając cię z blokadą odczytu.
- **Sprawiedliwa blokada to nie sprawiedliwy udział.** Sprawiedliwość porządkuje wątki, które już czekają.
  Nie mówi nic o tym, ile może trzymać jakikolwiek wywołujący; to problem `EqualShareSemaphore` z
  [drugiej notatki](map-concurrent-and-equal-share-semaphore.md).
- **`VarHandleLock` nie jest do użytku.** To rozpisany mechanizm. `ReentrantLock` jest lepiej
  przetestowana, ma warunki i jest 4–5 razy szybsza przy rywalizacji.
- **SWAR to nie SIMD** i, jak zmierzono wyżej, nawet go nie zastępuje, tylko z nim współpracuje. Vector
  API (`jdk.incubator.vector`) to jeszcze co innego: wciąż w inkubatorze i wymaga `--add-modules`, którego
  ten projekt nie używa.
- **Żadna z liczb przepustowości nie jest wynikiem benchmarku.** To jedna maszyna, jedna JVM, jedno
  popołudnie, z maleńkimi sekcjami krytycznymi wybranymi pod maksymalną rywalizację. Niezmienniki są
  sprawdzane asercjami; liczby są orientacyjne.

## Wzorce w testach

`test/java/jdk/`: 94 testy w czterech zestawach, ~4 s:

| Zestaw | Testy | Wzorzec |
|---|---:|---|
| `ReentrantLockTest` | 20 | semantyka JDK; kolejność przydziału sprawiedliwej blokady sprawdzona dokładnie; przekazanie zmierzone |
| `StampedLockTest` | 15 | semantyka JDK; deterministyczny rozdarty odczyt; zero przyjętych rozdartych par pod obciążeniem |
| `VarHandleLockTest` | 12 | nieatomowa sekcja krytyczna (wykluczanie) i każdy wątek roboczy kończący pracę (pobudki) |
| `VarHandleVectorTest` | 47 | SWAR wobec wzorca skalarnego dla **każdej** długości 0–40, nie tylko wielokrotności 8 |

Dwa wybory w tych testach są celowe i warte przeniesienia gdzie indziej:

**Zatrzaski, a nie stopery, wszędzie, gdzie twierdzenie na to pozwala.** „Czekający na pewno jest
zaparkowany” to `while (!lock.hasQueuedThread(t)) Thread.onSpinWait()`, a nie uśpienie. Uśpienia pojawiają
się tylko tam, gdzie asercja naprawdę brzmi „i potem przez 200 ms nic się nie stało”.

**SWAR psuje się na końcówce.** Każda procedura SWAR jest sprawdzana dla długości 0, 1, 2, 7, 8, 9, 15,
16, 17, 23, 31, 33, 40. Zestaw używający tylko wielokrotności 8 testuje szeroką pętlę i nigdy skalarnej
reszty, a błąd zawsze siedzi właśnie tam.

## Uruchomienie

```
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test         # wymaga JDK 25
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest='ReentrantLockTest,StampedLockTest,VarHandleLockTest,VarHandleVectorTest'
```

Tabele przepustowości pochodzą z jednoplikowego programu uruchamianego jako
`java -cp target/classes Bench.java`, celowo trzymanego poza repozytorium: nie ma asercji, więc nie jest
testem, a ten pakiet nie dostarcza programów pomiarowych jako kodu produkcyjnego.
