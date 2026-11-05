# Poisonous Plants: ile dni umierają rośliny

## Zadanie

W ogrodzie rośnie `n` roślin w jednym rzędzie, każda zawiera pewną ilość pestycydu. Każdego dnia
umiera każda roślina, która ma **więcej** pestycydu niż roślina stojąca **bezpośrednio na lewo** od
niej. Wszystkie rośliny, które umierają danego dnia, znikają jednocześnie. Proces trwa, dopóki
jakaś roślina umiera.

Szukamy liczby dni, po których rośliny przestają umierać.

```java
public static int poisonousPlants(List<Integer> p)
```

- `p`: ilości pestycydu w roślinach, od lewej do prawej;
- wynik: liczba dni, po których żadna roślina już nie umiera.

## Wejście

- pierwsza linia: `n`, liczba roślin;
- druga linia: `n` liczb oddzielonych spacjami, ilości pestycydu.

## Ograniczenia

- `1 ≤ n ≤ 10^5`
- `0 ≤ p[i] ≤ 10^9`

## Przykład

```
wejście            wyjście
7                  2
6 5 8 4 7 10 9
```

```
dzień 0   6  5  8  4  7  10  9
dzień 1   6  5     4         9     umierają 8 (> 5), 7 (> 4), 10 (> 7)
dzień 2   6  5     4               umiera 9 (> 4)
dzień 3   nic nie umiera, wynik 2
```

Rośliny porównujemy z sąsiadem z lewej **w stanie z początku dnia**: `10` umiera, bo jego sąsiad
`7` żył rano, choć sam tego dnia też umiera. `9` przeżywa dzień 1, bo `9 < 10`, a dopiero po
zniknięciu `7` i `10` jego sąsiadem zostaje `4`.

## Algorytm: stos z dniem śmierci

Symulacja dzień po dniu kosztuje O(n²), gdy rośliny umierają po jednej dziennie. Zamiast tego
liczymy od razu, **w którym dniu ginie każda roślina**, i bierzemy największy z tych dni.

Obserwacja: roślina `i` ginie, gdy jej lewym sąsiadem stanie się roślina z mniejszą ilością
pestycydu. Najbliższa taka roślina z lewej (`j`) nigdy nie zginie przed `i`. Wszystkie rośliny
między `j` a `i` mają pestycydu co najmniej tyle co `i`, więc `i` musi poczekać, aż one znikną.
Stąd:

- brak mniejszej rośliny z lewej: `i` przeżywa (dzień 0);
- w przeciwnym razie: dzień(`i`) = 1 + max dni roślin między `j` a `i` (0, gdy żadnej nie ma).

Najbliższą mniejszą z lewej i maksimum dni po drodze daje jeden stos par `(pestycyd, dzień)`,
przechodzony od lewej do prawej:

1. `max = 0`; dopóki na szczycie stosu jest roślina z pestycydem `≥ p[i]`, zdejmujemy ją
   i aktualizujemy `max` jej dniem.
2. Pusty stos: dzień = 0. W przeciwnym razie: dzień = `max + 1`.
3. Wkładamy `(p[i], dzień)` na stos i aktualizujemy wynik.

![Dla 6 5 8 4 7 10 9: rośliny 8, 7 i 10 giną w dniu 1, a 9 w dniu 2, bo między nią a najbliższą mniejszą z lewej, czyli 4, stoją 7 i 10 z dniem 1. Pod spodem stan stosu po każdym kroku: przy 9 zdejmowane jest 10/1, na stosie zostaje 4/0, 7/1 i wkładane jest 9/2](poisonous-plants-stack.svg)

Zdjętej rośliny nie potrzebujemy już nigdy: bieżąca `p[i]` jest od niej nie większa i stoi
bliżej, więc to ona przesłoni ją każdej roślinie dalej na prawo. Dzień zdjętej rośliny nie
przepada, bo wchodzi do `max`, a ten do dnia bieżącej.

Każda roślina trafia na stos i schodzi z niego najwyżej raz: **czas O(n), pamięć O(n)**. Równe
ilości pestycydu zdejmujemy (`≥`), bo roślina z taką samą ilością jak sąsiad nie umiera, więc nie
jest „mniejszą z lewej”.

## Założenia

| | |
|---|---|
| Lista lub element `null` | `NullPointerException` |
| Pusta lista | poprawne wejście, wynik 0 |
| Ilości pestycydu | dowolne `int`, także 0 i ujemne |
| Równe ilości | roślina nie ginie, warunek jest ostry |

## Pułapki

- Równe sąsiednie rośliny giną **po jednej dziennie**: w `1 5 5 5` każda piątka ginie dopiero
  wtedy, gdy zniknie jej równy sąsiad, więc wynik to 3, a nie 1.
- Symulacja dzień po dniu dla `0, n−1, n−2, …, 1` trwa `n−1` dni po O(n) każdy, czyli O(n²);
  test `oneDeathPerDayOnLargeInput` sprawdza ten przypadek dla `n = 10^5`.

Rozwiązanie: `src/java/collection/PoisonousPlants.java`. Test porównuje wynik z symulacją dzień
po dniu na losowych danych.
