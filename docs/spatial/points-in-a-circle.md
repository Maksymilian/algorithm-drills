# Punkty w kole: liczenie miliarda punktów bez czytania ich

Notatka do [`src/java/spatial/PointCloud.java`](../../src/java/spatial/PointCloud.java),
testy: [`test/java/spatial/PointCloudTest.java`](../../test/java/spatial/PointCloudTest.java).

**Zadanie.** Konstruktor dostaje wektor punktów o współrzędnych całkowitych na płaszczyźnie,
_miliardy_ punktów. Napisz metodę, która przyjmuje środek i promień i zwraca, ile z tych punktów
leży w tym kole.

_(Pytanie jest postawione w C++, jako `vector<pair<int,int>>`. To repozytorium jest w Javie, więc
rozwiązanie też; argument o reprezentacji poniżej jest ten sam w obu językach, a w Javie wypada
gorzej.)_

## Słowo, które decyduje o wszystkim, to „miliardy”

Bez niego odpowiedź to jedna linia: sprawdź każdy punkt, `dx*dx + dy*dy <= r*r`. Ta linia wciąż jest
w klasie, jako `countInCircleByScan`, bo dla _jednego_ zapytania to właściwa odpowiedź: nic nie
pobije jednego przejścia, gdy jedno przejście to wszystko, co i tak miało się zrobić.

Z nim dwie rzeczy zmieniają się, zanim wybierzemy jakikolwiek algorytm.

**Punkty nie mogą być obiektami**, a tu przekład pytania na Javę wymaga uwagi, bo oryginał tego
problemu nie ma. `vector<pair<int,int>>` w C++ _to jest_ 8 bajtów na punkt, w ciągłej pamięci, bez
nagłówków i bez niczego do śledzenia: dokładnie taki układ, jakiego się chce. Najbliższe odpowiedniki
w Javie takie nie są.

| Dwa miliardy punktów jako | Bajtów na punkt | Razem |
|---|---:|---:|
| `List<int[]>`: nagłówek i referencja na punkt | 32–40 | 64–80 GB |
| `List<Point>` / `Point[]` z rekordem o dwóch polach | ~32 | ~64 GB |
| **dwie tablice `int[]`** | **8** | **16 GB** |

Referencje przestają być kompresowane powyżej sterty 32 GB, co gwarantuje sam w sobie każdy z dwóch
pierwszych wierszy, więc te liczby to pesymistyczny koniec ich zakresu, a nie najgorszy przypadek. A
każdy z tych obiektów garbage collector musi przejść; dwie tablice `int[]` to dwa obiekty, cokolwiek
w nich jest.

Prawdziwy konstruktor to więc `PointCloud(int[] xs, int[] ys)` i _przejmuje tablice_ zamiast je
kopiować, bo kopia to kolejne 16 GB. `PointCloud.of(List<int[]>)` przyjmuje sygnaturę z pytania
przetłumaczoną dosłownie, a jej dokumentacja mówi wprost, że przy tym rozmiarze to zła droga: lista
kosztuje więcej niż zbudowana z niej struktura.

**A przeglądanie wszystkiego przy każdym zapytaniu jest beznadziejne.** Przejście po dwóch miliardach
punktów to kilka sekund czystej przepustowości pamięci: dobre raz, absurdalne dla metody, która
będzie wołana wiele razy. Wszystko dalej dotyczy odpowiadania bez dotykania punktów.

## Indeks: siatka i jedna tablica w dwóch rolach

Konstruktor rozkłada punkty na jednorodną siatkę komórek i trzyma trzy tablice, i nic więcej:

```
xs, ys       punkty, przestawione w miejscu tak, że punkty jednej komórki leżą obok siebie
cellStart    cells + 1 sum bieżących, wierszami
```

`cellStart` to cała sztuczka, bo odpowiada naraz na dwa różne pytania:

- czytana jako **granice**, `cellStart[c] .. cellStart[c+1]` to miejsce punktów komórki `c` w
  `xs`/`ys`: zwykły układ CSR;
