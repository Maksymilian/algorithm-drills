# Fibonacci: rekurencja i cena zapisania jej dosłownie

Notatka do [`src/java/recursion/Fibonacci.java`](../../src/java/recursion/Fibonacci.java),
testy: [`test/java/recursion/FibonacciTest.java`](../../test/java/recursion/FibonacciTest.java).

**Zadanie.** `F(0) = 0`, `F(1) = 1`, `F(n) = F(n-1) + F(n-2)`. Zapisz to jako rekurencję; zapisz
jeszcze raz z zapamiętywaniem; potem rozlicz, ile każda wersja kosztuje procesora i pamięci.

## Rekurencja to definicja i właśnie to jest z nią nie tak

```java
return n <= 1 ? n : naive(n - 1) + naive(n - 2);
```

Nic w tej linii nie jest błędne poza tym, że nie ma pamięci. Każde wywołanie przechodzi całe swoje
poddrzewo od nowa, a dwa poddrzewa pokrywają się prawie w całości:

```
                        F(6)
              F(5)                    F(4)
        F(4)        F(3)        F(3)        F(2)
     F(3)  F(2)   F(2) F(1)   F(2) F(1)   F(1) F(0)
   F(2) F(1) ...
```

`F(4)` jest liczone dwa razy, `F(3)` trzy razy, `F(2)` pięć razy, a to same liczby Fibonacciego:
**`F(k)` jest liczone `F(n-k+1)` razy**. Licząc każde wywołanie,

```
C(0) = C(1) = 1,    C(n) = C(n-1) + C(n-2) + 1        =>    C(n) = 2F(n+1) - 1
```

czyli ta sama rekurencja z dodatkową jedynką, a postać zamknięta jest dokładna, nie asymptotyczna.
Powód warto powiedzieć wprost: jedyna arytmetyka tego algorytmu to dodawanie jedynek w liściach, więc
dojście do `F(n)` wymaga `F(n)` takich dodawań. **Czas działania to wielkość samej odpowiedzi**, a
`F(n)` rośnie o czynnik `φ = 1.618...` na krok.

Zmierzone, JDK 25.0.1 (Temurin), Intel i7-14650HX, doraźnie, nie odtwarzane przy budowaniu:

| n | wywołania | czas | wobec `n-5` |
|---:|---:|---:|---:|
| 25 | 242 785 | 1.0 ms | — |
| 30 | 2 692 537 | 2.8 ms | 2.7× |
| 35 | 29 860 703 | 32.9 ms | 11.7× |
| 40 | 331 160 281 | 360.1 ms | 11.0× |
| 45 | 3 672 623 805 | 4 000.6 ms | 11.1× |

Ostatnia kolumna to cała historia i nie jest przypadkiem: **φ⁵ = 11.09**. Pięć więcej, jedenaście
razy więcej pracy, bez końca. Maszyna wykonywała 912 milionów wywołań na sekundę, a i tak
potrzebowała czterech sekund na `F(45)`.

**A pamięci nie kosztuje to wcale.** Żadnej alokacji, a stos nigdy nie schodzi głębiej niż `n`
ramek, tu 45, kilka kilobajtów. To zaskakuje ludzi: naiwny Fibonacci w żaden sposób nie jest
problemem pamięci. To czysta katastrofa procesorowa.

## Dwie różne granice: głębokość i liczba wywołań

Rekurencja ma dwie niezależne granice i łatwo je pomylić:

| Granica | Co mierzy | Co ją przekracza | Objaw |
|---|---|---|---|
| **głębokość** | ile wywołań jest otwartych **naraz**, jedno na drugim | ~10⁴–2·10⁴ ramek przy domyślnym stosie 1 MB | `StackOverflowError` |
| **liczba wywołań** | ile wywołań jest **łącznie** przez cały przebieg | ~10⁸–10⁹ na sekundę | program „wisi” |

Naiwne `naive(90)` jest daleko od pierwszej i beznadziejnie za drugą:

| `naive(90)` | Wartość | Granica | Problem? |
|---|---|---|---|
| głębokość | **90** ramek | ~20 000 | nie |
| liczba wywołań | `2F(91) − 1` ≈ **9.3·10¹⁸** | 9.12·10⁸ / s | **~324 lata** |

