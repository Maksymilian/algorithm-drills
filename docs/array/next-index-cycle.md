# Cykl w tablicy „indeks następnego”: rho i dlaczego stała pamięć jest też szybsza

Notatka do [`src/java/array/NextIndexCycle.java`](../../src/java/array/NextIndexCycle.java),
testy: [`test/java/array/NextIndexCycleTest.java`](../../test/java/array/NextIndexCycleTest.java).

**Zadanie.** Każdy element `int tab[n]` zawiera indeks następnego elementu. Zaczynając od `start`,
ustal, kiedy przejście zaczyna krążyć w cyklu i jak długi jest ten cykl. Przykład:
`tab = {3,2,1,2,4}`, `start = 0`:

```
krok  0    1    2    3    4    5
      0 -> 3 -> 2 -> 1 -> 2 -> 1 -> ...
                ^---------'
```

Cykl zaczyna się na indeksie 2, po 2 krokach, i ma 2 indeksy.

## Nie ma czego rozstrzygać w pytaniu „czy jest cykl”

`tab` to funkcja `f(i) = tab[i]` z `0 … n-1` w siebie, a przejście to `start, f(start), f²(start), …`.
Dwa zdania ustalają jego kształt raz na zawsze:

- **Musi się powtórzyć.** Wartości jest tylko `n`, więc w `n+1` krokach jakiś indeks pojawi się dwa
  razy.
- **Gdy raz się powtórzy, powtarza się zawsze.** `f` jest _funkcją_ (jeden następnik na indeks),
  więc krok po danym indeksie jest zawsze ten sam. Powrót do indeksu to powrót do całej jego
  przyszłości.

Przejście to więc **ogon `μ` kroków, który nigdy nie wraca, a potem cykl `λ` indeksów, bez końca**:
litera rho narysowana przez samo przejście.

```
   start              wejście
     o--o--o--o--o----->o----->o
                        ^      |        mu = 5 kroków ogona, przejście raz i zostawione
                        |      v        lambda = 4 indeksy cyklu, przechodzone bez końca
                        o<-----o
```

Każde pytanie o przejście to jedna z tych dwóch liczb. Nie ma przypadku acyklicznego do zgłoszenia
ani przeszukiwania do przerwania, a pierwszy indeks osiągnięty dwa razy jest _koniecznie_ wejściem
do cyklu: nic wcześniejszego nie może się powtórzyć, bo z indeksu na ogonie nie ma drogi powrotnej.

**Dwa odczytania „kiedy”.** Do cyklu _wchodzimy_ w kroku `μ = 2`; powtórzenie _staje się widoczne_
w kroku `μ + λ = 4`, gdy przejście drugi raz staje na indeksie 2. `Cycle` niesie `μ` jako
`stepsBefore`, a drugą liczbę daje `firstRepeatStep()`, bo obie są naturalnymi odczytaniami pytania,
a ich pomylenie to najłatwiejszy sposób na pomyłkę o `λ`.

**Skąd bierze się ogon.** [`MinimumSwaps`](minimum-swaps.md) chodzi po cyklach _permutacji_, a
permutacja to dokładnie przypadek bez ogona: każdy indeks ma jednego poprzednika, więc żadne
przejście nie dojdzie do indeksu z dwóch stron, a `μ = 0`, skądkolwiek zaczniemy. Tutaj `f` jest
dowolna (do indeksu 2 w przykładzie wchodzi się i z 3, i z 1), a to zbieganie się dróg to właśnie
ogon.

## Trzy sposoby znalezienia μ i λ

| | Odczyty `tab` | Dodatkowa pamięć |
|---|---|---|
| `findCycle`: znaczniki | `μ + λ` | 4n bajtów |
| `findCycleInConstantSpace`: Floyd | ≤ `5μ + 4λ` | **O(1)** |
| `findCycleByBrent`: Brent | ≤ `4(μ + λ) + 2` | **O(1)** |

Oba ograniczenia sprawdzono wyczerpująco na każdej funkcji na `n ≤ 7` indeksach z każdego startu:
żadnego naruszenia, najgorszy zaobserwowany przypadek to `4.29 (μ+λ)` dla Floyda i `3.40 (μ+λ)`
dla Brenta, oba na najdłuższym ogonie wchodzącym w pętlę własną.

**Znaczniki.** Zapisz w `firstSeen[i]` krok, w którym pierwszy raz stanęliśmy na `i`. Pierwszy
indeks znaleziony już ze znacznikiem to wejście do cyklu; jego znacznik to `μ`, a odległość od
niego do teraz to `λ`. Jedno przejście, najmniej możliwych odczytów i jedyna metoda, której pamięć
rośnie z _tablicą_, a nie z _przejściem_. Znaczniki trzymają krok **plus jeden**, więc zero świeżej
tablicy `int[n]` już znaczy „nie widziany” i nie trzeba przejścia inicjującego.

