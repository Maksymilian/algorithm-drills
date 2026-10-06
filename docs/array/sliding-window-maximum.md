# Maksimum w przesuwanym oknie

Kod: [`SlidingWindowMaximum.java`](../../src/java/array/SlidingWindowMaximum.java),
testy: [`SlidingWindowMaximumTest.java`](../../test/java/array/SlidingWindowMaximumTest.java).

## Treść

Dla tablicy i okna długości k zwróć maksimum każdego okna.

```
[1, 3, -1, -3, 5, 3, 6, 7], k = 3  ->  [3, 3, 5, 5, 6, 7]
```

## Założenia

| | |
|---|---|
| Okno | 1 ≤ k ≤ n, w przeciwnym razie wyjątek |
| Wartości | dowolne `int`, także ujemne i powtórzone |
| Wynik | n − k + 1 maksimów, po jednym na okno |

## Rozwiązanie

Kolejka monotoniczna: indeksy elementów, które *mogą jeszcze* zostać maksimum. Ich wartości maleją
od początku do końca kolejki.

```
dla każdego i:
  1. z początku usuń indeks, który wypadł z okna (≤ i − k)
  2. z końca usuń elementy ≤ a[i]: nowy jest od nich większy i zostanie w oknie dłużej,
     więc one już nigdy nie będą maksimum
  3. dopisz i na końcu
  4. maksimum okna = a[początek kolejki]
```

## Dlaczego kolejka monotoniczna

| Podejście | Czas | Pamięć | Komentarz |
|---|---|---|---|
| Przejrzyj każde okno | O(n · k) | O(1) | Dla n = 10⁶, k = 10⁴ to 10¹⁰ operacji |
| `TreeMap<wartość, licznik>` jako multizbiór | O(n log k) | O(k) | Dobre i proste. Działa też dla mediany |
| Kopiec z leniwym usuwaniem | O(n log n) | O(n) | Stare elementy zalegają w kopcu |
| Bloki po k: maksima prefiksowe i sufiksowe | O(n) | O(n) | Sprytne, ale trudniej je wytłumaczyć |
| **Kolejka monotoniczna** | **O(n)** | **O(k)** | **Wybrane** |

Dlaczego O(n), skoro w środku jest pętla `while`: każdy indeks raz wchodzi do kolejki i najwyżej
raz z niej wychodzi. Łącznie to najwyżej 2n operacji (analiza zamortyzowana).

## Dlaczego takie struktury danych

- **`ArrayDeque<Integer>`.** Potrzebujemy operacji na obu końcach: usuwania z początku (wypadł z
  okna) i z końca (przegrał z nowym). `ArrayDeque` robi to w O(1) na tablicy cyklicznej.
  - `LinkedList` też ma obie operacje, ale tworzy obiekt-węzeł na każdy element i jest wolniejszy.
  - `Stack` ma tylko jeden koniec i synchronizację.
  - `PriorityQueue` nie umie usunąć elementu, który wypadł z okna, w O(log n).
- **Indeksy, nie wartości.** Z indeksu odczytamy wartość, a z wartości nie wiemy, czy element
  wypadł z okna (wartości mogą się powtarzać).
- **Optymalizacja, jeśli zapytają.** `ArrayDeque<Integer>` opakowuje indeksy w obiekty. Własny
  bufor cykliczny na `int[k]` usuwa ten narzut.

## Dopytania

- **Minimum?** To samo z odwróconym porównaniem.
- **Mediana w oknie?** Dwa `TreeSet`/kopce (dolna i górna połowa), O(n log k).
- **Dane jako strumień?** Ta sama kolejka działa element po elemencie.
