# Minimum Swaps: odpowiedź to `n − cykle`, a żadne sortowanie jej nie policzy

Notatka do [`src/java/array/MinimumSwaps.java`](../../src/java/array/MinimumSwaps.java),
testy: [`test/java/array/MinimumSwapsTest.java`](../../test/java/array/MinimumSwapsTest.java).

**Zadanie.** Tablica zawiera kolejne liczby `1, 2, … n` w jakiejś kolejności, bez powtórzeń. Zwróć
najmniejszą liczbę zamian _dowolnych_ dwóch elementów, która posortuje ją rosnąco. Przykłady:
`[4,3,1,2] → 3`, `[2,3,4,1,5] → 3`, `[1,3,5,2,4,6,7] → 3`. Ograniczenia: `1 ≤ n ≤ 10⁵`.

## Tablica to permutacja, a nie lista liczb

Wartości to dokładnie `1 … n`, więc `arr[i]` to nie dane, tylko **adres**. Czytaj go tak:

```
sigma(i) = arr[i] - 1        "to, co stoi na i, należy na sigma(i)"
```

`sigma` jest permutacją `0 … n-1`, więc rozpada się na rozłączne cykle, a całe zadanie sprowadza się
do ich policzenia:

```
najmniej zamian = n - (liczba cykli)
```

przy czym punkt stały liczy się jako cykl długości jeden. Na przykładzie rozpisanym w treści:

```
i         0  1  2  3  4  5  6
arr       7  1  3  2  4  5  6
sigma(i)  6  0  2  1  3  4  5

cykle     (0 6 5 4 3 1) (2)      c = 2      7 - 2 = 5
```

Pięć: dokładnie tyle zamian wykonuje rozpisanie w treści, i to nie przypadek, że te zamiany to
rozwijanie cyklu po jednym elemencie.

## Dlaczego `n − c`, w obie strony

Wszystko opiera się na jednym lemacie: **jedna zamiana zmienia liczbę cykli dokładnie o jeden.**

| Zamiana | Skutek dla cykli |
|---|---|
| dwie pozycje z **tego samego** cyklu | dzieli go na dwa: `c + 1` |
| dwie pozycje z **różnych** cykli | łączy je w jeden: `c - 1` |

Nie ma trzeciego przypadku ani zmiany o dwa. Stąd:

- **Nie mniej niż `n − c`.** Posortowana tablica to permutacja z `n` cyklami (każdy element to punkt
  stały). Zaczynając od `c` cykli i zyskując najwyżej jeden na zamianę, do `n` potrzeba co najmniej
  `n − c` zamian.
- **Nie więcej niż `n − c`.** Cykl długości `k` sortuje się w `k − 1` zamianach: wyślij dowolny
  element na miejsce, a zostanie cykl długości `k − 1`. Suma po cyklach to `Σ(k_i − 1) = n − c`.

Ograniczenie z góry i z dołu się spotykają, więc liczba _jest_ odpowiedzią. Niczego się nie szuka,
nie porównuje i nigdy nie patrzy na uporządkowanie wartości, tylko na ich adresy.

Oba skrajne przypadki wynikają od razu: posortowana tablica ma `n` cykli i kosztuje `0`, a jeden
cykl przez wszystkie pozycje kosztuje `n − 1`, najwięcej, ile może kosztować dowolne wejście.

## Zamiany to nie inwersje

Oczywista zła odpowiedź to posortować i policzyć zamiany. Liczba zamian w sortowaniu bąbelkowym to
liczba **inwersji**, a ona odpowiada na inne pytanie, bo sortowanie bąbelkowe zamienia tylko
_sąsiednie_ elementy. Na odwróconej tablicy obie liczby rozjeżdżają się najbardziej, jak mogą:

| `n` | inwersje (tylko sąsiedzi) | najmniej zamian (dowolna para) |
|---:|---:|---:|
| 5 | 10 | 2 |
| 1 000 | 499 500 | 500 |
| 100 000 | ~5 × 10⁹ | 50 000 |

Kwadratowo wobec liniowo: `n(n-1)/2` wobec `floor(n/2)`. Ograniczenie zamian do sąsiadów to nie
drobna zmiana.

Sortowanie przez wybieranie _jest_ za to tutaj optymalne, a powodem jest lemat wyżej. Jego krok `i`
sprowadza wartość `i + 1` na miejsce, skądkolwiek jest, co odcina dokładnie jeden punkt stały od
cyklu przez `i`, i zamienia tylko wtedy, gdy pozycja `i` nie jest już poprawna: jedna zamiana, jeden
cykl więcej, żadnego zmarnowanego ruchu. `minimumSwapsInPlace` to ten algorytm bez liniowego
szukania: `arr[i]` już mówi, gdzie należy, więc nie ma czego szukać.