Krok po kroku na przykładzie, jeden wiersz na krok:

![findCycle na {3, 2, 1, 2, 4} od indeksu 0: każdy indeks dostaje znacznik krok plus jeden, aż w kroku 4 przejście dochodzi do indeksu 2 i zastaje go już oznaczonego](img_3.png)

_`findCycle`: do indeksu 2 dochodzimy ponownie w kroku 4, a ma on znacznik 3, więc pierwszy raz
staliśmy na nim w kroku 2. To `μ`, a odległość wstecz, `4 − 2`, to `λ`._

**Floyd.** Jeden wskaźnik idzie o jeden indeks na krok, drugi o dwa. Oba trafiają na cykl; szybszy
zyskuje jedno miejsce na krok, więc dogania wolniejszego od tyłu. W chwili spotkania, po `t`
krokach wolniejszego:

```
zając przeszedł 2t, żółw t, a stoją na tym samym indeksie
   =>  różnica 2t - t = t to całkowita liczba okrążeń   =>  lambda dzieli t
```

To cała sztuczka, a reszta z niej wynika. **Wyślij jeden wskaźnik z powrotem na `start` i prowadź
oba po jednym kroku.** Po `μ` krokach ten zawrócony jest na wejściu; drugi przeszedł `t + μ`, czyli
`μ` plus całe okrążenia, więc też jest na wejściu. Nie mogły spotkać się wcześniej: przed krokiem `μ`
zawrócony wskaźnik jest na ogonie, a drugi, już `t ≥ μ` kroków dalej, na cyklu, a ogon i cykl nie
mają wspólnych indeksów. Pierwsze spotkanie to więc wejście, a liczba kroków to `μ`. Jedno okrążenie
stamtąd daje `λ`.

To samo przejście w dwóch fazach:

![findCycleInConstantSpace na tej samej tablicy: w fazie 1 żółw idzie o jeden krok, a zając o dwa, aż spotkają się na indeksie 2; w fazie 2 jeden wskaźnik wraca na start i oba idą po jednym kroku, aż spotkają się na wejściu](img_4.png)

_`findCycleInConstantSpace`: punkt spotkania sam niczego nie mówi; dopiero faza 2 zamienia go w
`μ`, a robotę wykonuje powrót na `start`._

**Brent.** Zaparkuj żółwia i puść zająca: jeśli zając wróci do zaparkowanego indeksu, przebyta droga
_jest_ `λ`, bez drugiego okrążenia do mierzenia. Pytanie tylko, jak długo pozwolić mu biec przed
przestawieniem żółwia, a odpowiedź to podwajać limit za każdym razem: zaparkuj na pozycji zająca,
pozwól na `2^k` kroków, potem `2^(k+1)`. Pierwszy limit, który jest zarazem `≥ λ` i liczony z miejsca
na cyklu, się udaje, a podwajanie dochodzi do niego w `O(log(μ+λ))` restartach, przekraczając
najwyżej dwukrotnie. `μ` nie wymaga już sztuczki: ustaw jeden wskaźnik `λ` kroków przed drugim i
prowadź je razem; przewaga całego okrążenia jest niewidoczna na cyklu i oczywista na ogonie, więc
spotykają się dokładnie na wejściu.

Podwajanie to też jedyne miejsce, gdzie `int` może się przepełnić, przy `power = 2^31`. Bez ochrony
byłoby nieszkodliwe, gdy żółw jest już na cyklu, i zawieszeniem, gdy nie jest (ogon dłuższy niż
`2^30`), więc `power` zatrzymuje się na `Integer.MAX_VALUE`: jedno porównanie, dla tablicy, która
potrzebowałaby 4 GB, żeby w ogóle istnieć.

## Sprawdzane w trakcie przejścia, nigdy z góry

Element spoza `0 … n-1` to `IllegalArgumentException`, a nie `ArrayIndexOutOfBoundsException`, ale
dopiero gdy przejście go faktycznie przeczyta. Sprawdzenie tablicy z góry kosztowałoby O(n)
_czasu_, a właśnie tego unikają metody o stałej pamięci: w pomiarach poniżej dotykają około 1 200
elementów tablicy miliona elementów. `{1, 0, 999}` od `start = 0` dostaje więc odpowiedź, a nie
odmowę, i dokumentacja to mówi.

