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