## Pięć sposobów liczenia cykli

| Metoda | Czas | Dodatkowa pamięć | Tablica po wywołaniu |
|---|---|---|---|
| `minimumSwaps`: `boolean[] visited` | O(n) | n bajtów | nietknięta |
| `minimumSwapsInPlace`: zamiany od lidera cyklu | O(n) | **O(1)** | **posortowana** |
| `minimumSwapsMarkingSigns`: bit odwiedzin w znaku | O(n) | **O(1)** | nietknięta |
| `minimumSwapsByUnionFind`: cykle jako składowe | O(n·α(n)) | 9n bajtów | nietknięta |
| `minimumSwapsOfAnyDistinctValues`: sortowanie, potem rangi | O(n log n) | 9n bajtów | nietknięta |

### Bit odwiedzin to cała przestrzeń projektowa

Trzy pierwsze to to samo liniowe przejście. Różnią się tylko jednym: gdzie trzymają odpowiedź na
pytanie „czy już tu byłem?”:

- **w tablicy pomocniczej**: n bajtów, uczciwie i oczywiście;
- **w bicie znaku samej tablicy**: bez żadnej alokacji, bo wartości są na pewno dodatnie, więc
  zmiana znaku oznacza pozycję, nie niszcząc wartości. Ceną są dwa dodatkowe przejścia (sprawdzenie
  i przywrócenie) i tablica, która dla równoległego czytelnika w trakcie wywołania jest widocznie
  ujemna;
- **nigdzie**, bo przejście zużywa wejście. Odwiedzona pozycja to pozycja, na której stoi już jej
  własna wartość, a testem jest `arr[i] != i + 1`. To jedyna droga do pamięci O(1) w jednym
  przejściu i kosztuje wywołującego jego tablicę.

Żadna z trzech nie jest szybsza rzędem złożoności. To trzy różne odpowiedzi na pytanie „na co mogę
wydać?”.

Dwie pierwsze, krok po kroku na `[4, 3, 1, 2]`: ten sam pojedynczy cykl `0 → 3 → 1 → 2 → 0`,
policzony dwa razy:

![Przejście po cyklu z osobną tablicą odwiedzin, krok po kroku na 4, 3, 1, 2](img_1.png)

_`minimumSwaps`: bit „czy już tu byłem?” żyje w tablicy pomocniczej._

![To samo przejście z bitem odwiedzin pożyczonym ze znaku każdej wartości](img.png)

_`minimumSwapsMarkingSigns`: to samo przejście, z bitem pożyczonym ze znaku tablicy i oddanym
przez blok `finally` w kroku 6._

## Pomiary

Doraźnie, `n = 10⁷`, najlepszy z pięciu, JDK 25, jednorazowym programem, nie odtwarzane przy budowaniu.

| kształt, `n = 10⁷` | `boolean[]` odwiedzin | znak | w miejscu | union-find | sortowanie + rangi |
|---|---:|---:|---:|---:|---:|
| **losowa permutacja** | 641 ms | 593 ms | **565 ms** | 739 ms | 2 638 ms |
| jeden cykl długości `n`, `sigma(i) = i+1` | 15 ms | 22 ms | **13 ms** | 125 ms | 526 ms |
| 5 × 10⁶ transpozycji | 10 ms | 16 ms | **8 ms** | 67 ms | 528 ms |
| już posortowana | 9 ms | 17 ms | **3 ms** | 16 ms | 473 ms |

**Tablica pomocnicza nie jest darmowa, i to nie tylko w bajtach.** Na losowej permutacji każdy
krok przejścia to chybienie w pamięć podręczną, a `visited[]` dokłada _drugi_ strumień losowych
odczytów do tego, który tablica już ma. Metoda alokująca 10 MB jest więc **najwolniejsza** z trzech
liniowych przejść, a sztuczka ze znakiem, płacąca za dwa dodatkowe pełne przejścia, i tak wygrywa,
bo te przejścia są sekwencyjne i dobrze się prefetchują, a chybienie, którego unika, nie.

