# Abbreviation: DP i na co idzie jego czas

Notatka do [`src/java/dynamic/Abbreviation.java`](../../src/java/dynamic/Abbreviation.java),
testy: [`test/java/dynamic/AbbreviationTest.java`](../../test/java/dynamic/AbbreviationTest.java).

**Zadanie.** Dane są `a` (litery małe i wielkie) i `b` (wielkie litery). Rozstrzygnij, czy `a` da się
zamienić w `b`, zamieniając zero lub więcej małych liter na wielkie i usuwając wszystkie pozostałe
małe litery. Ograniczenia: `q ≤ 10`, `|a|, |b| ≤ 1000`.

Cała trudność to asymetria między dwoma rodzajami liter. Mała litera ma wybór (zamień ją na wielką
i zużyj na `b` albo usuń), a **wielka litera wyboru nie ma**: żadna operacja jej nie usuwa, więc
każda wielka litera `a` musi zostać dopasowana, po kolei.

## Rekurencja

```
c mała:   reachable[i][j] = reachable[i-1][j]                     // usuń c
                          | reachable[i-1][j-1] && upper(c) == d  // zamień c na wielką
c wielka: reachable[i][j] = reachable[i-1][j-1] && c == d         // c trzeba zużyć
```

| Część | Znaczenie |
|---|---|
| **Stan** | `reachable[i][j]`: pierwsze `i` liter `a` może dać pierwsze `j` liter `b` |
| **Przejście** | `c = a[i-1]`, `d = b[j-1]`; mała `c` zostawia obie możliwości, wielka ma jedną |
| **Przypadek bazowy** | `reachable[i][0]` jest prawdą tylko, gdy `a[0..i-1]` to same małe litery; `reachable[0][j>0]` to fałsz |
| **Odpowiedź** | `reachable[n][m]` |

**Dlaczego nie zachłannie.** `a = "aA"`, `b = "A"`. Branie pierwszej pasującej litery zamienia
`'a'` na wielką i zostawia `'A'` bez pary → NIE; odpowiedź to TAK, przez _usunięcie_ `'a'` i
dopasowanie `'A'`. Czy zużycie małej litery się opłaci, zależy od całej reszty napisu, więc obie
gałęzie muszą zostać przy życiu. Po to jest tabela.

## Wydajność

### Dlaczego Θ(n·m)

Przestrzeń stanów to iloczyn długości obu prefiksów, `(n+1)(m+1)` komórek, a każde przejście czyta
najwyżej dwóch sąsiadów i robi O(1) pracy na znakach. Ani więcej, ani mniej:

```
Θ(n·m + n)      czas        n·m komórek plus O(1) na wiersz dla charAt / isLowerCase / toUpperCase
Θ(min(n, m))    pamięć      jeden wiersz tabeli
```

Składnik `+ n` to nie pedanteria: przy `m = 2` praca na wiersz jest porównywalna z samym wierszem,
dlatego zmierzony koszt na komórkę mocno rośnie dla bardzo krótkich celów (tabela niżej).

### Nie ma najlepszego przypadku

To DP jest **niezależne od danych**: każda komórka jest liczona dla każdego wejścia, bez wczesnego
wyjścia i bez rozgałęzień na granicach pętli zależnych od danych. Najlepszy, średni i najgorszy
przypadek to Θ(n·m). To nietypowe i warto to nazwać, bo jedyne drogi do przyspieszenia to (a)
odrzucenie zapytania przed pętlą albo (b) zmniejszenie stałej na komórkę. Obie są opisane niżej.

### Zmierzone skalowanie

`|a| = |b| = k`, alfabet `{a,b}` wobec `{A,B}`, najlepszy z 5 rozgrzanych przebiegów, jeden wątek,
JDK 17.0.17 (Temurin), Intel i7-14650HX:

| \|a\| | \|b\| | komórki | czas | wobec poprzedniego | ns/komórkę |
|---:|---:|---:|---:|---:|---:|
| 1 250 | 1 250 | 1 562 500 | 0.56 ms | — | 0.359 |
| 2 500 | 2 500 | 6 250 000 | 2.20 ms | 3.92× | 0.352 |
| 5 000 | 5 000 | 25 000 000 | 8.90 ms | 4.05× | 0.356 |
| 10 000 | 10 000 | 100 000 000 | 35.07 ms | 3.94× | 0.351 |
| 20 000 | 20 000 | 400 000 000 | 139.54 ms | 3.98× | 0.349 |

