# Decibinary Numbers: `x`-ta liczba na posortowanej liście
## Zadanie

Liczba _decybinarna_ ma cyfry od `0` do `9` jak w systemie dziesiętnym, ale waga cyfry na pozycji
`i` (licząc od prawej, od zera) to `2^i` jak w systemie dwójkowym. Wartość dziesiętna liczby o
cyfrach `d_i`:

```
V(d) = suma po i z  d_i · 2^i
```

Na przykład:

```
2016  →  2·8 + 0·4 + 1·2 + 6·1 = 24
2008  →  2·8 + 0·4 + 0·2 + 8·1 = 24
```

Ta sama wartość ma więc wiele zapisów decybinarnych.

Wszystkie nieujemne liczby decybinarne ustawiamy na nieskończonej liście:

1. rosnąco według wartości dziesiętnej `V(d)`;
2. przy równej wartości rosnąco według zwykłej wartości liczbowej zapisu (`2008` przed `2016`).

Dla pozycji `x` (numerowanej od 1) szukamy liczby decybinarnej, która na niej stoi.

```java
public static long decibinaryNumbers(long x)
```

## Wejście

- pierwsza linia: `q`, liczba zapytań;
- każda z kolejnych `q` linii: pozycja `x`;
- na każde zapytanie wypisujemy liczbę decybinarną z pozycji `x`.

## Ograniczenia

Nie było ich widać w PDF-ie. Oryginał na HackerRank podaje, o ile pamiętam, `1 ≤ q ≤ 10^5` i
`1 ≤ x ≤ 10^16`. **Trzeba to sprawdzić** przed rozwiązaniem, bo od zakresu `x` zależy podejście.

## Początek listy

| Pozycja | Liczba decybinarna | Wartość |
|---:|---:|---:|
| 1 | 0 | 0 |
| 2 | 1 | 1 |
| 3 | 2 | 2 |
| 4 | 10 | 2 |
| 5 | 3 | 3 |
| 6 | 11 | 3 |
| 7 | 4 | 4 |
| 8 | 12 | 4 |
| 9 | 20 | 4 |
| 10 | 100 | 4 |

Sprawdzenie dla wartości 4: `4`, `12 = 1·2 + 2`, `20 = 2·2`, `100 = 1·4`. Innych zapisów nie ma,
więc wartość 4 zajmuje dokładnie pozycje 7 do 10.

## Algorytm: zliczanie zapisów i wybór cyfr

Lista jest nieskończona, a `x` może być ogromne, więc jej nie generujemy. Liczymy, **ile** zapisów
mieści się w kawałku listy, i przeskakujemy całe kawałki naraz. Wszystko opiera się na jednej
tablicy programowania dynamicznego:

```
ways[p][v] = liczba zapisów na p najmłodszych pozycjach (wagi 1, 2, …, 2^(p−1)) o wartości v

ways[0][0] = 1,  ways[0][v > 0] = 0
ways[p][v] = suma po d = 0..9 z ways[p−1][v − d·2^(p−1)]      (gdy v − d·2^(p−1) ≥ 0)
```

Nowa pozycja ma wagę `2^(p−1)`, a cyfra `d` na niej zabiera z wartości `d·2^(p−1)`. Resztę muszą
dać młodsze pozycje.

![Trzy kroki dla x = 9. Pierwszy: tablica ways dla p od 0 do 3 i v od 0 do 8. Drugi: pozycje 1–20 podzielone na grupy wartości, x = 9 wpada do wartości 4 na pozycjach 7–10, numer w grupie k = 2. Trzeci: wybór cyfr od najstarszej: na pozycji 2 cyfra 0 (3 zapisy, k = 2 mieści się), na pozycji 1 cyfry 0 i 1 są pomijane, bierzemy 2, na pozycji 0 cyfra 0; wynik 20](decibinary-numbers-ways.svg)

1. **Tablica.** Liczymy `ways[p][v]` dla `p = 0..P` i `v = 0..V`. `P` to liczba pozycji, które
   wystarczą do zapisu największej potrzebnej wartości `V`. Pozycja o wadze większej niż `V` może
   mieć tylko cyfrę 0.