**Co odwraca się, gdy tylko dostęp do pamięci staje się łatwy.** Tam, gdzie przejście już jest
sekwencyjne (`sigma(i) = i + 1` albo pole transpozycji), nie ma chybień, z którymi drugi strumień
mógłby rywalizować, a dwa dodatkowe przejścia sztuczki ze znakiem to po prostu dwa dodatkowe
przejścia: 16 ms wobec 10 ms. Tablica pomocnicza wygrywa, gdy pamięć jest łatwa, i przegrywa, gdy
jest trudna, odwrotnie niż przewiduje liczenie bajtów.

**Żadna z tych różnic nie jest duża, a dwie pozostałe kolumny są.** 641 ms wobec 565 ms to 13%.
Union-find nigdy nie jest szybszy, a tam, gdzie przejście jest tanie, jest 8 razy wolniejszy, więc
jego koszt naprawdę jest tak zły, jak mówi tabela wyżej. A porzucenie obietnicy `1 … n` kosztuje
na tych samych danych **4 razy** więcej, 2 638 ms wobec 641 ms: uczciwa cena konieczności
sortowania, żeby poznać rangi.

Uwaga na ostatni wiersz: na już posortowanym wejściu przejście w miejscu nie robi żadnej zamiany, w
jednym przejściu i bez alokacji, w 3 ms, a „sortowanie + rangi” dalej płaci 473 ms za sortowanie,
którego nie może pominąć.

## Union-find przegrywa, a i tak warto go mieć

Ten sam rząd liniowy, dziewięć razy więcej pamięci, skakanie po wskaźnikach tam, gdzie inne idą po
kolei: pod względem kosztu `minimumSwapsByUnionFind` przegrywa ze wszystkim powyżej. Jest w pliku
dla pytania, na które przejście po cyklach nie odpowie.

![Union-find na tej samej tablicy: indeksy łączone z miejscami docelowymi ich wartości, cykle jako składowe](img_2.png)

_To samo `[4, 3, 1, 2]` jako składowe zamiast przejścia. Uwaga na panel „Important note”:
union-find po cichu połyka powtórzoną krawędź, dlatego metoda płaci za osobne przejście
`requirePermutation`._

Każde przejście tutaj zakłada, że zamiany są **nieograniczone**: dowolne dwie pozycje, w dowolnej
chwili. Bez tego rozkład na cykle nic nie mówi. Gdy zamiast tego dana jest lista par pozycji, które
_wolno_ zamieniać, składowe tego grafu odpowiadają na pytanie o **osiągalność**: element może trafić
na pozycję dokładnie wtedy, gdy obie są w jednej składowej, więc tablicę da się posortować wtedy i
tylko wtedy, gdy każda składowa już zawiera wartości należące do jej pozycji, a najbliższe osiągalne
ułożenie to każda składowa posortowana osobno.

**Nie** uogólnia się za to liczba. `n − składowe` to _nie_ koszt przy ograniczeniach:

```
pozycje {0,1,2}, zamiany dozwolone na (0,1) i (1,2)
  -> jedna składowa, więc n - składowe = 2
[3,2,1] potrzebuje 3
```

i musi to być błąd, bo zamiany tylko sąsiadów to dokładnie sortowanie bąbelkowe, którego koszt to
liczba inwersji: o tym była sekcja o inwersjach. Liczenie zamian na grafie ograniczeń to problem
_token swapping_, w ogólnym przypadku NP-trudny; `n − c` to przypadek szczególny, w którym graf
dozwolonych zamian jest pełny. Union-find mówi coś użytecznego także wtedy, gdy nie jest.

## Poza `1 … n`

Z wartości liczyło się zawsze tylko jedno: **gdzie należy**, czyli jej ranga. Obietnica `1 … n` jest
cenna właśnie dlatego, że daje rangi za darmo (`arr[i] - 1`), i tylko dzięki temu liniowa odpowiedź w
ogóle jest możliwa.

`minimumSwapsOfAnyDistinctValues` porzuca obietnicę i za to płaci: sortuje kopię, szuka binarnie
rangi każdej wartości i uruchamia to samo przejście po cyklach. O(n log n), zdominowane przez
sortowanie, a przenumerowanie to powód, dla którego test może wziąć dowolną permutację, przepuścić
ją przez funkcję ściśle rosnącą i sprawdzić, że odpowiedź się nie zmienia.

**Powtórzenia są naprawdę poza zakresem**, a nie tylko nieobsłużone. Przy równych wartościach nie
ma jednego „gdzie należy”: element można wysłać na miejsce dowolnej z równych mu wartości, więc
odpowiedź przestaje być _jedną_ liczbą cykli i staje się maksymalizacją liczby cykli po wszystkich
poprawnych przypisaniach. To inne zadanie; plik odrzuca takie wejście, zamiast po cichu na nie
odpowiadać.