- czytana jako to, czym jest, czyli **suma bieżąca**, `cellStart[b] - cellStart[a]` to _ile punktów
  leży w komórkach `a .. b-1`_, a ponieważ siatka jest ułożona wierszami, komórki jednego wiersza są
  kolejne, więc cały ciąg komórek w poprzek wiersza liczy się **jednym odejmowaniem**.

Jedna tablica, bez drugiej struktury, bez obiektów na komórkę: 4 bajty na komórkę ponad 8 bajtów na
punkt. Przy 64 punktach na komórkę to indeks rzędu 0.8%, co potwierdzają pomiary niżej: 58 MB przy
7.6 GB.

### Permutacja w miejscu

Ułożenie punktów w kolejności komórek to sortowanie przez zliczanie, a podręcznikowe pisze do drugiej
pary tablic, czyli kolejnych 16 GB. Zamiast tego punkty są przenoszone na miejsce cyklami: weź
pierwsze niewypełnione pole komórki, zobacz, co na nim leży, i zamień to z pierwszym wolnym polem
komórki, do której należy. Każda zamiana stawia co najmniej jeden punkt na jego ostatecznym miejscu,
więc permutacja to O(n) zamian i kosztuje 4 bajty pamięci roboczej na _komórkę_, a nie na punkt.

## Zapytanie: płać za obwód, nie za pole

Całość na jednym obrazku: siatka, trzy rodzaje komórek i odejmowanie, które liczy ich ciąg:

![Liczenie punktów w kole z indeksem siatkowym: płaszczyzna podzielona na jednorodne komórki, komórki w całości wewnątrz koła liczone wprost z sum bieżących, komórki przecinane przez brzeg sprawdzane punkt po punkcie, komórki na zewnątrz pomijane](img.png)

_Zielone są liczone w całości, z `cellStart`, bez przeczytania ani jednego punktu. Żółte to miejsca,
przez które przechodzi brzeg, i tylko te punkty są sprawdzane. Panel 5 to odejmowanie, które wykonuje
całą pracę._

Teraz szczegóły. Koło jest przechodzone wiersz komórek po wierszu, a dla wiersza o wszystkim
decydują dwie dokładne odległości całkowite: najbliższa i najdalsza, na jaką pas `y` tego wiersza
oddala się od środka:

```
                          dyFar (najgorszy przypadek tego wiersza)
   +---+---+---+---+---+---+---+---+
   | . | . | # | # | # | # | . | . |     # komórki w całości wewnątrz: JEDNO odejmowanie
   +---+---+---+---+---+---+---+---+
     ^^^                       ^^^       komórki przecinane przez brzeg: punkty sprawdzane po kolei
```

- każdy punkt komórki jest w kole, gdy jest w nim **najdalszy narożnik** komórki, więc ciąg takich
  komórek to ciągły zakres kolumn, liczony w postaci zamkniętej i dodawany jednym odejmowaniem na
  `cellStart`, **niezależnie od tego, ile milionów punktów obejmuje**;
- otwierane są tylko komórki na dwóch końcach tego ciągu, te, przez które przechodzi brzeg koła, a
  ich punkty są sprawdzane dokładnie;
- wiersz, w który koło w ogóle nie trafia, jest pomijany bez arytmetyki.

Komórek brzegowych jest O(r/s) dla promienia `r` i rozmiaru komórki `s` (obwód mierzony w komórkach),
więc koszt jest proporcjonalny do **obwodu** koła i punktów przy nim, nigdy do jego pola ani do punktów
w środku. To zdanie, dla którego istnieje cała klasa: _koło zawierające miliard punktów dostaje
odpowiedź po przeczytaniu mniej więcej tysięcznej z nich_. Zmierzone niżej: 785 milionów policzonych
po otwarciu około dwunastu tysięcy komórek z piętnastu milionów.