2. **Wartość.** Na liście wartość `v` zajmuje `ways[P][v]` kolejnych pozycji. Sumy prefiksowe tych
   rozmiarów mówią, w której grupie stoi `x`: to pierwsza wartość, dla której suma prefiksowa
   jest `≥ x`. Szukamy jej wyszukiwaniem binarnym, bo zapytań jest wiele. Numer w grupie liczony od
   zera to `k = x − (pierwsza pozycja grupy)`.
3. **Cyfry.** W obrębie grupy zapisy mają tę samą liczbę pozycji (z zerami wiodącymi), więc
   porządek „jak zwykłe liczby” to porządek leksykograficzny cyfr. Idziemy od najstarszej pozycji
   `p` i próbujemy cyfr `d = 0, 1, …`. Cyfra `d` zostawia resztę `r = v − d·2^p`, którą młodsze
   pozycje zapisują na `ways[p][r]` sposobów:
   - `k < ways[p][r]`: szukany zapis zaczyna się od `d`, bierzemy ją i schodzimy pozycję niżej;
   - w przeciwnym razie pomijamy wszystkie te zapisy naraz: `k −= ways[p][r]`, próbujemy `d + 1`.

Zera wiodące znikają same, gdy złożymy cyfry w liczbę `long`: na rysunku `0, 2, 0` daje 20.

**Złożoność.** Tablica kosztuje O(P · V · 10) raz, przed wszystkimi zapytaniami. Jedno zapytanie
to wyszukiwanie binarne po wartościach, O(log V), i wybór cyfr, najwyżej 10 prób na pozycję,
czyli O(10 · P).

## Założenia

| | |
|---|---|
| `x` | od 1 do 10¹⁶; poza zakresem `IllegalArgumentException` |
| Wynik | `long` bez zer wiodących |

Górną granicę 10¹⁶ przyjąłem z oryginału na HackerRank (patrz „Ograniczenia”); w kodzie to stała
`MAX_POSITION`.

## Liczby dla `x ≤ 10^16`

- Rozmiary grup liczymy jedną rekurencją bez z góry znanego zakresu: najmłodsza cyfra `d` ma
  parzystość `v`, a reszta to zapis wartości `(v − d) / 2`, więc
  `count[v] = suma po d ≡ v (mod 2), d ≤ 9 z count[(v − d) / 2]`. Kolejne wartości dokładamy, aż
  suma rozmiarów sięgnie 10¹⁶. Wychodzi `V = 285 112`, czyli 19 bitów: `P = 19` pozycji.
- Tablica `ways` ma 20 wierszy po 285 113 liczb `long`, ok. 45 MB. Liczymy ją raz, przy pierwszym
  zapytaniu (klasa-schowek `Tables`).
- Najstarsza pozycja ma wagę 2¹⁸ = 262 144, a `V < 2 · 2¹⁸`, więc stoi na niej najwyżej cyfra 1.
  Wynik ma najwyżej 19 cyfr i jest mniejszy niż 2·10¹⁸, mieści się w `long`.
- Rozmiar grupy dochodzi do ok. 1,1·10¹¹, a sumy prefiksowe do 10¹⁶, więc obie tablice są `long`.

## Pułapki

- Wartość wyszukujemy po `x − 1` w sumach prefiksowych `positionsBefore`, żeby ostatnia pozycja
  grupy (np. `x = 10`, wartość 4) nie trafiła do grupy następnej.
- Porównanie „jak zwykłe liczby” działa jako porządek leksykograficzny tylko przy tej samej liczbie
  cyfr, dlatego wybieramy cyfry na wszystkich `P` pozycjach, także zera wiodące.

Rozwiązanie: `src/java/dynamic/DecibinaryNumbers.java`. Test porównuje pierwsze pozycje z listą
zbudowaną przeglądem wszystkich liczb poniżej 10⁶ (wszystkie zapisy wartości mniejszych niż 40)
i sprawdza porządek tysiąca pozycji przed 10¹⁶.
