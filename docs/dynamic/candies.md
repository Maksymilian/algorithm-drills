# Candies: dwa kierunki złożone w jedno przejście

Notatka do [`src/java/dynamic/Candies.java`](../../src/java/dynamic/Candies.java),
testy: [`test/java/dynamic/CandiesTest.java`](../../test/java/dynamic/CandiesTest.java).

**Zadanie.** Dzieci stoją w rzędzie z ocenami `arr[n]`. Każde dostaje co najmniej jeden cukierek, a
dziecko ocenione **wyżej niż bezpośredni sąsiad** musi dostać ściśle więcej niż ten sąsiad.
Zminimalizuj sumę. Ograniczenia: `1 ≤ n ≤ 10⁵`, `1 ≤ arr[i] ≤ 10⁵`; odpowiedź jest zwracana jako
`long`, a powód (sekcja „Odpowiedź nie mieści się w `int`” niżej) to nie ozdoba.

Ograniczone jest tylko dziecko ocenione wyżej. Równe oceny nikogo nie ograniczają, a niższa ocena
niczego nie narzuca sąsiadowi; dlatego `[1, 2, 2]` kosztuje `1 + 2 + 1 = 4`, a nie 6.

## Rekurencja

Każda zasada to _dolna granica_ dla jednego dziecka, a granice dzielą się według kierunku:

```
L[i] = ratings[i] > ratings[i-1] ? L[i-1] + 1 : 1        L[0] = 1     od lewej do prawej
R[i] = ratings[i] > ratings[i+1] ? R[i+1] + 1 : 1        R[n-1] = 1   od prawej do lewej

candy[i] = max(L[i], R[i])                               odpowiedź = Σ candy[i]
```

| Część | Znaczenie |
|---|---|
| **Stan** | `L[i]`: odpowiedź dla `ratings[0..i]`, gdyby prawego sąsiada nie było; `R[i]` lustrzanie dla sufiksu |
| **Przejście** | dziecko ocenione wyżej niż poprzednik musi go przebić, więc bierze jego odpowiedź plus jeden; inaczej łańcuch się urywa i dziecko wraca do minimum |
| **Przypadek bazowy** | `1`: minimum należne każdemu dziecku i powód, dla którego urwany łańcuch zaczyna od nowa, a nie ciągnie się dalej |
| **Odpowiedź** | `Σ max(L[i], R[i])` |

Przykład 1, `[2, 4, 2, 6, 1, 7, 8, 9, 2, 1]` → 19:

```
 i        0  1  2  3  4  5  6  7  8  9
 ocena    2  4  2  6  1  7  8  9  2  1
 L        1  2  1  2  1  2  3  4  1  1     <- rośnie przy wzroście, inaczej wraca do 1
 R        1  2  1  2  1  1  1  3  2  1     <- ta sama zasada od tyłu
 candy    1  2  1  2  1  2  3  4  2  1     = 19
                                 ^  ^
                     L niesie wzrost 1,7,8,9 na indeksach 4..7; tylko R wie,
                     że indeks 8 zaczyna spadek i nie może zostać z 1.
```

Potrzebne są oba kierunki, a każdy sam jest ślepy: samo `L` niedokarmia każdego spadku, samo `R`
każdego wzrostu.

## Dlaczego maksimum jest poprawne

To krok, który trzeba uzasadnić; reszta to księgowość.

**Jest dozwolone.** Weź wzrost, `ratings[i] > ratings[i-1]`. Wtedy `L[i] = L[i-1] + 1`, a
`R[i-1] = 1`, bo `ratings[i-1] > ratings[i]` jest fałszem, więc:

```
candy[i] ≥ L[i] = L[i-1] + 1 > max(L[i-1], R[i-1]) = candy[i-1]        (R[i-1] = 1 ≤ L[i-1])
```

Spadek to lustrzane odbicie, przez `R`. Równi sąsiedzi niczego nie ograniczają. Każda zasada jest
więc spełniona.

**Jest minimalne.** Niech `c` będzie _dowolnym_ poprawnym rozdaniem. Wtedy `c[i] ≥ L[i]` przez
indukcję wzdłuż wzrostu: `c[i] ≥ 1 = L[i]` tam, gdzie łańcuch się urywa, i `c[i] > c[i-1] ≥ L[i-1]`
tam, gdzie się nie urywa, więc `c[i] ≥ L[i-1] + 1 = L[i]`. Symetrycznie `c[i] ≥ R[i]`, stąd
`c[i] ≥ max(L[i], R[i])` na każdym indeksie. Poprawne rozdanie, które spełnia każde z tych ograniczeń
dokładnie, jest więc _tym_ najtańszym, a poprzedni akapit pokazał, że to rozdanie jest poprawne.
Argument wymiany nie jest potrzebny: minimum jest wymuszone punktowo, a nie tylko w sumie.