`countInCircleByCells` to ten sam pomysł zrobiony w oczywisty sposób: odwiedź każdą komórkę
prostokąta otaczającego koło, dodaj całą liczbę tych w środku, sprawdź punkty tych, które przecina
brzeg. Też nigdy nie czyta punktów wnętrza, ale wciąż odwiedza _komórki_ wnętrza, O((r/s)²) z nich.
Sumowanie całego wiersza naraz usuwa tę ostatnią proporcjonalność do pola i to jedyna różnica między
dwiema metodami.

## Liczby całkowite do samego dołu

Dwa miejsca, w których łatwo się pomylić, i oba są testowane.

**Żadnych pierwiastków z odległości.** Koło to `dx*dx + dy*dy <= r*r` i nic więcej. Jedyny pierwiastek
to `isqrt`, czyli jak daleko koło sięga w `x` przy danym `y`, i jest poprawiany do dokładnej podłogi
całkowitej, bo `Math.sqrt` zwraca `double` z 53 bitami mantysy dla wartości mającej do 62 bitów i może
wylądować po dowolnej stronie prawdziwego pierwiastka. Punkt leżący dokładnie na okręgu musi być
policzony przez każdą metodę albo przez żadną.

**Przepełnienie.** Współrzędne obejmują cały zakres `int`, więc `dx` między dwiema z nich może wynosić
`2³²`, czego nie zmieści żaden `int`, a `dx*dx` może wynosić `2⁶⁴`, czego nie zmieści też żaden `long`.
Każde sprawdzenie odległości odrzuca więc `|dx| > r` _zanim_ cokolwiek podniesie do kwadratu, co
ogranicza iloczyn do `(2³¹−1)²`, a sumę dwóch do odrobinę poniżej `Long.MAX_VALUE`.

Oba błędy, które złapały testy, należały dokładnie do tej rodziny i żaden nie był w geometrii.

**Wybór rozmiaru komórki** porównuje `columns * rows` z budżetem, a dla chmury obejmującej całą
płaszczyznę obie strony to `2³²`, więc iloczyn to `2⁶⁴`, który `long` zgłasza jako **zero**: najszersza
możliwa siatka wygląda wtedy na najmniejszą i powstaje siatka bez żadnych komórek. Teraz porównanie to
dzielenie.

**Znajdowanie ciągu komórek w całości wewnątrz** dzieli `cx - reach - minX` przez rozmiar komórki, a
ten licznik sięga `2³²`, gdy chmura leży na jednym końcu płaszczyzny, a koło na drugim. Rzutowany na
`int` przed przycięciem do faktycznych kolumn wiersza wychodził _ujemny_, a ujemna kolumna czyta przed
początkiem sum bieżących: test, który to znalazł, pytał chmurę przy `MIN_VALUE` o koło przy
`MAX_VALUE` i dostawał odpowiedź **12** punktów zamiast zera. Zła odpowiedź, a nie awaria, czyli
rodzaj błędu, który przeżywa. Przycinanie odbywa się teraz na `long`, a rzutowanie dopiero wtedy, gdy
wiadomo, że wartość jest kolumną.

## Wybór rozmiaru komórki

Jedno pokrętło, jeden kompromis. Komórki mają szerokość potęgi dwójki, więc indeks komórki to
przesunięcie, a nie dzielenie, a konstruktor wybiera najmniejszą potęgę dwójki, przy której siatka
mieści się w budżecie około `n / pointsPerCell` komórek (domyślnie 64, najwyżej 2²⁶ komórek = 256 MB
indeksu).

| Mniejsze komórki | Większe komórki |
|---|---|
| mniej punktów do dokładnego sprawdzenia na brzegu | więcej |
| praca na brzegu maleje liniowo z `s` | rośnie liniowo |
| indeks rośnie kwadratowo, gdy `s` maleje | maleje kwadratowo |

64 punkty to kilka linii pamięci podręcznej: mniej więcej rozmiar, przy którym otwarcie komórki
kosztuje tyle, ile i tak kosztuje jeden losowy odczyt pamięci.

## Pomiary

Zmierzone doraźnie, nie odtwarzane przy budowaniu. JDK 25.0.1 (Temurin), Intel i7-14650HX, punkty
jednostajnie w kwadracie 2·10⁶ × 2·10⁶, najlepszy z pięciu, wszystkie trzy metody zgodne w każdym
wierszu.

