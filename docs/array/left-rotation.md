# Left Rotation: jedna mapa indeksów i dlaczego najmniej zapisów przegrywa

Notatka do [`src/java/array/LeftRotation.java`](../../src/java/array/LeftRotation.java),
testy: [`test/java/array/LeftRotationTest.java`](../../test/java/array/LeftRotationTest.java).

**Zadanie.** Dane są `int a[n]` i `d`; wykonaj `d` obrotów `a` w lewo. Przykład: `[1,2,3,4,5]` z
`d = 4` → `[5,1,2,3,4]`. Ograniczenia: `1 ≤ n ≤ 10⁵`, `1 ≤ d ≤ n`.

## Pułapka jest w sformułowaniu

„Wykonaj `d` obrotów w lewo” opisuje pętlę, a zapisane jako pętla kosztuje O(n·d). Przy górnej
granicy zadania to 10¹⁰ przesunięć elementów. Zmierzone dla `n = d = 100 000`:

| Podejście | Czas |
|---|---:|
| `d` obrotów o jedną pozycję | 683 ms |
| Jedna mapa indeksów (`rotLeft`) | 68 µs |

**Około 10 000 razy**, a różnica jest kwadratowa: rośnie z każdym elementem. Złożenie `d`
pojedynczych obrotów to jednak wcale nie pętla, tylko jedno przenumerowanie:

```
rotated[i] = a[(i + d) mod n]
```

Każdy element ma dokładnie jedno miejsce docelowe, znane w postaci zamkniętej, więc niczego nie
trzeba ruszać dwa razy i żaden obrót nie musi kosztować więcej niż O(n). Wszystko dalej to ten
jeden wzór, liczony w innej kolejności.

## Normalizacja d

Wzór potrzebuje tylko `d mod n`, dlatego `Math.floorMod(d, n)` daje za darmo trzy przypadki, o które
zadanie nie pyta:

| `d` | `floorMod(d, n)` | znaczenie |
|---|---|---|
| `0` albo `n` | `0` | nic się nie zmienia |
| `> n` | zawija się | `d = 17, n = 5` → przesunięcie 2 |
| **ujemne** | zawija się w drugą stronę | **obrót w prawo** |

`Integer.MIN_VALUE` też się mieści, a to wartość, na której wykłada się ręczna normalizacja: `-d`
przepełnia się z powrotem do `Integer.MIN_VALUE`. Dlatego wzorzec z `Collections.rotate` w testach
musi normalizować _przed_ zmianą znaku. Osobno obsługiwane jest tylko `n = 0`, bo `floorMod(d, 0)`
rzuca wyjątek: nie ma modułu, przez który można by dzielić.

## Trzy kolejności liczenia

| | Dodatkowa pamięć | Zapisy elementów | Dostęp do pamięci |
|---|---|---|---|
| `rotLeft` (kopia) | O(n) | n | dwa ciągłe bloki |
| `rotateLeftInPlace` (3 odwrócenia) | **O(1)** | ~2n | sekwencyjny |
| `rotateLeftInPlaceByCycles` (żonglowanie) | **O(1)** | **n** | skoki co `d` |

**Kopia.** `rotated[i] = a[(i + d) mod n]` mówi, że ogon `a[d..n)` trafia na początek, a głowa
`a[0..d)` za nim: dwa ciągłe bloki, więc dwa wywołania `System.arraycopy`, a nie n operacji modulo.

**Trzy odwrócenia.** Obrót w lewo daje `ogon ++ głowa`. Odwrócenie każdej części, a potem całej
tablicy wykonuje tę zamianę w miejscu, bo odwrócenie odwróconego bloku go przywraca:

```
[1 2 3 4 5]  d = 4
 reverse(0,4)   [4 3 2 1 | 5]
 reverse(4,5)   [4 3 2 1 | 5]
 reverse(0,5)   [5 | 1 2 3 4]
```

**Cykle (żonglowanie).** `i → (i + d) mod n` jest permutacją, więc każdy element można zanieść
prosto na miejsce: trzymaj jeden, ściągnij jego następcę z pola o `d` dalej, powtarzaj, aż
przejście się domknie, wstaw trzymaną wartość w dziurę. Cykli jest dokładnie **`gcd(n, d)`**, każdy
długości `n / gcd(n, d)` (krok `+d` pierwszy raz wraca na start po `lcm(n, d)` krokach), dlatego
jedno przejście nie zawsze wystarcza. Gdy `n` i `d` są względnie pierwsze, jeden cykl obejmuje
wszystko, i **akurat taki jest przykład z HackerRank** (`n = 5, d = 4`), więc wersja, która nigdy
nie zaczyna przejścia od nowa, i tak go przechodzi. Najmniejszy przypadek, który to łapie, to
`n = 4, d = 2`: przejście bez ponownego startu zostawia `[1,2,3,4]` jako `[3,2,1,4]` zamiast
`[3,4,1,2]`.