Podwojenie obu napisów mnoży czas przez 3.92–4.05, a koszt komórki trzyma się w granicach 3%. To
podręcznikowe Θ(n·m): kwadratowy jest _iloczyn_, a nie któraś z długości.

### Liczy się iloczyn, a nie długości

| \|a\| | \|b\| | komórki | czas | uwaga |
|---:|---:|---:|---:|---|
| 1 000 000 | 2 | 2 000 000 | 1.84 ms | 0.92 ns/komórkę: dominuje składnik `+ n` |
| 1 000 000 | 20 | 20 000 000 | 17.64 ms | 0.88 ns/komórkę: wciąż narzut na wiersz |
| 100 000 | 200 | 20 000 000 | 8.44 ms | 0.42 ns/komórkę |
| 10 000 | 2 000 | 20 000 000 | 7.16 ms | 0.36 ns/komórkę: pętla wewnętrzna dość długa, żeby się zamortyzować |
| 2 000 | 10 000 | — | 0.00 ms | szybka ścieżka `n < m`: odrzucone bez dotykania tabeli |

Milionowe `a` wobec dwuliterowego `b` jest tańsze niż 10 000 × 2 000, choć `a` jest 100 razy dłuższe.
Liczy się tylko `n·m`.

### Pamięć: O(min(n, m))

Przejście czyta tylko wiersz `i - 1`, więc przetrwać musi jeden wiersz: `m + 1` wartości `boolean`,
około 10 KB przy `m = 10⁴`, wobec około 100 MB na pełne `boolean[n+1][m+1]`. Wiersz jest
aktualizowany **malejąco po `j`**, żeby `reachable[j]` i `reachable[j-1]` przy odczycie były jeszcze
wierszem `i - 1`; rosnąco najpierw by je nadpisało, a to klasyczny błąd w tej rodzinie zadań
(sprawdzone mutacją: 8 błędów).

Orientacja ma znaczenie dla ograniczenia, nie dla odpowiedzi. Zewnętrzna pętla po `j` z _kolumną_
`n + 1` wartości też działa (z jedną przenoszoną zmienną na przekątną) i daje pamięć O(n). Szybka
ścieżka `n < m` gwarantuje `m ≤ n`, więc wiersz użyty tutaj to już mniejszy z dwóch wymiarów, a
pamięć to w obecnej postaci O(min(n, m)).

### Drabina złożoności

`L` = liczba małych liter w `a`, `w` = słowo maszynowe (64).

| Podejście | Czas | Pamięć | Przy n = m = 10⁴ |
|---|---|---|---|
| Wyliczenie zamian na wielkie (wzorzec w testach) | O(2^L · n) | O(n) | 2^5000: beznadziejne |
| Rekurencja bez zapamiętywania | O(2^L) | O(n+m) stosu | beznadziejne |
| Od góry + zapamiętywanie | O(n·m) | O(n·m) + O(n+m) stosu | 100 MB + głęboki stos |
| Tabela od dołu | O(n·m) | O(n·m) | 100 MB |
| **Od dołu, jeden wiersz** | **Θ(n·m + n)** | **O(min(n,m))** | **35 ms, 10 KB** |
| Wiersz równoległy bitowo | Θ(n·m/w + n) | O(m/w) | ~64 razy mniej operacji na słowach |

Postać równoległa bitowo to jedyny prawdziwy zysk w stałej, jaki został. Spakuj wiersz do `long[]`, a
całe przejście staje się dwiema operacjami na słowie na 64 kolumny:

```
row_i = ((row_{i-1} << 1) & match[c])  |  (droppable ? row_{i-1} : 0)
```

gdzie `match[c]` ma ustawiony bit `j` wtedy i tylko wtedy, gdy `b[j-1] == upper(c)`: tabela 26
wpisów budowana raz w O(m·26/w). Ta sama klasa Θ(n·m/w) co bitowo-równoległe LCS. Niezaimplementowane:
0.35 ns na komórkę to już dość szybko, a to ćwiczenie dotyczy rekurencji, nie sztuczek na słowach.

### Czy kwadratowo to optimum?

Nie wiadomo i warto to powiedzieć precyzyjnie. Pokrewne DP dla LCS i odległości edycyjnej mają
warunkowe dolne ograniczenia oparte na SETH, wykluczające algorytmy silnie podkwadratowe, ale to nie
jest dowód dla _tego_ zadania i nie twierdzę tu, że istnieje redukcja. Jest prawdziwa struktura do
wykorzystania: wielkie litery `a` to wymuszone kotwice, które muszą pojawić się w `b` po kolei, a
między kolejnymi kotwicami ciąg małych liter może dać dowolny swój podciąg, co jest warunkiem
domkniętym na prefiksy, więc szybszy algorytm nie jest oczywiście niemożliwy. DP go nie potrzebuje:
przy podanych ograniczeniach zapytanie to 10⁶ komórek, czyli około 0.4 ms.