**Miliard punktów**: 7.6 GB współrzędnych, wygenerowane w 7 s i zaindeksowane w 230 s do 15 264 649
komórek po 65 punktów, czyli **58 MB indeksu: 0.76% ponad dane**.

| promień | znalezione punkty | `countInCircle` | `byCells` | `byScan` |
|---:|---:|---:|---:|---:|
| 1 000 | 753 | 0.044 ms | 0.050 ms | 337.9 ms |
| 10 000 | 78 155 | 0.033 ms | 0.148 ms | 356.2 ms |
| 100 000 | 7 852 517 | 0.364 ms | 0.935 ms | 382.6 ms |
| 500 000 | 196 341 681 | 1.812 ms | 10.276 ms | 530.9 ms |
| 1 000 000 | **785 404 930** | **3.901 ms** | 38.216 ms | 807.5 ms |
| 2 000 000 | **1 000 000 000** | **0.724 ms** | 36.702 ms | 802.0 ms |

Dwa wiersze to odpowiedź na pytanie. **785 milionów punktów policzonych w 3.9 milisekundy**, 207 razy
szybciej niż przeglądanie, a jedyne przeczytane punkty to te w komórkach, przez które przechodzi brzeg
koła, około dwunastu tysięcy komórek z piętnastu milionów. I ostatni wiersz, gdzie koło połyka całą
płaszczyznę: **cały miliard policzony w 0.72 ms**, 1 100 razy szybciej niż przeglądanie, bo koło bez
brzegu wewnątrz siatki to same odejmowania wierszy. Odpowiedź jest większa, a pracy mniej.

`byCells` to grupa kontrolna. Przy `r = 1 000 000` wykonuje identyczne sprawdzenia punktów i jest 10
razy wolniejsza, bo to koło pokrywa całą siatkę: odwiedza wszystkie 15 milionów komórek po kolei, gdzie
`countInCircle` robi 3 906 odejmowań wierszy. Ten czynnik to zmierzona różnica między
„proporcjonalne do pola” a „proporcjonalne do obwodu”.

Dla porównania dziesięć milionów punktów: 60 025 komórek po 166 punktów, zbudowane w 0.4 s:

| promień | znalezione punkty | `countInCircle` | `byCells` | `byScan` |
|---:|---:|---:|---:|---:|
| 1 000 | 8 | 0.014 ms | 0.014 ms | 6.7 ms |
| 10 000 | 788 | 0.004 ms | 0.007 ms | 4.1 ms |
| 100 000 | 78 288 | 0.055 ms | 0.093 ms | 4.3 ms |
| 500 000 | 1 963 625 | 0.255 ms | 1.170 ms | 6.0 ms |
| 1 000 000 | 7 855 094 | 0.533 ms | 1.109 ms | 9.2 ms |
| 2 000 000 | 10 000 000 | **0.055 ms** | 0.161 ms | 8.5 ms |

Najpierw ostatni wiersz: **cała chmura policzona w 55 mikrosekund**, bo koło, które obejmuje wszystko,
w ogóle nie ma brzegu wewnątrz siatki, więc każdy wiersz to jedno odejmowanie. Wiersz nad nim to ten,
który mówi o polu: 7.9 miliona punktów policzonych w pół milisekundy po otwarciu tylko komórek wzdłuż
jednego obwodu.

`byCells` trzyma się blisko `countInCircle`, dopóki koło jest małe, i zostaje w tyle, gdy rośnie: przy
`r = 500 000` jest 4.6 razy wolniejsza. Wykonuje dokładnie te same sprawdzenia punktów; zamiast 122
odejmowań wierszy odwiedza wszystkie 122² ≈ 15 000 komórek prostokąta otaczającego, po kolei. Obie
zostawiają przeglądanie daleko w tyle, a czasy samego przeglądania prawie nie zmieniają się z
promieniem, i to jest znak: za każdym razem wykonuje tę samą pracę, niezależnie od pytania.