### Co metody odrzucają

Zadanie gwarantuje permutację, więc pilnowanie tego to tanie ubezpieczenie, a dla przejścia w
miejscu konieczność: podręcznikowe `while (arr[i] != i + 1) swap(...)` **kręci się w nieskończoność**
na `[1, 1]`, bo zamiana, którą chce zrobić, niczego nie zmienia.

Dwa przejścia w pamięci O(1) dostają sprawdzenie za darmo, z samego przechodzenia. W prawdziwej
permutacji każda pozycja ma dokładnie jednego poprzednika, więc przejście może skończyć się tylko
powrotem do swojego początku. Powtórzenie zostawia jakąś pozycję bez poprzednika; ta pozycja jest
wciąż nieodwiedzona, gdy przychodzi jej kolej na start, a jej przejście musi skończyć się gdzie
indziej. Jedno `if (position != start)` to pełny wykrywacz powtórzeń.

Union-find tego nie potrafi (łączy po cichu, połykając podwojoną krawędź bez protestu), więc płaci
za jawne przejście z `boolean[] seen`. I tak już wydał pamięć.

## Wzorce w testach

Nic nie opiera się na tym, że implementacja świadczy sama za siebie, a jeden wzorzec jest kluczowy:

| Wzorzec | Skala | Co łapie |
|---|---|---|
| **BFS od posortowanej tablicy** | **każda** permutacja `n ≤ 8`, 40 320 przy `n = 8` | że `n − c` nie jest _minimum_ |
| Ręcznie zapisane oczekiwane liczby | 18 przypadków: wszystkie trzy przykłady, rozpisanie z treści, skrajności | złą liczbę cykli |
| Permutacje zbudowane z wybranej struktury cykli | 300 tablic do `n = 200` | złą obsługę wielu krótkich cykli |
| Sortowanie przez wybieranie z szukaniem | 300 losowych permutacji, O(n²) | jakiekolwiek rozumowanie o cyklach |
| Trzy stałe odpowiedzi przy 10⁷ | jeden cykl długości `n`, 5M transpozycji, dowolne różne wartości | przepełnienie, głębokość i skalę |

BFS to wzorzec, który dowodzi twierdzenia, zamiast je zakładać. Każdy inny wzorzec, łącznie z
naiwnym sortowaniem przez wybieranie, potwierdza tylko, że `n − c` jest **osiągalne**. Przeszukiwanie
wszerz od posortowanej tablicy, z jedną zamianą dowolnych dwóch pozycji jako krawędzią, zwraca
prawdziwe minimum z samej konstrukcji; permutacje do ośmiu elementów mieszczą się w `long` po cztery
bity, więc całe `S₈` zmieści się w `HashMap` i liczy się w niecałą sekundę.

### Sprawdzenie mutacjami

Piętnaście celowych uszkodzeń, trzynaście złapanych:

| Mutacja | Złapana przez |
|---|---|
| cykl kosztuje `k` zamiast `k − 1` | 23 błędy |
| rangi zastąpione surowymi indeksami | 21 |
| przejście ze znakiem ignoruje własne oznaczenie | 20 |
| zwrócone `składowe` zamiast `n − składowe` | 17 |
| `if` zamiast `while` w przejściu w miejscu | 8 |
| usunięte przywracanie znaków | 2 błędy asercji, 2 wyjątki |
| usunięte sprawdzenie domknięcia w przejściu ze znakiem | 2 |
| sprawdzenie domknięcia z tablicą odwiedzin, ochrona przed powtórzeniem w miejscu, sprawdzenie permutacji w union-find, sprawdzenie różności, sprawdzenie zakresu przesunięte o jeden | po 1 |
| przejście w miejscu zamienia z `i + 1` zamiast z miejscem wartości | **zawieszenie**: `TimeoutException` po 30 s |

Ostatni wiersz to powód, dla którego klasa w ogóle ma limit czasu: to uszkodzenie sprawia, że
przejście w miejscu w nieskończoność przestawia dwa elementy na danych, na które każda inna metoda
odpowiada od razu, a zanim powstał limit, zatrzymywało build, zamiast go przerwać błędem.

Dwa ocalałe to mutanty równoważne, a nie luki: usunięcie łączenia według rozmiaru i usunięcie
połowienia ścieżek to usunięcie optymalizacji union-find bez zmiany tego, co liczy.

```
mvn test -Dtest=MinimumSwapsTest
```
