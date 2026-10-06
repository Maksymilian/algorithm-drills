# Śnieg między wzgórzami (woda po deszczu)

Kod: [`TrappingWater.java`](../../src/java/array/TrappingWater.java),
testy: [`TrappingWaterTest.java`](../../test/java/array/TrappingWaterTest.java).

Na liście są dwa zadania, które są jednym: „ile śniegu zmieści się między wzgórzami” i „trapping
rain water”.

## Treść

Wzgórza mają wysokości `[0, 1, 2, 1, 0, 3, 1, 2]`. Ile jednostek śniegu zmieści się między nimi?
Odpowiedź: **4**. Między 2 i 3 mieszczą się 3 jednostki, między 3 i 2 jedna.

```
          #          # = wzgórze, * = śnieg
    # * * # * #
  # # # * # # #
0 1 2 1 0 3 1 2      wysokości
0 0 0 1 2 0 1 0      śnieg nad każdym wzgórzem, razem 4
```

## Założenia

| | |
|---|---|
| Wzgórza | wysokości całkowite, nieujemne; każde ma szerokość 1 |
| Brzegi | poza tablicą nic nie ma, więc śnieg zsuwa się z brzegów |
| Wynik | `long`, bo n · max(h) może przekroczyć `int` |

## Kluczowa obserwacja

Nad wzgórzem `i` zmieści się

```
min(najwyższe po lewej, najwyższe po prawej) − h[i]
```

bo śnieg zsunie się przez niższą z dwóch ścian. Licząc maksima, wliczamy samo wzgórze `i`, więc
wynik nigdy nie jest ujemny.

## Dlaczego dwa wskaźniki

| Podejście | Czas | Pamięć | Komentarz |
|---|---|---|---|
| Dla każdego wzgórza szukaj maksimów w lewo i w prawo | O(n²) | O(1) | Wprost z obserwacji, za wolne |
| **Tablice maksimów od lewej i od prawej** | **O(n)** | **O(n)** | Najłatwiej udowodnić. **Od tego zaczynam na rozmowie** |
| Stos na `ArrayDeque` (warstwy poziome) | O(n) | O(n) | Jedno przejście od lewej, więc działa też na strumieniu. `trapWithDeque` |
| **Dwa wskaźniki** | **O(n)** | **O(1)** | **Wersja docelowa** |

Dlaczego dwa wskaźniki są poprawne: przesuwamy wskaźnik przy niższym wzgórzu. Jeśli
`h[left] < h[right]`, to po prawej stoi ściana co najmniej `h[right]`, wyższa niż cokolwiek, co do
tej pory widzieliśmy po lewej. Niższą ścianą dla `left` jest więc maksimum po lewej, które już
znamy. Prawej strony nie trzeba dokładnie znać.

## Wersja ze stosem (`ArrayDeque`)

Pozostałe wersje liczą śnieg **pionowo**, kolumna po kolumnie. Stos liczy go **poziomo**,
warstwami, w chwili, gdy po prawej pojawi się wyższa ściana.

Na stosie leżą indeksy wzgórz o nierosnących wysokościach, czyli wzgórza, które czekają na prawą
ścianę. Gdy przychodzi wzgórze `i` wyższe od szczytu stosu:

1. zdejmujemy szczyt: to **dno** dołka;
2. nowy szczyt to **lewa ściana**, a `i` to **prawa ściana**. Jeśli stos jest pusty, lewej ściany
   nie ma i śnieg zsuwa się poza mapę;
3. dokładamy warstwę: szerokość `i − lewa − 1`, wysokość `min(h[lewa], h[i]) − h[dno]`;
4. powtarzamy, dopóki szczyt jest niższy od `h[i]`, a potem wkładamy `i`.

```java
Deque<Integer> walls = new ArrayDeque<>();
for (int i = 0; i < h.length; i++) {
    while (!walls.isEmpty() && h[walls.peek()] < h[i]) {
        int bottom = walls.pop();
        if (walls.isEmpty()) break;
        int left = walls.peek();
        total += (long) (i - left - 1) * (Math.min(h[left], h[i]) - h[bottom]);
    }
    walls.push(i);
}
```

Przykład `[0, 1, 2, 1, 0, 3, 1, 2]`. Dla `i = 5` (wysokość 3) stos to `[2, 3, 4]` (wysokości 2, 1, 0):

| Zdjęte dno | Lewa ściana | Szerokość | Wysokość | Warstwa |
|---|---|---|---|---|
| 4 (wys. 0) | 3 (wys. 1) | 5 − 3 − 1 = 1 | min(1, 3) − 0 = 1 | 1 |
| 3 (wys. 1) | 2 (wys. 2) | 5 − 2 − 1 = 2 | min(2, 3) − 1 = 1 | 2 |
| 2 (wys. 2) | brak | — | — | stop |

Razem 3 jednostki między 2 i 3. Dla `i = 7` (wysokość 2) dno 6 między ścianami 5 i 7 daje jeszcze 1.
Wynik: **4**.

Dlaczego O(n), skoro jest pętla w pętli: każdy indeks raz wchodzi na stos i najwyżej raz z niego
schodzi, więc `while` wykonuje się łącznie najwyżej n razy.

## Dlaczego takie struktury danych

- **Zwykłe `int[]`.** Dane i tak są tablicą, a dostęp po indeksie z obu końców jest O(1).
- **W wersji ze stosem: `ArrayDeque<Integer>`, a nie `Stack`.** `Stack` dziedziczy po `Vector`, ma
  synchronizację na każdej operacji i jest przestarzały. `ArrayDeque` to tablica cykliczna, szybsza
  i bez blokad. Dokumentacja JDK sama zaleca `Deque` zamiast `Stack`. `push`, `pop` i `peek`
  działają na początku kolejki, w O(1).
- **Nie `LinkedList`.** Też implementuje `Deque`, ale tworzy obiekt-węzeł na każdy element.
- **Indeksy, nie wysokości.** Do szerokości warstwy potrzebna jest pozycja lewej ściany, a wysokość
  i tak odczytamy z `h[indeks]`.
- **Optymalizacja, jeśli zapytają.** `ArrayDeque<Integer>` opakowuje indeksy w obiekty. Własny
  stos na `int[n]` ze wskaźnikiem szczytu usuwa ten narzut.

## Dopytania

- **Mapa 2D?** Kolejka priorytetowa (`PriorityQueue`) komórek brzegowych. Zawsze rozlewamy od
  najniższej ściany do środka. O(n · m · log(n · m)).
- **Wzgórza zmieniają się na bieżąco?** Drzewo przedziałowe z maksimami.