## Ile to kosztuje

Zmierzone doraźnie, nie odtwarzane przy budowaniu. JDK 25.0.1 (Temurin), Intel i7-14650HX
(L2 24 MB, L3 30 MB), `tab` wypełnione jednostajnie losowo, 2 000 przejść na wiersz:

| n | `μ + λ` | znaczniki | Floyd | Brent | Brent / Floyd |
|---:|---:|---:|---:|---:|---:|
| 10³ | 36.6 | 36.6 | 144.9 | 111.7 | 0.77 |
| 10⁵ | 409.7 | 409.7 | 1 574.4 | 1 249.3 | 0.79 |
| 10⁶ | 1 216.6 | 1 216.6 | 4 815.7 | 3 791.5 | 0.79 |

Odczyty, nie nanosekundy. Dwa wnioski. **Rho jest maleńkie w porównaniu z tablicą, w której żyje**:
dla losowego `f` obie połowy wynoszą średnio `sqrt(pi n / 8)`, więc `μ + λ ≈ 1.25 sqrt(n)`, czyli
1 217 odczytów w tablicy miliona elementów, a środkowa kolumna tabeli to ten wzór zmierzony. I
**Brent czyta konsekwentnie około czterech piątych tego, co Floyd**: każdy krok Brenta to jeden
odczyt, a zając Floyda czyta dwa razy, a okrążenie jest liczone w trakcie szukania, zamiast
przechodzone ponownie po nim.

Liczba odczytów to nie czas działania, bo jedna z tych metod alokuje. Najlepszy z 5, 200 losowych
startów, ta sama losowa `tab`:

| n = 10⁷, losowa `tab` (`μ + λ ≈ 3 900`) | na wywołanie |
|---|---:|
| znaczniki | 1.66 ms |
| Floyd | **0.079 ms** |
| Brent | **0.084 ms** |

**21 razy**, i nic z tego to nie przejście. Przejście to cztery tysiące odczytów; 40 MB znaczników
jest alokowane i zerowane przy każdym wywołaniu i to jest cały pomiar. To normalny przypadek
(losowa `tab` zawsze ma rho rzędu `sqrt(n)`) i powód, dla którego metody o stałej pamięci to nie
tylko optymalizacja pamięci.

Oczywisty kontrargument to rho długie jak tablica, gdzie znaczniki czytają cztery razy mniej. On też
nie trafia, dopóki przejście jest ułożone sekwencyjnie:

| n = 10⁷, łańcuch wchodzący w cykl (`μ = 8·10⁶`, `λ = 2·10⁶`) | na wywołanie |
|---|---:|
| znaczniki | 45.8 ms |
| Floyd | 27.8 ms |
| Brent | **22.6 ms** |

10⁷ odczytów przegrywa z 4.2·10⁷ Floyda (i 2.8·10⁷ Brenta), bo płacimy nie za odczyty. To przejście
to `i → i+1`, więc prefetcher ma każdą linię gotową, zanim o nią poprosimy, a dodatkowy odczyt jest
prawie darmowy, podczas gdy 40 MB znaczników trzeba zaalokować, wyzerować i zapisać niezależnie od
przejścia.

Gdy przejście skacze po pamięci, ranking wreszcie się odwraca: jeden cykl przez potasowaną
permutację, `μ = 0`, `λ = n`:

| n = 10⁷, jeden losowy cykl długości n | na wywołanie |
|---|---:|
| znaczniki | **875 ms** |
| Floyd | 1 604 ms |
| Brent | 2 104 ms |

Każdy odczyt to teraz chybienie w pamięć podręczną, a prefetcher nie ma czego przewidywać, więc
decyduje liczba odczytów _zależnych od poprzedniego_: `λ` dla znaczników (zapisy znaczników nie
zależą od przejścia i nakładają się na nie), około `3λ` dla Floyda, bliżej `4λ` dla Brenta. To też
jedyna tabela, w której Brent przegrywa z Floydem, choć czyta mniej: Floyd przesuwa dwa niezależne
wskaźniki, więc w locie jest naraz więcej niż jedno chybienie, a pojedynczy zając Brenta to czysty
łańcuch zależności. Wobec 21 razy na losowej `tab` i 1.6 razy na łańcuchu dla metod o stałej
pamięci, znaczniki odbierają tę rundę 1.8 razy.

**W skrócie:** znaczniki, gdy wiadomo, że przejście obejmie dużą część tablicy, _a_ tablica jest zbyt
rozrzucona, żeby ją prefetchować; w przeciwnym razie Floyd albo Brent, które nigdy nie alokują, a w
przypadku, który naprawdę się zdarza, są też dwadzieścia razy szybsze.

