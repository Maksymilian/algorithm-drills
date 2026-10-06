# Dwie windy, dwadzieścia pięter: wersja najmniejsza

Notatka do [`src/java/greedy/SimpleElevators.java`](../../src/java/greedy/SimpleElevators.java)
(około 80 linii logiki), testy:
[`test/java/greedy/SimpleElevatorsTest.java`](../../test/java/greedy/SimpleElevatorsTest.java).
To wersja do napisania z pamięci. Każde dopytanie dokłada jedną rzecz:
[`ElevatorSystem`](elevator-interview.md) dokłada kierunek, w którym chce jechać wzywający, a
[`TwoElevators`](two-elevators.md) drzwi, pojemność, czas i pomiary.

## Cały pomysł w dwóch zdaniach

1. **Jedna winda:** piętro, kierunek i **posortowany zbiór** przystanków. Jedzie dalej, dopóki
   przystanek jest przed nią, zawraca, gdy żadnego nie ma, i staje, gdy zbiór jest pusty. To
   algorytm LOOK.
2. **Dwie windy:** wezwanie z piętra dostaje winda, która **dojedzie pierwsza**, a to nie zawsze
   najbliższa.

## 1. Dopytaj (około 1 minuty)

- Piętra 0–19, dwie windy, obie startują z parteru.
- Przyciski: **w kabinie** („zawieź mnie na 12”) i **na piętrze** („przyjedź na 7”).
- Cel: krótkie oczekiwanie i nikt nie czeka w nieskończoność.
- Na razie pomijamy: drzwi, pojemność, czasy i to, czy wzywający chce jechać w górę, czy w dół.

## 2. Dane (około 1 minuty)

```java
class Elevator {
    int floor;
    Direction direction;            // UP, DOWN, IDLE
    TreeSet<Integer> stops;         // piętra, na których trzeba stanąć
}
```

**Dlaczego posortowany zbiór, a nie kolejka?**

- Kolejka obsługuje w kolejności wciśnięcia przycisków. Wciśnij 12, 5, 9, a pojedzie najpierw na 12,
  wioząc osobę jadącą na 5 obok jej piętra. Zamiatanie staje na 5, 9, 12.
- Zbiór ignoruje powtórzenia: pięć osób wciskających 7 to jeden przystanek.
- `higher(floor)` i `lower(floor)` odpowiadają na pytanie „czy coś jest przede mną?” w O(log n).

## 3. Jeden krok jednej windy (około 3 minut)

```java
void step() {
    openDoorsIfStop();                       // jeśli to piętro jest przystankiem
    if (stops.isEmpty()) {
        direction = Direction.IDLE;
        return;
    }
    direction = nextDirection();
    floor += direction == Direction.UP ? 1 : -1;
}

private Direction nextDirection() {
    boolean above = stops.higher(floor) != null;
    boolean below = stops.lower(floor) != null;
    return switch (direction) {
        case UP -> above ? Direction.UP : Direction.DOWN;     // jedź dalej, inaczej zawróć
        case DOWN -> below ? Direction.DOWN : Direction.UP;
        case IDLE -> above ? Direction.UP : Direction.DOWN;
    };
}
```

Jeśli zbiór nie jest pusty, a nad windą nic nie ma, coś musi być pod nią, więc zawracanie jest zawsze
bezpieczne.

**Bez głodzenia:** winda nigdy nie zawraca, dopóki przystanek jest przed nią, więc do każdego
przystanku dojedzie w ciągu jednego przejazdu w górę i jednego w dół.

## 4. Wybór windy (około 3 minut)

Koszt = liczba pięter do przejechania, zanim winda dojedzie do wzywającego:

| Winda | Wzywający jest… | Koszt |
|---|---|---|
| stoi | gdziekolwiek | `abs(floor − target)` |
| jedzie w górę | na jej piętrze lub wyżej | `target − floor` |
| jedzie w górę | niżej | `(top − floor) + (top − target)`, gdzie `top` to jej najwyższy przystanek |
| jedzie w dół | lustrzanie | lustrzanie |

```java
Elevator call(int floor) {
    Elevator best = elevators.stream()
            .min(Comparator.comparingInt(e -> cost(e, floor)))   // przy remisie pierwsza z listy
            .orElseThrow();
    best.press(floor);
    return best;
}
```

**Przykład** (to robi `main`): A jest na piętrze 3 i jedzie w górę na 19, B stoi na parterze. Ktoś
wzywa windę z piętra 2.

- Najbliższa: A, piętro dalej.
- Dojedzie pierwsza: A kosztuje (19 − 3) + (19 − 2) = **33**, B kosztuje **2**. Wysyłamy **B**.

## 5. Złożoność

- `step`: O(log n) na windę, przy n ≤ 20 przystankach.
- `call`: O(E) dla E wind. Koszt to O(1), bo `TreeSet.last()` i `first()` to O(log n).

## 6. Dopytania i co się zmienia

| Pytanie | Odpowiedź |
|---|---|
| Wzywający chce jechać w dół, a winda jedzie obok w górę? | Dwa zbiory, `upStops` i `downStops`, i obsługa wezwania tylko w drodze w jego stronę. To [`ElevatorSystem`](elevator-interview.md). |
| N wind? | Nic się nie zmienia: minimum kosztu po liście. |
| Czas zamiast pięter? | Koszt = piętra × sekundy na piętro + przystanki po drodze × sekundy na przystanek. |
| Pojemność? | Pełna winda mija wezwania. Dodaj karę do jej kosztu albo oddaj wezwanie. |
| Współbieżność? | Naciśnięcia przycisków trafiają do kolejki. Jedna pętla sterująca ją opróżnia i jako jedyna trzyma stan, więc blokady nie są potrzebne. |
| Dlaczego nie FIFO? | Jest sprawiedliwe co do kolejności, ale winda ciągle zawraca. W pełnej symulacji LOOK skrócił średnie czekanie nawet pięciokrotnie ([liczby](two-elevators.md)). |
| Maska bitowa zamiast `TreeSet`? | 20 pięter mieści się w `int`: „coś powyżej” to `(stops >> (floor + 1)) != 0`. |

## Uruchomienie

```sh
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn -q compile
java -cp target/classes greedy.SimpleElevators
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest=SimpleElevatorsTest
```

```
A is at 3 going UP, B is idle at 0
call at 2: cost for A 33 floors, for B 2 floors
sent: B  (A is nearer, but it must reach 19 first)
pressed 12, 5, 9; stopped at [5, 9, 12]
```
