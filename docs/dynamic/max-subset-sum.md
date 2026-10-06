# Max Subset Sum: dlaczego to programowanie dynamiczne

Notatka do [`src/java/dynamic/MaxSubsetSum.java`](../../src/java/dynamic/MaxSubsetSum.java),
testy: [`test/java/dynamic/MaxSubsetSumTest.java`](../../test/java/dynamic/MaxSubsetSumTest.java).

**Zadanie.** Dana jest `int arr[n]`; wybierz podzbiór bez dwóch sąsiednich elementów o największej
sumie. Pusty podzbiór się liczy, więc odpowiedź nigdy nie jest ujemna: wejście z samymi liczbami
ujemnymi zwraca `0`.

## Rekurencja

```
best[i] = max( best[i - 1] , best[i - 2] + arr[i] )        best[-1] = best[-2] = 0
```

| Część | Znaczenie |
|---|---|
| **Stan** | `best[i]`: odpowiedź dla prefiksu `arr[0..i]`, mniejsza instancja tego samego zadania |
| **Przejście** | pomiń `arr[i]` i przenieś odpowiedź prefiksu albo weź go, co wyklucza `arr[i-1]` i zostawia prefiks kończący się na `i-2` |
| **Przypadek bazowy** | `0` dla obu pustych prefiksów: to wprowadza pusty podzbiór do gry |
| **Odpowiedź** | `best[n-1]` |

Przypadek bazowy `0` jest kluczowy. Sprawia, że stan jest monotonicznie niemalejący
(`best[i] = max(best[i-1], …) ≥ best[i-1] ≥ … ≥ 0`), więc odpowiedź nigdy nie wyjdzie ujemna, a
wejście z samymi ujemnymi nie wymaga osobnego przypadku. Składnik `best[i-2] + arr[i]` _może_ być
ujemny; `max`, który go odrzuca, to dokładnie decyzja „wzięcie tego elementu jest gorsze niż
pominięcie”:

```
[-2, 1, 3, -4, 5]
  i  wartość | best[i-2]+wartość   best[i-1] | best[i]  wzięty?
  0       -2 |                -2           0 |       0  pomiń    <- ujemny kandydat odrzucony
  1        1 |                 1           0 |       1  weź
  2        3 |                 3           1 |       3  weź
  3       -4 |                -3           3 |       3  pomiń    <- ujemny kandydat odrzucony
  4        5 |                 8           3 |       8  weź
```

## Warunek 1: optymalna podstruktura

Każdy poprawny podzbiór `arr[0..i]` należy do dokładnie jednego z dwóch przypadków, a w każdym
reszta jest sama optymalnym rozwiązaniem mniejszego prefiksu:

- **pomija `arr[i]`** → jest poprawnym podzbiorem `arr[0..i-1]`, a najlepszy taki to `best[i-1]`;
- **bierze `arr[i]`** → `arr[i-1]` jest wykluczony, więc reszta to poprawny podzbiór `arr[0..i-2]`,
  a najlepszy taki to `best[i-2]`.

Argument wymiany domyka sprawę: gdyby reszta w drugim przypadku nie była optymalna dla `arr[0..i-2]`,
podstawienie optimum tego prefiksu dałoby ściśle większą sumę, dalej bez sąsiadów, a to sprzeczność.
To _uprawnia_ rekurencję; bez tego byłaby zgadywaniem, które akurat przechodzi przykłady.

## Warunek 2: nakładające się podproblemy

`best[i]` potrzebuje `best[i-1]`, które potrzebuje `best[i-2]`, a tego potrzebuje też druga gałąź.
Zwykła rekurencja liczy te same prefiksy wykładniczo wiele razy. Zmierzona liczba wywołań dla
**identycznej** rekurencji, z zapamiętywaniem i bez (te same odpowiedzi, sprawdzone w każdym
wierszu):