**Kiedy nie budować indeksu.** Budowa to dwa przejścia po punktach, a przy dziesięciu milionach
kosztuje mniej więcej tyle co 100 przeglądań; przy miliardzie 230 s, czyli około 600 przeglądań, bo
zapisy permutacji lądują losowo w 7.6 GB, a to drożeje na punkt, gdy tablica przerasta każdą pamięć
podręczną. Jedno zapytanie albo kilka: przeglądanie wygrywa bez dyskusji. Powyżej kilkuset siatka już
się zwróciła, a każde kolejne zapytanie jest prawie darmowe. Punkt przecięcia to własność budowy, a nie
zapytania, i dlatego przeglądanie zostaje w klasie, zamiast być w niej wstydliwym dodatkiem.

## Czym to nie jest

**Drzewem k-d.** Ten sam skrót „całe poddrzewo jest w środku, dodaj jego liczbę” działa na drzewie
k-d i nie zakłada, że punkty są równomiernie rozłożone: przy silnym skupieniu jednorodna siatka
degeneruje się do kilku zatłoczonych komórek, a praca na brzegu rośnie razem z nimi. Drzewo kosztuje
skok po wskaźniku na poziom zamiast przesunięcia, indeks 4-8 bajtów na _punkt_ zamiast na komórkę i
budowę, która sortuje zamiast liczyć. Dla danych w miarę równomiernych siatka wygrywa na każdej osi;
dla skupionych drzewo jest bezpieczniejszą strukturą.

**Sortowaniem po x z wyszukiwaniem binarnym.** Posortuj punkty po `x`, znajdź binarnie pas
`[cx-r, cx+r]`, sprawdź wszystko w nim. To cztery linie i poza sortowaniem nie potrzebuje żadnego
indeksu, ale sprawdza każdy punkt pasa szerokości `2r` i wysokości całych danych, gdzie siatka
sprawdza pierścień grubości `s`. Dla zapytania o promieniu małym względem chmury jest przyzwoite; dla
dużych kół powyżej to przeglądanie z dodatkowymi krokami.

**Przybliżeniem.** Żadnego próbkowania, żadnych liczb zmiennoprzecinkowych, żadnego liczenia
probabilistycznego. Każda odpowiedź to dokładna liczba punktów, łącznie z tymi dokładnie na okręgu.

## Testy

Kluczowy test jest wyczerpujący i maleńki: **każdy punkt `[-4, 4]²`, każdy środek `[-6, 6]²`, każdy
promień od 0 do 8**, czyli 1 521 kół, każde policzone wszystkimi trzema metodami i porównane z
definicją, przy trzech różnych rozmiarach komórki: siatka po jednym punkcie na komórkę, potem po
cztery, potem cała chmura w jednej komórce. Odpowiedzi nie mogą znać tej różnicy. Przypadki brzegowe
siedzą w kształcie zadania, a nie w jego rozmiarze: koła o środku w punkcie, między punktami, całkiem
poza chmurą i te najcenniejsze, których brzeg przechodzi dokładnie przez punkty, co przy promieniu 5
wokół początku układu daje `(3, 4)` i `(0, 5)`.

Wzorzec jest napisany na `BigInteger`. To celowe: klasa uważa na `long` właśnie dlatego, że `dx * dx`
się w nim nie mieści, a wzorzec powtarzający to rozumowanie zgodziłby się z błędem w nim.

Dalej: współrzędne przy `Integer.MIN_VALUE` i `MAX_VALUE` z pasującymi promieniami (test przepełnienia,
który znalazł błąd w wyborze rozmiaru siatki); ciasna chmura na jednym końcu płaszczyzny pytana o koła
na drugim, która znalazła rzutowanie; pusta chmura; sto kopii jednego punktu; koło w dziurze; koła
mijające chmurę z każdej z czterech stron; 2 000 losowych kół na 400 losowych chmurach w każdej skali,
od ciasnego skupiska po całą płaszczyznę; i sprawdzenie, że permutacja w miejscu ani nie gubi punktu,
ani nie wymyśla nowego.