## DP pod spodem: najdłuższy łańcuch w grafie ograniczeń

Narysuj krawędź `j → i` dla każdej sąsiedniej pary, w której `ratings[i] > ratings[j]`, czytaną jako
„`i` musi przebić `j`”. Zasady to wtedy dokładnie „`candy[i]` przewyższa każdego poprzednika”, więc

```
candy[i] = 1 + (długość najdłuższego łańcucha krawędzi kończącego się w i)
```

czyli najdłuższa ścieżka w DAG, kanoniczny kształt programowania dynamicznego. Oceny ściśle rosną
wzdłuż każdej krawędzi, więc żaden łańcuch nie wraca do indeksu; na prostej łańcuch, który nie wraca,
to ciągły odcinek w jednym kierunku. **Dlatego wystarczą dwa przejścia prefiksowe**: łańcuch nie może
zawrócić, więc jest w całości w lewo albo w całości w prawo, a `L` i `R` mierzą dokładnie te dwa.

Nakładanie się jest zwykłe: łańcuch kończący się w `i` zawiera łańcuch kończący się w `i-1`. Liczone od
nowa dla każdego indeksu przejście to Θ(n²) na monotonicznym rzędzie; zapamiętanie jednej liczby na
indeks daje Θ(n).

## Czym to nie jest

**Nie jest jednym zachłannym przejściem.** Przejście od lewej, dające `poprzednik + 1` przy wzroście,
a `1` w przeciwnym razie, to dokładnie `L` i jest błędne wszędzie, gdzie rząd spada:

| Wejście | jedno przejście do przodu | odpowiedź |
|---|---|---|
| `[3, 2, 1]` | `[1, 1, 1]` = 3 | `[3, 2, 1]` = 6 |
| `[1, 5, 4, 3, 2, 1]` | `[1, 2, 1, 1, 1, 1]` = 7 | `[1, 5, 4, 3, 2, 1]` = 16 |

**To nie „minima lokalne dostają 1”.** Dziecku może należeć się więcej niż minimum, choć nie jest
szczytem: `[3, 2, 2, 1]` → `[2, 1, 2, 1]`. Druga `2` nie jest ani maksimum lokalnym, ani wzrostem,
ale zaczyna spadek, więc `R` ją podnosi.

**Sortowanie nie jest użyteczne**, choć daje poprawny wynik: przetwarzaj indeksy według ocen i
ustaw `candy[i] = 1 + max` po już przydzielonych sąsiadach z niższą oceną. Każdy ściśle niższy sąsiad
jest wtedy już ustalony, więc wychodzi to samo rozdanie, ale w O(n log n), a sortowanie to jedyny
powód, dla którego nie jest liniowe.

## Tabela zapamiętywania

Zapisane tak, jak czyta się rekurencję, dwie tabele są trzymane i sumowane, czyli
`candiesWithTable(int[])`:

```java
for (int i = 0; i < n; i++)      left[i]  = i > 0     && r[i] > r[i - 1] ? left[i - 1] + 1  : 1;
for (int i = n - 1; i >= 0; i--) right[i] = i < n - 1 && r[i] > r[i + 1] ? right[i + 1] + 1 : 1;
for (int i = 0; i < n; i++)      total   += Math.max(left[i], right[i]);
```

Czas Θ(n), pamięć O(n): dwa `int[]` długości rzędu. To postać do _czytania_: przepisana rekurencja,
a poszczególne przydziały przetrwają wywołanie, podczas gdy wersja zwinięta je odrzuca.

**Ile warta jest tabela, a ile nie.** Tabele zamieniają Θ(n²) w Θ(n): bez nich `L[i]` i `R[i]` trzeba
liczyć od nowa, idąc od `i` wstecz wzdłuż ich odcinków, a na monotonicznym rzędzie każde takie
przeliczenie ma pełną długość. W pomiarach (sekcja „Pomiary” niżej) to 575 ms wobec 0.25 ms przy
n = 80 000.