## Filtry wstępne: poprawne, tanie i nie zmieniają ograniczenia

Dwa warunki konieczne liczone w O(n+m) mogą odrzucić zapytanie, zanim dotkniemy tabeli:

- **liczności**: `count_b(X) ≤ count_a(X) + count_a(x)` dla każdej litery, bez rozróżniania wielkości;
- **podciąg**: wielkie litery `a` muszą występować w `b`, po kolei.

Zmierzone przy 10⁴ × 10⁴ (pojedynczy przebieg, rozgrzany):

| Przypadek | DP | liczności | podciąg | który odrzuca? |
|---|---:|---:|---:|---|
| TAK | 98 ms | 0.38 ms | 0.18 ms | żaden |
| łatwe NIE: zabłąkane `Z` w `a` | 39 ms | 0.22 ms | 0.32 ms | **podciąg** |
| trudne NIE: dobre litery, zła kolejność | 39 ms | 0.44 ms | 0.18 ms | żaden |

Około 0.5% narzutu i około 100 razy oszczędności, gdy filtr zadziała. Ale filtr **nigdy** nie
zadziała na TAK (tam żaden warunek konieczny nie zawodzi) ani na kosztownych NIE: wejścia, na
których DP mieli najdłużej, to dokładnie te, w których litery się zgadzają. **Najgorszy przypadek
zostaje Θ(n·m).** Pokrycie spada też, gdy alfabet się zawęża, czyli wtedy, gdy NIE przestaje być
oczywiste:

| Losowe wejścia | faktycznie NIE | liczności odrzucają | podciąg odrzuca |
|---|---:|---:|---:|
| alfabet 26 liter | 99.4% | 95.4% | 77.2% |
| 4 litery | 94.7% | 59.6% | 59.6% |
| 2 litery | 82.0% | 37.2% | 42.5% |

Żaden filtr nie trafił do kodu. Dwie pułapki sprawiają, że kosztują więcej, niż się wydaje:
`b.charAt(j) - 'A'` to 32 dla małej litery, więc naiwny filtr liczności rzuca
`ArrayIndexOutOfBoundsException` tam, gdzie DP poprawnie odpowiada NIE (testy to łapią), a
porównanie liter _z rozróżnianiem wielkości_ błędnie odrzuca `a = "abc"`, `b = "ABC"`, czyli TAK bez
ani jednej wielkiej litery w `a`. Linia `n < m` przechodzi tę poprzeczkę, bo to jedno porównanie, w
którym nie da się pomylić; jej usunięcie zostawia testy zielone, co potwierdza, że to czysta
szybka ścieżka.

## Wzorce w testach

| Wzorzec | Skala | Co łapie |
|---|---|---|
| Brutalne wyliczenie 2^L zamian na wielkie | 5 000 losowych zapytań, \|a\| ≤ 10 | złą rekurencję |
| Tabela z pamięcią O(n·m), bez szybkiej ścieżki | \|a\| = 2 000, osiągalne cele | złe zwinięcie do jednego wiersza |
| Analitycznie | 10⁴ × 10⁴ oraz 10⁶ × 2 | skalę i reżim `+ n` |

21 znanych zapytań jest sprawdzanych względem wszystkich trzech naraz. Losowe porównanie sprawdza,
że znalazło ponad 500 osiągalnych przypadków, więc nie może się zdegenerować do porównywania NIE z
NIE. Duże cele są z konstrukcji osiągalne (zostaw każdą wielką, zamień część małych), a potem
`+ "Z"` daje pewne NIE: żadna litera napisu z `{a,b,A,B}` nie da `Z`.

Sprawdzone mutacjami, wszystkie złapane: wielka litera usuwalna → 4 błędy; zapomnienie, że wielkiej
litery nie można zostawić niezużytej → 3; pętla wewnętrzna rosnąco → 8; odwrócony test `droppable`
→ 13. Usunięcie szybkiej ścieżki `n < m` → nadal zielono, tak jak powinno.

```
mvn test        # 51 testów w obu ćwiczeniach, ~1 s
```

## Zobacz też

- [Max Subset Sum: dlaczego to programowanie dynamiczne](max-subset-sum.md): przypadek 1D i pełny
  argument o optymalnej podstrukturze i nakładających się podproblemach.