Rekurencja schodzi jedną gałęzią (`F(90) → F(89) → … → F(1)`), wraca i schodzi następną, więc na
stosie leży naraz najwyżej 90 ramek. Robi to jednak wykładniczo wiele razy. Program się nie wywraca,
tylko nigdy nie kończy. Dlatego oceniając rekurencję, zadajemy **dwa** pytania: jak głęboko zejdzie
(stos) i ile będzie wywołań łącznie (czas).

## Problemem nie jest rekurencja, tylko powtórzenia

Wykładniczy koszt daje połączenie dwóch rzeczy: **dwa wywołania na poziom** (drzewo się rozgałęzia)
i **podproblemy, które się pokrywają i maleją tylko o 1 albo 2** (drzewo ma głębokość ~`n`).
Same dwa wywołania nie szkodzą, jeśli dzielą problem na rozłączne połowy:

| Rekurencja | Wywołań na poziom | Podproblem maleje | Pokrywanie | Koszt |
|---|---|---|---|---|
| naiwny Fibonacci | 2 | o 1 i o 2 | **prawie całkowite** | O(1.618ⁿ) |
| sortowanie przez scalanie | 2 | do połowy | brak | O(n log n) |
| Fibonacci, jedno wywołanie niosące parę | 1 | o 1 | brak | O(n) |
| Fibonacci, szybkie podwajanie | 1 | do połowy | brak | O(log n) |

Wystarczy jedno wywołanie, które niesie w dół dwie ostatnie wartości, zamiast liczyć je od nowa:

```java
private static long fib(int n, long previous, long current) {
    if (n == 0) {
        return previous;
    }
    return fib(n - 1, current, previous + current);
}
// fib(n, 0, 1)
```