Ale to _wszystko_, co daje tu zapamiętywanie, i warto powiedzieć dokładnie dlaczego. Klasyczny obraz
zapamiętywania to Fibonacci: jeden podproblem potrzebny w wielu miejscach, a tabela zwija
wykładnicze drzewo do `n` wpisów. Nic takiego nie dzieje się w tej rekurencji. Każdy wpis ma dokładnie
jednego zależnego (`L[i]` zasila tylko `L[i+1]`, `R[i]` tylko `R[i-1]`), więc graf zależności to dwa
łańcuchy, a nie drzewo, i żaden wpis nie jest _potrzebny_ dwa razy. Tabela usuwa nie powtarzane
odczyty, tylko powtarzane **wyprowadzanie**: przechodzenie łańcucha od nowa dla każdego indeksu. To
różnica między tym ćwiczeniem a [Max Subset Sum](max-subset-sum.md), gdzie rekurencja się rozgałęzia,
a zapamiętywanie jest warte wykładniczo, a nie czynnik `n`.

I dlatego tabela może zniknąć. Okno zależności szerokości jednego indeksu nie potrzebuje tablicy.

## Zwinięcie tabeli

Tabela ma O(n) wpisów, ale żywych jest naraz tylko O(1): `R` wypełnia się od prawej, więc nie da się
go policzyć w tym samym przejściu do przodu co `L`. Nie trzeba go jednak _przechowywać_, tylko jego
wkład do sumy, a `R[i] > L[i]` **tylko w środku malejącego odcinka**. Idziemy więc po spadku i płacimy
na bieżąco:

- przedłużenie spadku do długości `d` przenumerowuje dzieci już w nim (każde potrzebuje jednego
  więcej), a nowe dostaje 1. Razem to `d` dodatkowych cukierków, różnica dwóch liczb trójkątnych,
  `d(d+1)/2 − (d−1)d/2`;
- szczyt, z którego schodzi spadek, potrzebuje `d + 1`, ale ma już `ascent + 1` ze wzrostu, więc
  kosztuje **jeden więcej dopiero w chwili, gdy spadek przerośnie ten wzrost**, i potem już nic.

Wystarczy pięć liczb: poprzednia ocena, bieżący `ascent`, bieżący `descent`, `peakAscent`, z którego
zaczął się spadek, i suma bieżąca. `[1, 5, 4, 3, 2, 1]`:

```
 i  ocena  krok      ascent descent peakAscent | delta   suma   rozdanie do tej pory
 0      1  pierwszy       0       0          0 |    +1      1   [1]
 1      5  wzrost         1       0          1 |    +2      3   [1,2]
 2      4  spadek         0       1          1 |    +1      4   [1,2,1]      szczyt wciąż dość wysoki
 3      3  spadek         0       2          1 |    +3      7   [1,3,2,1]    spadek go przerósł: +1
 4      2  spadek         0       3          1 |    +4     11   [1,4,3,2,1]
 5      1  spadek         0       4          1 |    +5     16   [1,5,4,3,2,1]
```

Płaskowyż zeruje wszystkie trzy liczniki: równi sąsiedzi nie ograniczają żadnego z dzieci, więc
odcinek nie może przez niego przechodzić. Ta jedna linia trzyma `[3, 2, 2, 1]` na 6: spadek zaczyna
się od nowa po płaskowyżu, zamiast przez niego przechodzić, co policzyłoby go jako spadek o czterech
krokach i kosztowało 10.

## Drabina złożoności

| Podejście | Czas | Pamięć | |
|---|---|---|---|
| Przeszukanie każdego rozdania w `{1..n}ⁿ` | Θ(nⁿ) | O(n) | wzorzec w testach, `n ≤ 6` |
| Start od jedynek, naprawianie naruszeń aż do stabilności | O(n²) | O(n) | ten sam najmniejszy punkt stały, osiągany przejściami |
| Sortowanie według ocen, przydział po kolei | O(n log n) | O(n) | poprawne; cały koszt to sortowanie |
| Liczenie każdego odcinka od nowa, bez pamięci | Θ(n²) | O(1) | to usuwa tabela |
| Dwa przejścia, `L` i `R` w tablicach | Θ(n) | O(n) | `candiesWithTable`: postać czytelna |
| **Jedno przejście, same liczniki** | **Θ(n)** | **O(1)** | **`candies`**: ta sama tabela, zwinięta |

Θ(n) to optimum: zmiana jednej oceny może zmienić sumę, więc trzeba przeczytać każdą ocenę.

## Odpowiedź nie mieści się w `int`