## Testy

Kluczowy test jest wyczerpujący: **każda funkcja na najwyżej sześciu indeksach, przechodzona z
każdego startu**, czyli 50 069 tablic i 296 675 przejść, a każdy wynik sprawdzany względem
_definicji_, a nie innej implementacji. Dla `(entry, μ, λ)` test sprawdza, że przejście stoi na
`entry` po `μ` krokach, że `λ` to najmniejsza liczba kroków wracająca z `entry` do siebie i że nic,
czego przejście dotknęło wcześniej, nie leży na tym cyklu. Te trzy warunki dopuszczają dokładnie
jedną odpowiedź, więc to wzorzec, a nie słabsze powtórzenie kodu. Trzy implementacje muszą potem
zgadzać się ze sobą, a przejście zwrócone przez `walkToFirstRepeat` musi iść po `tab` i domykać się
tam, gdzie mówi rekord.

Sześć indeksów wystarcza na każdy możliwy kształt: brak ogona i najdłuższy ogon, pętla własna, cykl
przez wszystko, kilka składowych, indeksy nieosiągalne.

**Na drugim końcu zakresu** jeden test przechodzi pięćdziesiąt milionów indeksów, czyli 200 MB
intów, w stercie 512 MB, w której surefire uruchamia ten build. Pyta tylko metody o stałej pamięci,
bo `findCycle` chciałaby drugie 200 MB na znaczniki, a jedna tablica służy dwa razy, nadpisywana w
miejscu zamiast alokowana ponownie: najpierw jako rho długie jak ona sama (`μ = 4·10⁷`, `λ = 10⁷`, co
kosztuje Floyda 2.1·10⁸ odczytów, a Brenta 1.7·10⁸), potem wypełniona losowo, gdzie rho kurczy się
do kilku tysięcy kroków, a test wymaga, żeby było mniejsze niż tysięczna tablicy. To dwa reżimy, o
których są pomiary wyżej, sprawdzone asercjami zamiast mierzenia czasu.

**Każdy test ma limit czasu i własny wątek** (`@Timeout(30, SEPARATE_THREAD)` na klasie), bo
typowym błędem przejścia po cyklu nie jest zła odpowiedź, tylko brak końca. Niech zając Floyda
robi jeden krok zamiast dwóch, a oba wskaźniki krążą po cyklu w stałej odległości bez końca, więc
żadna asercja nie zostanie sprawdzona. To nie hipoteza, tylko jedna z mutacji poniżej, a bez limitu
czasu zatrzymuje build, zamiast przerwać go błędem.

### Sprawdzenie mutacjami

Jedenaście celowych uszkodzeń, dziesięć złapanych:

| Mutacja | Złapana przez |
|---|---|
| znacznik zapisuje krok po jego wykonaniu, a nie przed | 21 błędów |
| `μ` odczytane wprost ze znacznika, bez przesunięcia | 14 błędów asercji, 7 wyjątków |
| Floyd liczy okrążenie zamykające od zera | 14 błędów asercji, 6 wyjątków |
| Brent nie zeruje licznika okrążenia przy przestawieniu | 11 błędów, 7 przekroczeń czasu |
| Floyd nigdy nie wysyła wskaźnika z powrotem na `start` | 11 błędów |
| Brent pomija przewagę `λ` kroków przed szukaniem `μ` | 11 błędów |
| `walkToFirstRepeat` zwraca o jeden indeks za mało | 6 błędów |
| `nextOf` przestaje sprawdzać, czy element jest indeksem | 1 błąd |
| `Cycle` przyjmuje długość zero | 1 błąd |
| **zając Floyda robi jeden krok zamiast dwóch** | **15 przekroczeń czasu**: `TimeoutException` po 30 s, w sumie siedem i pół minuty |
| pusta tablica nie jest odrzucana jawnie | **przetrwała** |

Ocalała mutacja jest równoważna, a nie jest luką: gdy tablica jest pusta, `start >= tab.length`
zachodzi dla każdego `start`, więc przejście jest odrzucane ze sprawdzeniem i bez niego. Różni się
tylko komunikat, i dla niego sprawdzenie zostaje.

Dwa wiersze z przekroczeniem czasu warto czytać razem. Obie mutacje zostawiają wskaźnik goniący
coś, czego nigdy nie dogoni, i żadna nie daje złej odpowiedzi: nie dają żadnej. Bez limitu czasu na
klasie nie przerywają buildu błędem, tylko go zatrzymują.

```
mvn test -Dtest=NextIndexCycleTest
```