## Najmniej zapisów to nie najszybciej

Przejście po cyklach zapisuje każdy element dokładnie raz: to udowodnione minimum, połowa tego, co
robią odwrócenia. I właśnie jego należy unikać. Zmierzone doraźnie, `n = 50 000 000` intów
(200 MB), najlepszy z 3 pomiarów, JDK 25.0.1 (Temurin), Intel i7-14650HX (L2 24 MB, L3 30 MB):

| `d` | potęga 2 | 3 odwrócenia | cykle | stosunek |
|---:|:---:|---:|---:|---:|
| 1 | ✓ | 0.50 ns/el | 0.44 ns/el | 0.9× |
| 3 | | 0.52 | 0.78 | 1.5× |
| 7 | | 0.51 | 1.63 | 3.2× |
| 15 | | 0.50 | 3.22 | 6.4× |
| 100 | | 0.50 | 4.45 | 8.9× |
| 1 000 | | 0.50 | 1.60 | 3.2× |
| **1 024** | **✓** | 0.51 | **11.33** | **22×** |
| 12 345 | | 0.51 | 6.82 | 13.4× |
| **65 536** | **✓** | 0.50 | **23.52** | **47×** |
| 70 000 | | 0.51 | 1.08 | 2.1× |
| **1 048 576** | **✓** | 0.51 | **28.88** | **56×** |
| 3 333 333 | | 0.51 | 0.63 | 1.2× |
| 25 000 000 | | 0.51 | 0.52 | 1.0× |

Dwie kolumny, jedna tablica, ta sama liczba instrukcji. **Odwrócenia są płaskie: 0.50–0.52 ns na
element przez siedem rzędów wielkości `d`. Cykle rozciągają się od 0.44 do 28.88, czyli 65 razy,
a decyduje o tym tylko wartość `d`.**

Skąd ten rozrzut, w trzech zakresach:

- **`d` mniejsze niż linia pamięci podręcznej (16 intów).** Przejście okrąża zakres adresów `d`
  razy, a przy skoku poniżej 64 bajtów każde okrążenie dotyka każdej linii, więc cała tablica jest
  wczytywana `d` razy. Koszt rośnie jak `d/2`: `d = 7` → 3.2×, `d = 15` → 6.4×.
- **Potęgi dwójki.** `1 024`, `65 536` i `1 048 576` kosztują 22×, 47× i 56×, a ich bezpośredni
  sąsiedzi `1 000` i `70 000` 3.2× i 2.1×. Skok o potęgę dwójki trafia każdym dostępem w te same
  zbiory (sets) pamięci podręcznej; sąsiedzi nie. To najostrzejszy efekt w tabeli i zupełnie
  niewidoczny w liczbie zapisów.
- **`d` na tyle duże, że `n/d` jest małe.** Przejście staje się kilkoma sekwencyjnymi strumieniami,
  za którymi nadąża prefetcher, i wraca do remisu: `d = 25 000 000` to 1.0×.

Trzy zakresy to mniej model, niż się wydaje: pozostałe średnie wartości lądują gdziekolwiek między
2.1× (`70 000`) a 13.4× (`12 345`), a `d = 100` kosztuje 8.9×, podczas gdy `d = 1 000` 3.2×.
Geometria pamięci podręcznej jest bardziej zawiła niż jakakolwiek reguła warta zapisania. Zostaje
kształt tabeli, a nie wzór: odwrócenia są płaskie, cykle nie.