| n | wywołania bez zapamiętywania | z zapamiętywaniem | wzrost |
|---:|---:|---:|---:|
| 10 | 287 | 21 | 14× |
| 20 | 35 421 | 41 | 864× |
| 30 | 4 356 617 | 61 | 71 420× |
| 36 | 78 176 337 | 73 | 1 070 909× |

Obie kolumny to dokładne wzory zamknięte, zgodne ze wszystkimi czterema wierszami:

- **z zapamiętywaniem** = `2n + 1`: każdy z _n_ różnych podproblemów liczony raz.
- **bez** = `2·Fib(n+2) − 1`, rośnie jak φⁿ (φ ≈ 1.618; stosunek 30→36 to 17.9 = φ⁶). `Fib(n+2)` to
  dokładnie liczba podzbiorów _n_ elementów bez sąsiadów, więc naiwna rekurencja to brutalne
  wyliczanie przebrane za rekurencję.

Zwinięcie Fibonacciego wielu podzbiorów do _n_ podproblemów to to, co czyni z tego programowanie
_dynamiczne_, a nie zwykłą rekurencję. Wykorzystanie tej różnicy **jest** tą techniką.

## Czym to nie jest

**Nie jest zachłanne.** Branie największego wciąż dozwolonego elementu nie jest bezpieczne: jeden
lokalny wybór może zablokować dwa lepsze:

| Wejście | zachłannie | DP |
|---|---:|---:|
| `[4, 5, 4]` | 5 | **8** |
| `[10, 11, 10, 11, 10]` | 22 | **30** |
| `[3, 5, -7, 8, 10]` | 15 | 15 (zachłanne ma szczęście) |

**Nie jest dziel i zwyciężaj.** D&C potrzebuje _rozłącznych_ podproblemów. Te nakładają się z samej
konstrukcji i to dokładnie rozwidlenie, na którym D&C staje się DP.

## Drabina złożoności

| Podejście | Czas | Pamięć | Uwaga |
|---|---|---|---|
| Wyliczenie podzbiorów (maska bitowa) | O(2ⁿ · n) | O(1) | wzorzec w testach dla n ≤ 12 |
| Naiwna rekurencja na tej rekurencji | O(φⁿ) | O(n) stosu | dobra rekurencja, bez ponownego użycia |
| Od góry + zapamiętywanie | O(n) | O(n) + O(n) stosu | **przepełnia stos około n ≈ 10⁴–10⁵** |
| Tabela od dołu | O(n) | O(n) | `tableDp` w teście, wzorzec dla n = 10⁷ |
| Od dołu, dwa wiersze | **O(n)** | **O(1)** | w `MaxSubsetSum` |

## Zwinięcie tabeli

W `MaxSubsetSum` nie ma tablicy `dp[]`, ale tabela nie zniknęła: przejście sięga najwyżej dwa
wiersze wstecz, więc żywe są tylko dwa. Prywatna klasa `Best` _jest_ tymi dwoma wierszami
(`forPrefixBeforeLast` = `best[i-2]`, `forPrefix` = `best[i-1]`). Usunięcie tabeli to optymalizacja
pamięci stosowana _po_ DP, nigdy zamiast niego, i jest tu możliwa tylko dlatego, że zasięg przejścia
jest ograniczony do 2. `tableDp` w teście to ten sam algorytm z jawną tablicą, dlatego działa jako
wzorzec: to nie inne podejście, tylko rozwinięta postać identycznego DP.

## Konsekwencje dla bardzo dużych zbiorów

Trzy własności tego DP mają znaczenie, gdy _n_ wychodzi poza 10⁵ z treści zadania:

1. **Pamięć O(1) oznacza, że wejście nie musi istnieć w całości.** Dwie liczby to cała tabela, więc
   `maxSubsetSumAsLong(IntStream)` przetwarza zbiór prosto z pliku, kursora albo generatora. Test
   przetwarza 2·10⁸ elementów (~800 MB jako inty) w stercie 512 MB, co jest możliwe tylko bez
   ich materializowania.