Ograniczenie `n ≤ 10⁵` już przekracza `Integer.MAX_VALUE`. Ściśle rosnący rząd 100 000 dzieci kosztuje
`100000·100001/2 = 5 000 050 000` cukierków, 2.3 razy więcej niż mieści `int`. Sygnatura z HackerRank
zwraca z tego powodu `long`, a akumulator `int` po cichu zawinąłby się do `705 082 704`. Przydziały
dla pojedynczych dzieci są małe (najwyżej `n`); przepełnia się tylko suma.

## Pomiary

Nie odtwarzane przy budowaniu: zmierzone raz na tej maszynie, JDK 25, `-Xmx512m`, po rozgrzaniu.

**Co daje tabela**, na najgorszym przypadku dla wersji bez niej: jeden rosnący odcinek, więc każde
przeliczenie ma pełną długość. Dwie pierwsze kolumny to ta sama rekurencja z zapamiętywaniem i bez
(po jednym pomiarze, więc kolumny poniżej milisekundy to głównie dokładność zegara):

| n (jeden rosnący odcinek) | bez tabeli, liczenie od nowa | `candiesWithTable` | `candies` |
|---:|---:|---:|---:|
| 20 000 | 42.0 ms | 0.15 ms | 0.27 ms |
| 40 000 | 143.3 ms | 0.37 ms | 0.07 ms |
| 80 000 | 574.8 ms | 0.25 ms | 0.03 ms |

Pierwsza kolumna rośnie czterokrotnie przy podwojeniu: Θ(n²), najczyściej w dwóch ostatnich wierszach,
4.01 razy.

**Co daje porzucenie tabeli**, gdy obie wersje są liniowe (najlepszy z 9):

| n = 10 000 000, błądzenie losowe | Czas | Pamięć tabel |
|---|---:|---:|
| `candiesWithTable` | 108 ms | 80 MB |
| `candies` | 42 ms | 0 |

**2.5 razy**, z jednego przejścia po 40 MB wejścia zamiast trzech przejść przenoszących 240 MB między
RAM a dwiema tabelami: rekurencja jest ograniczona przepustowością pamięci, więc tabela, której
wersja zwinięta nie alokuje, to też czas, którego nie wydaje. A ponieważ nie trzyma stanu
proporcjonalnego do `n`, rząd można wycenić prosto ze strumienia: 200 000 000 dzieci w 97 ms, na
stercie, która w ogóle nie zmieściłaby tablicy wejściowej (762 MB).

## Wzorce w testach

Trzy niezależne sprawdzenia, jak wszędzie w tym repozytorium:

- **wyczerpujące**: każdy rząd do 6 dzieci jest wyceniany przez przeszukanie całego `{1..n}ⁿ` w
  poszukiwaniu najtańszego poprawnego rozdania. Żadne minimalne rozdanie nie daje dziecku więcej niż
  `n` (żaden łańcuch nie jest dłuższy niż rząd), więc przeszukanie jest pełne i sprawdza _treść
  zadania_, a nie przeformułowanie rekurencji. Zasilają je dwa wyliczenia: wszystkie rzędy z 3
  wartości ocen oraz, ponieważ z trzech wartości nie zbuduje się odcinka dłuższego niż trzy, jeden
  przedstawiciel każdego z `3ⁿ⁻¹` kształtów wzrost/płasko/spadek, i tam długie odcinki oraz zasada
  szczytu dostają wyczerpujące sprawdzenie;
- **tabela z dwóch przejść**: ta sama rekurencja z `L` i `R` naprawdę zapisanymi, na 40 000 losowych
  rzędach i dwa razy przy 10 000 000 (jednostajnie i jako błądzenie ±1, żeby odcinki były długie, a
  księgowość szczytu pod obciążeniem). Nieprzechowywanie `R` to jedyny krok, który mógłby się
  zepsuć w skali. Test trzyma własną kopię tej wersji zamiast wołać `candiesWithTable`, żeby wzorzec
  był niezależny od testowanego kodu; obie postacie z kodu są potem sprawdzane względem niego i
  względem siebie;
- **wzory zamknięte**: ściśle rosnący rząd `n` dzieci kosztuje `n(n+1)/2`, sprawdzone przy 10⁵ dla
  granicy `long` i przy 2·10⁸ przez strumień, gdzie samo przejście testu jest dowodem, że stan
  naprawdę zajmuje O(1).

## Zobacz też

- [Max Subset Sum](max-subset-sum.md): ten sam ruch „zwiń tabelę do kilku zmiennych”, tam dlatego,
  że przejście sięga tylko dwa stany wstecz, tutaj dlatego, że jeden z dwóch kierunków da się spłacić
  w trakcie przechodzenia.