To _nie_ jest efekt `gcd`: `d = 25 000 000` obsługuje 25 milionów cykli długości 2 i jest
najszybszym wierszem tabeli. To efekt pamięci podręcznej i mówi o tym właśnie ta nieregularność:
koszt TLB rósłby gładko ze skokiem, a nie skakał 7 razy między `1 000` a `1 024` i spadał z powrotem
przy `1 050`. (TLB nie został wyizolowany wprost: ta maszyna ma THP ustawione na `[always]`, więc
`-XX:+UseTransparentHugePages` niczego tu nie zmienia, a wyłączenie THP wymaga roota.) Zmniejszenie
tablicy zmniejsza karę, co jest bardziej bezpośrednim dowodem: to samo `d = 65 536` kosztuje 46×
przy 200 MB, 24× przy 32 MB, 15× przy 8 MB i 1.0× przy 1 MB, gdzie tablica mieści się w L2, a skoki
przestają mieć znaczenie.

**Wniosek: wybierz odwrócenia.** Kosztują 2n zapisów zamiast n i wygrywają nawet 56 razy przez to,
_gdzie_ piszą. Przejście po cyklach warto znać jako dowód, że n zapisów wystarcza, a sięgać po nie
tylko wtedy, gdy `d` jest znane i małe.

## Co robi JDK i dlaczego to nie kontrprzykład

`Collections.rotate` wybiera dokładnie między dwoma ostatnimi:

```java
if (list instanceof RandomAccess || list.size() < ROTATE_THRESHOLD)   // 100
    rotate1(list, distance);   // przejście po cyklach
else
    rotate2(list, distance);   // trzy odwrócenia
```

Listom opartym na tablicy daje przejście po cyklach, czyli pozornie odwrotnie niż wniosek wyżej. To
nie sprzeczność, bo odpowiada na inne pytanie: `rotate2` odwraca przez `subList` i `ListIterator`,
które `LinkedList` przechodzi tanio, ale przejście przez `get`/`set` z indeksem byłoby O(n²).
Wybór dotyczy **kosztu dostępu**, a nie pamięci podręcznej. Dla `int[]`, gdzie indeksowanie jest
darmowe, a tablica większa niż L3, kompromis się odwraca.

Uwaga też na **znak**: `Collections.rotate(list, d)` obraca w _prawo_. Obrót w lewo o `d` to
`rotate(list, -d)`, i tak test używa go jako jednego ze wzorców.

## Obrót jako brak operacji

Wzór `rotated[i] = a[(i + d) mod n]` nigdy nie wymagał przesuwania czegokolwiek. Jeśli wywołujący
tylko czyta, obrót to zmiana arytmetyki adresów i kosztuje O(1): dokładnie tym jest bufor
cykliczny i dlatego `ArrayDeque` obraca się przez przesunięcie indeksu początku. Przesuwanie
elementów to cena za to, żeby wynik był zwykłym `int[]`, którego indeks 0 jest tam, gdzie mówi
język.

## Wzorce w testach

Nic nie opiera się na tym, że implementacja świadczy sama za siebie:

| Wzorzec | Skala | Co łapie |
|---|---|---|
| Ręcznie zapisane oczekiwane tablice | 20 przypadków, w tym `gcd > 1`, ujemne `d`, `MIN_VALUE` | złą mapę indeksów |
| Powtarzane pojedyncze obroty | **każde** `(n, d)` dla `n ≤ 40`, `abs(d) ≤ 2n+3` | złą liczbę cykli |
| `Collections.rotate` | 500 losowych tablic | błąd znaku albo o jeden |
| `rotated[i] == (i + d) mod n` | 10⁷ elementów | przepełnienie i skalę |

Kluczowe jest przejście wyczerpujące: `gcd(n, d) > 1` to mniejszość par, a przejście po cyklach bez
ponownego startu przechodzi każdy przykład, który je omija.

Sprawdzone mutacjami: jedenaście celowych uszkodzeń, dziewięć złapanych. `cycles = 1` → 8 błędów,
usunięcie odejmowania przy zawijaniu → 20, odwrócenie całej tablicy najpierw → 15, surowe `d % n`
zamiast `floorMod` → 5, zwrócenie wejścia przy `shift == 0` → 1, obrót w prawo → 16, zepsucie
zamiany w algorytmie Euklidesa → 19, zakres któregoś odwrócenia przesunięty o jeden → 16 i 19. Dwa
ocalałe to mutanty równoważne, a nie luki: usunięcie `if (shift == 0) return` zostawia
`reverse(0,0)` i dwa pełne odwrócenia, czyli tożsamość, a `hole == start` zachodzi tylko w
pierwszym kroku przejścia.

```
mvn test -Dtest=LeftRotationTest        # 28 testów, ~0.2 s
```

Powyższe pomiary zrobiono doraźnie jednorazowym programem, a nie w ramach budowania.