2. **`int` przepełnia się dużo wcześniej, niż algorytm zaczyna się męczyć.** 10⁷ losowych elementów
   z zakresu ±10 000 sumuje się do **18 631 707 043**, 8.7 razy ponad `Integer.MAX_VALUE`. Stan jest
   liczony w `long`; `maxSubsetSum(int[])` zachowuje sygnaturę z HackerRank i rzuca
   `ArithmeticException` przez `Math.toIntExact`, zamiast po cichu zawijać. Sprawdzenie tylko
   wyniku końcowego wystarcza, bo stan jest monotoniczny: jeśli odpowiedź się mieści, mieścił się
   każdy wynik pośredni. Przy danych typu `int` akumulator `long` przepełnia się po około 10¹⁵
   elementach.
3. **Od dołu nie ma głębokości stosu.** Postać od góry z zapamiętywaniem tej samej rekurencji
   pierwsza umiera na `StackOverflowError`; to pułapka, którą zastawia to zadanie.

Zmierzone jednowątkowo, z rozgrzanym JIT, JDK 17.0.17 (Temurin), Intel i7-14650HX:

| Obciążenie | Czas |
|---|---:|
| 10⁷ elementów, DP na dwóch wierszach | 4–8 ms |
| 10⁷ elementów, DP z tabelą O(n) | 17–21 ms |
| 10⁸ elementów, strumieniowo | 40–49 ms (~0.4 ns/element) |

Dalej: rekurencja to tropikalny iloczyn macierzy (max, +), który jest łączny, więc możliwe jest
równoległe skanowanie prefiksowe na wielu rdzeniach. Niezaimplementowane; jeden wątek to już około
2.5·10⁹ elementów na sekundę.

## Wariant, który warto znać

Gdyby wariant wymagał podzbioru **niepustego**, ten kod byłby błędny: `[-2, -4, -6, -1]` powinno dać
`-1` (najmniej złe pojedyncze wybranie), a nie `0`. To wymaga innych przypadków bazowych: znacznika
„jeszcze nic nie wybrano” równego `Long.MIN_VALUE / 2`, który wymusza pierwszy wybór, albo po prostu
`max(result, max(arr))`, gdy wynik to 0. Treść na HackerRank wprost dopuszcza pusty podzbiór, więc
kod pasuje do zadania tak, jak je podano.

## Wzorce w testach

Trzy niezależne sprawdzenia w trzech skalach, więc nic nie opiera się na tym, że implementacja
świadczy sama za siebie:

| Wzorzec | Skala | Co łapie |
|---|---|---|
| Brutalne wyliczenie maską bitową | 2 000 losowych tablic, n ≤ 12 | złą rekurencję |
| `tableDp` z pamięcią O(n) | 10⁷ losowych elementów | złe zwinięcie do dwóch wierszy |
| Odpowiedzi analityczne | 10⁷ naprzemiennych, 2·10⁸ strumieniowo | przepełnienie, kolejność, pamięć |

Sprawdzone mutacjami, każde celowe uszkodzenie złapane: dopuszczenie sąsiadów → 12 błędów i 1
wyjątek, ciche rzutowanie `(int)` → 2, stan `int` zamiast `long` → 5, `forEach` zamiast
`forEachOrdered` → dokładnie test ze strumieniem równoległym.

```
mvn test        # 24 testy, ~0.3 s
```

## Odtworzenie liczby wywołań

```java
static long calls;

static long naive(int[] a, int i) {                 // bez zapamiętywania: O(φⁿ)
    calls++;
    if (i < 0) {
        return 0;
    }
    return Math.max(naive(a, i - 1), naive(a, i - 2) + a[i]);
}

static long memo(int[] a, int i, long[] cache, boolean[] done) {   // ta sama rekurencja: O(n)
    calls++;
    if (i < 0) {
        return 0;
    }
    if (done[i]) {
        return cache[i];
    }
    done[i] = true;
    return cache[i] = Math.max(memo(a, i - 1, cache, done), memo(a, i - 2, cache, done) + a[i]);
}
```