**Test, który kłóci się z `double`.** Dziesięć punktów w jednym wierszu komórek o boku jeden i koło o
promieniu `2³⁰`, którego brzeg przechodzi przez nie. Zasięg koła wzdłuż tego wiersza to
`sqrt(r² − 1)`, którego podłoga to `r − 1`, ale `r² − 1` potrzebuje 61 bitów, a `double` niesie 53,
więc zaokrągla się do `r²`, a `Math.sqrt` odpowiada `r`. O jeden za daleko: komórka z najbardziej
wysuniętym w lewo punktem zostaje uznana za całą wewnątrz, a jej punkt policzony bez przeczytania,
dziesięć zamiast dziewięciu. To jedyny test w klasie, który odróżnia `isqrt` od `Math.sqrt`, i to nie
zgadywanie, tylko to, co zgłosiło sprawdzenie mutacjami niżej, zanim ten test istniał.

### Test, który sprawdza strukturę

Testy poprawności nie widzą różnicy między tą klasą a klasą, która po cichu sprawdza każdy punkt: obie
zwracają te same liczby. Jeden test sprawdza więc kształt pracy zegarem: **dziesięć milionów punktów,
dwadzieścia tysięcy kół, w każdym około pięciu milionów punktów**, w limicie 20 sekund.

Zmierzone: około 4.5 s w obecnej postaci. Z wyłączonym ciągiem wnętrza (punkty komórek sprawdzane po
kolei, mutacja, która nie zmienia żadnej odpowiedzi i przechodzi każdy inny test w klasie) około 58 s.
Oba marginesy są szerokie, a limit między nimi oddziela „indeks działa” od „indeks to dekoracja”.

### Sprawdzenie mutacjami

Czternaście celowych uszkodzeń, czternaście złapanych, choć dwa z testów istnieją tylko dlatego, że
wcześniejsze przejście tego sprawdzenia mówiło inaczej.

| Mutacja | Złapana przez |
|---|---|
| koło otwarte zamiast domkniętego | 7 błędów |
| najdalszy narożnik komórki brany jako najbliższy | 7 błędów |
| suma wnętrza gubi ostatnią komórkę | 7 błędów |
| liczniki komórek nigdy nie zamieniane na sumy bieżące | 7 błędów, 1 przekroczenie czasu |
| ciąg wnętrza mierzony od najbliższej krawędzi wiersza, a nie od najdalszej | 5 błędów |
| ciąg wnętrza o jedną komórkę za szeroki | 5 błędów |
| sufit zamiast podłogi na lewej krawędzi ciągu wnętrza | 5 błędów |
| sprawdzenie punktu przestaje chronić przed przepełnieniem | 2 błędy |
| sprawdzenie narożnika przestaje chronić przed przepełnieniem | 2 błędy |
| `isqrt` ufa `Math.sqrt` | **1 błąd**: tylko test dokładnego pierwiastka |
| kolumny wnętrza rzutowane przed przycięciem | **1 błąd**: tylko test dalekiego narożnika |
| budżet siatki znowu porównywany mnożeniem | 1 wyjątek: tylko chmura na całą płaszczyznę |
| **ciąg wnętrza nigdy nie działa** | **1 przekroczenie czasu**: tylko 20-sekundowy limit |
| **permutacja nigdy nie przechodzi dalej za punkt już na miejscu** | **14 przekroczeń czasu**: w sumie 13 minut |

Warto czytać cztery wiersze, które wiszą na jednym teście. Dwa z tych testów dopisano po fakcie: test
dokładnego pierwiastka, bo wcześniejsze sprawdzenie zgłosiło `isqrt` jako mutację, która **przeżyła**,
i test dalekiego narożnika, bo czytanie kodu ujawniło przedwczesne rzutowanie. Po to jest to ćwiczenie:
nie dla dziesięciu wierszy, które głośno zawodzą, tylko dla tego jednego, który nie zawiódł wcale.

```
mvn test -Dtest=PointCloudTest
```