Drzewo staje się ścieżką: `n` wywołań i `n` dodawań, czyli pętla z [wersji iteracyjnej](#wersja-iteracyjna-i-co-zapamiętywanie-wciąż-kosztuje)
zapisana rekurencyjnie. To jest „rekurencja niosąca” z tabeli ramek niżej: szybka, ale nadal `n`
ramek stosu, bo Java nie usuwa wywołań ogonowych.

Trzy sposoby usunięcia powtórzeń, od najprostszego:

1. **zapamiętać** wyniki: dwa wywołania zostają, każde `F(k)` liczone raz, O(n);
2. **nieść stan w dół**: jedno wywołanie, O(n), bez tabeli;
3. **dzielić na pół**: jedno wywołanie, O(log n) (szybkie podwajanie, niżej).

## Zapamiętywanie: to samo drzewo z każdym powtórzeniem skreślonym

```java
if (n <= 1) {
    return n;
}
if (known[n] != 0) {
    return known[n];
}
return known[n] = memoized(n - 1, known) + memoized(n - 2, known);
```

Jeden odczyt i jeden zapis, a kształt rekurencji poza tym się nie zmienia. Zmienia się to, że do
policzonego już poddrzewa nigdy nie wchodzimy drugi raz, więc drzewo, szerokie na dwie gałęzie w
każdym węźle, zwija się do ścieżki w dół i ścieżki z powrotem: **`2n - 1` wywołań i `n - 1`
dodawań** tam, gdzie było `2F(n+1) - 1` i `F(n+1) - 1`.

`0` znaczy „jeszcze nie policzone”, co oszczędza przejście wypełniające i jest bezpieczne z jednego
konkretnego powodu: `F(k)` jest zerem tylko dla `k = 0`, a to przypadek bazowy, którego nigdy nie
zapisujemy. (Ta sama sztuczka co znaczniki kroków w [`array.NextIndexCycle`](../array/next-index-cycle.md),
z tego samego powodu: niezapisana tablica `int[]`/`long[]` to same zera, więc wartość, która nie może
legalnie być zerem, może za darmo znaczyć „puste”.)

Tabela jest alokowana przy każdym wywołaniu, więc nic nie jest współdzielone między wywołaniami ani
wątkami. Przeniesienie jej do pola `static`, żeby była „ciepła”, to oczywisty następny pomysł i tak
ta klasa dostałaby błąd współbieżności: model pamięci nie gwarantuje atomowości zapisów `long`, więc
dwa wątki wypełniające jedną tabelę mogą w zasadzie odczytać w połowie zapisaną wartość.

## Wersja iteracyjna i co zapamiętywanie wciąż kosztuje

```java
for (int i = 0; i < n; i++) {
    long next = previous + current;
    previous = current;
    current = next;
}
```

Rekurencja sięga wstecz dokładnie o dwa miejsca, więc wystarczą dwie zmienne: **bez tabeli, bez
rekurencji, bez wywołań**. Liczenie w kolejności rosnącej sprawia, że wartość jest potrzebna tylko
wtedy, gdy jest jeszcze jedną z dwóch ostatnich, i to cały powód, dla którego tabelę można wyrzucić.

| przy `n = 92` | na wywołanie |
|---|---:|
| rekurencja z zapamiętywaniem | 207.3 ns |
| iteracja | **9.7 ns** |

**21 razy różnicy** przy tych samych 92 dodawaniach. Różnica to wszystko, czego zapamiętywanie nie
usunęło: tablica 744 bajtów do zaalokowania i wyzerowania, 183 wywołania do wykonania i powrotu oraz
tabela do indeksowania zamiast dwóch wartości w rejestrach. Zapamiętywanie usuwa wykładnik; iteracja
usuwa resztę.

## Procesor i pamięć obok siebie

Przy `n = 92`, największym, na jakie pozwala `long`:

| | Dodawania | Wywołania | Czas | Sterta | Stos |
|---|---:|---:|---:|---:|---:|
| naiwna rekurencja | `F(n+1) - 1` = 1.2·10¹⁹ | 2.44·10¹⁹ | **~848 lat** | nic | 92 ramki |
| rekurencja z zapamiętywaniem | 91 | 183 | 207 ns | 744 bajty | 92 ramki |
| iteracja | 92 | 1 | **9.7 ns** | **nic** | **1 ramka** |

848 lat to zmierzona szybkość wywołań, 912 milionów na sekundę, podzielona przez dokładną liczbę
wywołań. To nie ograniczenie ani oszacowanie asymptotyki, tylko to, ile maszyna, która liczyła `F(45)`
cztery sekundy, liczyłaby `F(92)`.

Czytaj tabelę odwrotnie, a lekcja jest ostrzejsza niż „zapamiętywanie jest szybsze”. **Pamięć to nie
oś, na której cokolwiek tu się zmienia**: różnica między najlepszym a najgorszym to 744 bajty. Różnica
w czasie to siedemnaście rzędów wielkości (848 lat wobec 207 nanosekund), a każdy z nich kupiono za
tabelę na tyle małą, że zginęłaby w linii pamięci podręcznej.

## Dlaczego tabela kończy się na 92

`F(92) = 7540113804746346429` mieści się w `long` z zapasem. `F(93) = 12200160415121876738` w ogóle się
nie mieści: jest większe niż `Long.MAX_VALUE = 9223372036854775807`. Każda metoda odrzuca wszystko
powyżej 92, zamiast zwracać po cichu zawiniętą odpowiedź, a test sprawdza tę granicę względem
`BigInteger`, żeby stała nie mogła rozjechać się z arytmetyką, którą rzekomo opisuje.

To też ucina pytanie o pamięć, zanim się zacznie: cała tabela, od `F(0)` do `F(92)`, to 93 liczby
`long`, czyli **744 bajty**. Dla Fibonacciego w `long` nie ma problemu pamięci do omawiania.

## Powyżej 92: `BigInteger`, gdzie pamięć staje się tematem

Zmień typ, a oba koszty zmieniają charakter. `F(n)` ma około `0.694n` bitów (`n·log₂φ`), więc
dodawanie przestaje mieć stały czas, a zapisana tabela przestaje być rzędem rejestrów:

| n | bity w `F(n)` | cała tabela | dwie zmienne | czas |
|---:|---:|---:|---:|---:|
| 1 000 | 694 | 59 807 B | 206 B | 0.7 ms |
| 10 000 | 6 942 | 4.5 MB | 1 768 B | 2.8 ms |
| 100 000 | 69 424 | **415 MB** | **17 KB** | 117.3 ms |
| 1 000 000 | 694 242 | _~43 GB_ | ~174 KB | — |

Ostatniego wiersza nie uruchomiono z powodu, który podaje jego środkowa kolumna. Trzymanie każdej
wartości kosztuje `0.347n²` bitów, **kwadratową pamięć dla liniowej odpowiedzi**, a pętla trzyma dwie
liczby i płaci `O(n)`. Przy `n = 100 000` to czynnik 25 000 i rośnie z każdym krokiem.

A rekurencja zyskuje drugą granicę, której pętla w ogóle nie ma:

| rekurencja niosąca | ramki przed `StackOverflowError` |
|---|---:|
| `long` | 21 571 |
| `BigInteger` | 12 268 |

Fibonacci z zapamiętywaniem na `BigInteger`, naturalne rozszerzenie kodu wyżej, przestaje więc działać
gdzieś przy `n = 12 000`, przy domyślnych ustawieniach stosu, niezależnie od rozmiaru sterty. Wersja
iteracyjna nie ma w sobie takiej liczby. **To prawdziwy argument przeciw zapamiętywaniu od góry w
skali** i nie ma nic wspólnego z wykładnikiem, do którego naprawy wprowadzono zapamiętywanie.

## Zapamiętywanie i programowanie dynamiczne to ten sam graf

Rekurencja z zapamiętywaniem i pętla odwiedzają dokładnie te same `n+1` podproblemów i wykonują
dokładnie te same `n` dodawań. Różnią się tylko kierunkiem: od góry na żądanie wobec od dołu w
kolejności ustalonej z góry. To cała różnica między zapamiętywaniem a programowaniem dynamicznym, a
Fibonacci to najmniejsze zadanie, które ją pokazuje; zob. [Max Subset Sum](../dynamic/max-subset-sum.md),
gdzie ten sam argument dotyczy sytuacji, w której kolejność nie jest oczywista, a wybór naprawdę coś
kosztuje.

## Szybciej niż liniowo

`n` dodawań to nie dno. Tożsamości

```
F(2k)   = F(k) · (2F(k+1) - F(k))
F(2k+1) = F(k)² + F(k+1)²
```

dochodzą do `F(n)` w `O(log n)` krokach: szybkie podwajanie, czyli potęgowanie macierzy
`[[1,1],[1,0]]` z usuniętą nadmiarowością. Dla `long` nie ma to sensu: 92 dodawania są już darmowe,
a siedem podwojeń, które je zastępują, to mnożenia. Dla `BigInteger` przy `n = 1 000 000` to właściwa
odpowiedź, a pętla nie, bo zamienia milion dużych dodawań w dwadzieścia dużych mnożeń.

Żadnego z nich nie ma w tej klasie. Ćwiczenie prosiło o rekurencję i jej wersję z zapamiętywaniem;
tu powinien zajrzeć ktoś, kto potrzebuje `F(10⁶)`.

Wynika z tego też, że **iteracja nie zawsze jest „o wiele lepsza”**:

| Iteracja zamiast… | Czy o wiele lepsza? |
|---|---|
| naiwnej rekurencji | tak, ale dzięki usunięciu powtórzeń, nie dzięki samej pętli |
| zapamiętywania, `long` | ~21× szybsza (9.7 ns wobec 207 ns), w praktyce bez znaczenia |
| zapamiętywania, `BigInteger`, duże `n` | tak: brak granicy stosu i O(1) liczb w pamięci zamiast całej tabeli |
| szybkiego podwajania, bardzo duże `n` | nie, przegrywa |

## Jak policzyć wywołania

**Zmierzyć licznikiem.** Pole zwiększane na wejściu do metody (a dla głębokości: `depth++` na
wejściu, `depth--` przed powrotem, `maxDepth = Math.max(maxDepth, depth)`):

```java
private static long calls;

static long naive(int n) {
    calls++;
    if (n <= 1) {
        return n;
    }
    return naive(n - 1) + naive(n - 2);
}
```

Daje to 177 wywołań dla `n = 10`, 21 891 dla 20 i 2 692 537 dla 30, czyli dokładnie `2F(n+1) − 1`.
Dla dużego `n` licznik nie pomoże, bo trzeba by doczekać wyniku, więc warto umieć to policzyć.

**Policzyć z rekurencji.** Jedno wywołanie to ono samo plus wywołania, które robi:
`C(n) = 1 + C(n-1) + C(n-2)` (rozwiązane na początku notatki).

**Oszacować: rozgałęzienie do potęgi głębokości.**

| Kształt rekurencji | Wywołania |
|---|---|
| 1 wywołanie, `n` maleje o 1 | n |
| 1 wywołanie, `n` maleje o połowę | log n |
| 2 wywołania, `n` maleje o połowę (scalanie) | ~2n wywołań, O(n log n) pracy |
| 2 wywołania, `n` maleje o 1 | **2ⁿ** |
| 2 wywołania, `n` maleje o 1 i o 2 (Fibonacci) | **~1.618ⁿ** |
| `k` wywołań, głębokość `d` | do **kᵈ** |
| z zapamiętywaniem | liczba **różnych** argumentów × wywołania na stan |

**Zamienić na czas:** ~10⁸–10⁹ prostych wywołań na sekundę (tu zmierzone 9.12·10⁸):

| Wywołania | Czas |
|---|---|
| 10⁶ | ~1 ms |
| 10⁹ | ~1 s |
| 10¹² | ~20 minut |
| 10¹⁸ | ~30 lat |

Na rozmowie: rekurencja na liczbę wywołań, oszacowanie `rozgałęzienie^głębokość`, porównanie z
budżetem 10⁸ na sekundę. Trzydzieści sekund i wiadomo, czy rozwiązanie się skończy.

## Rekurencja czy iteracja

Dla **tego samego algorytmu** iteracja w Javie jest zwykle trochę szybsza, o stałą. Złożoność
wyznacza algorytm, nie forma zapisu.

**Co JIT robi z rekurencją.** Kompiluje ją do kodu maszynowego jak każdą gorącą metodę, ale wstawia
(inline) rekurencyjne wywołania tylko na **jeden poziom** (`MaxRecursiveInlineLevel = 1`, sprawdzone
`java -XX:+PrintFlagsFinal -version`, JDK 25). Pętla dostaje pełny zestaw: rozwijanie, zmienne w
rejestrach, wektoryzację. Wywołań ogonowych JIT **nie usuwa**, więc głębokość zawsze kosztuje ramkę.
Samo wywołanie to jednak ułamek nanosekundy do kilku nanosekund (912 milionów na sekundę wyżej):
widać je dopiero przy ciele metody tak małym jak tu i miliardach wywołań.

**Skąd granica 10⁴.** Domyślny stos wątku to 1 MB (`ThreadStackSize = 1024`). Ile ramek się mieści,
zależy od rozmiaru ramki (parametry, zmienne lokalne): tu 21 571 dla `long` i 12 268 dla `BigInteger`,
a metoda z większą liczbą zmiennych padnie wcześniej. Dlatego `n ≥ 10⁴` to bezpieczny próg, od którego
zwykła rekurencja prawdopodobnie się wywróci, a testy HackerRank mają często `n = 10⁵`.

**Kiedy rekurencja:**

| Sytuacja | Przykłady | Dlaczego |
|---|---|---|
| dane są rekurencyjne | drzewa, JSON, system plików, wyrażenia | kod odwzorowuje strukturę |
| dziel i zwyciężaj | sortowanie przez scalanie, quicksort, szybkie podwajanie | głębokość ~log n |
| nawroty (backtracking) | permutacje, podzbiory, N hetmanów, sudoku, DFS | stos wywołań za darmo pamięta stan do cofnięcia |
| DP od góry z zapamiętywaniem | dużo stanów, potrzebna tylko część | liczy tylko to, co potrzebne |
| gramatyki | parser zstępujący | jedna metoda na regułę |

**Kiedy iteracja:**

| Sytuacja | Przykłady | Dlaczego |
|---|---|---|
| głębokość może być ~n | DFS na grafie 10⁵ wierzchołków, lista, DP po n krokach | `StackOverflowError` |
| proste akumulowanie | suma, Fibonacci, silnia, okno przesuwne | pętla jest tak samo czytelna |
| DP od dołu | gdy i tak potrzebny jest każdy stan | bez stosu, tabelę często da się zwinąć do O(1) |
| gorący kod wołany miliony razy | | narzut wywołań się sumuje |

1. **Pisz tak, jak najczytelniej.** Dla drzew i nawrotów to zwykle rekurencja.
2. **Sprawdź, jak głęboko zejdzie.** log n albo kilkaset poziomów: rekurencja jest w porządku. Jeśli
   może to być `n` przy `n ≥ 10⁴`: pętla albo jawny stos na `ArrayDeque` (dla bezpieczeństwa stosu,
   nie dla szybkości). Głębokość zależy od **kształtu danych w najgorszym przypadku**, nie od typowego:
   BST zbudowane z posortowanych kluczy, DFS po grafie-ścieżce i quicksort ze złym pivotem schodzą na
   `n`, a nie na log n. W ostateczności wątek z większym stosem:
   `new Thread(null, task, "deep", 512L << 20)`.
3. **Sprawdź, czy coś nie jest liczone dwa razy.** Jeśli tak, lekarstwem jest zapamiętywanie albo DP,
   nie pętla. To właśnie lekcja tej notatki: `naive(90)` blokuje liczba wywołań, nie głębokość.
4. **Szybkość mierz, nie zakładaj.** Prawdziwe powody, by przepisać rekurencję na pętlę, to stos i
   powtórzenia; JIT jest daleko na trzecim miejscu.

## Testy

Osiem testów, ćwierć sekundy i wzorzec na `BigInteger`, który buduje ciąg niezależnie, zamiast
porównywać jedną implementację z drugą: każde `n` od 0 do 92 dla wersji z zapamiętywaniem i
iteracyjnej i do 35 dla naiwnej, bo 35 to miejsce, w którym jej własna tabela wyżej każe się
zatrzymać.

Test, na którym opiera się argument, to **zapamiętywanie musi być prawdziwe**: tabela, z której nic
nie jest odczytywane, wciąż daje _poprawne_ wyniki, dokładnie te same liczby. Nie potrafi ich tylko
w ogóle zwrócić, więc poproszenie o `F(92)` w 30-sekundowym limicie klasy rozdziela te przypadki:
mikrosekundy wobec stuleci. Reszta: rekurencja sprawdzona względem siebie, granica 92 sprawdzona
względem `BigInteger` w obie strony, każda metoda odrzucająca liczby ujemne i 93 oraz 64 współbieżne
wywołania zgodne z odpowiedzią iteracyjną, bo oczywista „poprawka”, czyli przeniesienie tabeli do
pola statycznego, psułaby to sporadycznie.

### Sprawdzenie mutacjami

Dziesięć celowych uszkodzeń, dziesięć złapanych:

| Mutacja | Złapana przez |
|---|---|
| przypadek bazowy z zapamiętywaniem zwraca 1 dla `F(0)` | 4 błędy |
| pętla wykonuje o jeden krok za dużo | 4 błędy |
| pętla zwraca wartość o jeden dalej niż żądana | 4 błędy |
| naiwny przypadek bazowy zwraca 1 dla `F(0)` | 3 błędy |
| naiwna rekurencja sięga dwa razy o jedno miejsce wstecz | 3 błędy |
| deklarowana granica to 93 | 3 błędy, 2 przekroczenia czasu |
| nic nie zatrzymuje ujemnego `n` | 1 błąd |
| **nic nie zatrzymuje `n` powyżej 92** | **1 przekroczenie czasu**: naiwna metoda poproszona o `F(1 000)` nigdy nie wraca, żeby oblać asercję |
| **tabela jest czytana, ale nigdy zapisywana** | **5 przekroczeń czasu**, 2½ minuty |
| **tabela jest zapisywana, ale nigdy czytana** | **5 przekroczeń czasu**, 2½ minuty |

Dwa ostatnie wiersze to całe ćwiczenie w jednej linii. Żadna z tych mutacji nie zmienia ani jednej
zwracanej wartości (obie to _poprawne_ implementacje Fibonacciego) i żadnej nie wykryje żadna asercja
w pliku. Zmieniają to, czy odpowiedzi w ogóle przychodzą, a jedynym instrumentem, który to zgłasza,
jest zegar.

```
mvn test -Dtest=FibonacciTest
```
