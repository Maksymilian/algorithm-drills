# Dwie windy, dwadzieścia pięter: wersja na rozmowę

Notatka do [`src/java/greedy/ElevatorSystem.java`](../../src/java/greedy/ElevatorSystem.java)
(około 150 linii), testy:
[`test/java/greedy/ElevatorSystemTest.java`](../../test/java/greedy/ElevatorSystemTest.java).
Pełna symulacja, z czasami, pojemnością, czterema strategiami i pomiarami, to
[`TwoElevators`](two-elevators.md). Ta strona to wersja do wyjaśnienia przy tablicy w piętnaście
minut, w kolejności, w jakiej się ją tłumaczy.

## 1. Zapytaj, zanim zaprojektujesz

- Ile pięter i wind? 20 i 2, numerowane 0–19. Czy parter to 0?
- Co można wcisnąć? W górę i w dół na każdym piętrze (**wezwanie z piętra**), piętro w kabinie
  (**przycisk w kabinie**).
- Co znaczy „dobrze”? Zwykle średni czas czekania, przy czym nikt nie czeka w nieskończoność.
- Poza zakresem, chyba że rekruter zapyta: pojemność, czasy drzwi, sytuacje awaryjne, współbieżność.

## 2. Model: dwa zbiory na windę, a nie kolejka

```java
class Elevator {
    int floor;
    Direction direction;                      // UP, DOWN, IDLE
    TreeSet<Integer> upStops;                 // przystanki w drodze w górę
    TreeSet<Integer> downStops;               // przystanki w drodze w dół
}
```

Dlaczego nie jedna kolejka żądań w kolejności wciśnięcia?

- **Kolejność.** Poproszona o 12, 5 i 9 kolejka jedzie najpierw na 12, wioząc osobę jadącą na 5 obok
  jej piętra. Właściwa kolejność to ta, w której winda mija piętra: 5, 9, 12.
- **Powtórzenia.** Pięć osób wciskających „w dół” na 7 to jedno żądanie. Zbiór połyka to za darmo.
- **Kierunek.** „W górę na 7” i „w dół na 7” to różne żądania, obsługiwane w różnych chwilach.

Gdzie trafia każde żądanie:

| Żądanie | Trafia do |
|---|---|
| wezwanie z piętra `f` w górę | `upStops` |
| wezwanie z piętra `f` w dół | `downStops` |
| przycisk w kabinie na `f`, winda poniżej | `upStops` |
| przycisk w kabinie na `f`, winda powyżej | `downStops` |

## 3. Jedna winda: zamiatanie (LOOK)

W każdym kroku winda zadaje trzy pytania:

1. **Stanąć tutaj?** Tak dla przystanku w jej kierunku. Staje też dla przystanku w drugą stronę, jeśli
   przed nią nic nie ma i i tak zaraz zawraca. Winda jadąca w górę mija kogoś, kto chce jechać w
   dół, i zabiera go w drodze powrotnej.
2. **W którą stronę dalej?** Jedź dalej, dopóki cokolwiek jest przed windą, inaczej zawróć, inaczej
   stój.
3. Przejedź jedno piętro.

```java
boolean anythingAbove() {
    return upStops.higher(floor) != null || downStops.higher(floor) != null;   // O(log n)
}

boolean shouldStopHere() {
    return switch (direction) {
        case UP -> upStops.contains(floor) || downStops.contains(floor) && !anythingAbove();
        case DOWN -> downStops.contains(floor) || upStops.contains(floor) && !anythingBelow();
        case IDLE -> upStops.contains(floor) || downStops.contains(floor);
    };
}
```

To algorytm planowania dysku LOOK, czyli „algorytm windy”. SCAN to wariant, który zawsze jedzie do
końca szybu; LOOK zawraca przy ostatnim żądaniu.

**Bez głodzenia.** Winda nigdy nie zawraca, dopóki cokolwiek jest przed nią, więc każdy przydzielony
przystanek obsłuży najpóźniej pod koniec trzeciego przejazdu (góra, dół, znowu góra).

**Koszt.** Każdy krok to O(log n) dla n przystanków, a n ≤ 20. Przy 20 piętrach równie dobrze działa
maska bitowa w `int`: „coś powyżej” to `(stops & (-1 << (floor + 1))) != 0`. Tak robi pełna
symulacja.

## 4. Dwie windy: wyślij tę, która dojedzie pierwsza

Kusząca reguła to **najbliższa** winda. Jest błędna, bo ignoruje kierunek:

![Dwie windy w czasie: najbliższa właśnie minęła piętro 2, jadąc na 19, więc wzywający czeka 72 s; stojąca, dwa piętra dalej, dojeżdża w 4 s](two-elevators-nearest-vs-eta.svg)

Lepsza reguła to **szacowany czas przyjazdu** (ETA): ile winda musi przejechać, zanim zabierze tę
osobę, jadąc w jej stronę.

```java
static int cost(Elevator e, int callFloor, Direction wanted) {
    if (e.direction == Direction.IDLE || isOnTheWay(e, callFloor, wanted)) {
        return Math.abs(e.floor - callFloor);
    }
    // inaczej: do końca tego przejazdu, potem z powrotem
    int turn = e.direction == Direction.UP ? Math.max(e.endOfSweep(), callFloor)
                                           : Math.min(e.endOfSweep(), callFloor);
    return Math.abs(turn - e.floor) + Math.abs(turn - callFloor);
}
```

Przykład, który jest w `main` i w teście: A jest na piętrze 3 i jedzie w górę na 19, B stoi na
parterze. Ktoś na piętrze 2 chce jechać w górę.

- **Najbliższa:** A, piętro dalej.
- **ETA:** A kosztuje (19 − 3) + (19 − 2) = **33** piętra; B kosztuje **2**. Wysyłamy B.

## 5. Co jest lepsze i o ile

Oba wybory zmierzono w pełnej symulacji: godzina ruchu, 20 ziaren losowości, cztery wzorce ruchu
([szczegóły](two-elevators.md)). Średni czas czekania przy czterech osobach na minutę:

| | poranny szczyt | wieczorny szczyt | między piętrami | lunch |
|---|---:|---:|---:|---:|
| kolejka FIFO + najbliższa | 545 s | 125 s | 88 s | 54 s |
| LOOK + najbliższa | 103 s | 57 s | 41 s | 32 s |
| **LOOK + ETA** | **20 s** | **31 s** | **31 s** | **25 s** |

Trzy rzeczy warte powiedzenia na głos:

- **LOOK + ETA wygrywa wszędzie**, a przy średnim czekaniu poniżej minuty obsługuje mniej więcej dwa
  razy większy ruch niż kolejka FIFO.
- **To, który wybór ważniejszy, zależy od obciążenia.** Gdy w budynku jest spokojnie, najważniejsze
  jest wysłanie właściwej windy. Gdy jest tłoczno, najważniejsze jest zamiatanie, bo winda FIFO
  spędza czas na zawracaniu.
- **Ceną jest sprawiedliwość.** Kolejka FIFO obsługuje ludzi po kolei. Przy małym ruchu jej
  _najdłuższe_ czekanie bywało krótsze: w trzech z czterech wzorców przy dwóch osobach na minutę.
  LOOK każe kilku osobom czekać dłużej, żeby wszyscy inni czekali krócej.

## 6. Dopytania, które może zadać rekruter

| Pytanie | Krótka odpowiedź |
|---|---|
| Pojemność? | Pełna winda mija wezwania i je zatrzymuje albo oddaje dyspozytorowi. Dodaj karę do jej kosztu. |
| Ponowny przydział wezwań? | Tak, co krok, przeliczając koszty; albo pozwól przejąć wezwanie każdej windzie jadącej w tę stronę. Prawdziwe systemy robią jedno albo drugie. |
| Czas zamiast pięter? | Koszt = piętra × sekundy na piętro + przystanki po drodze × sekundy na przystanek. Pełna symulacja odtwarza przejazd, żeby je policzyć. |
| N wind? | Nic się nie zmienia: dyspozytor bierze minimum kosztu po wszystkich. O(N) na wezwanie. |
| Bezpieczeństwo wątkowe? | Naciśnięcia przycisków przychodzą współbieżnie. Wrzucaj je do kolejki opróżnianej przez jedną pętlę sterującą, która trzyma cały stan, więc na gorącej ścieżce nie ma blokad. Albo zsynchronizuj `call` i `step`. |
| Stojące windy? | Parkowanie: przed porannym szczytem jedna w holu, w innych porach rozłożone po budynku. |
| Nowoczesne budynki? | Destination dispatch: ludzie wpisują piętro docelowe już na korytarzu, więc dyspozytor może grupować jadących w te same miejsca. |
| Sytuacje awaryjne? | Tryb priorytetowy, który czyści przystanki i wysyła windy na parter. Ma pierwszeństwo przed dyspozytorem. |

## Uruchomienie

```sh
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn -q compile
java -cp target/classes greedy.ElevatorSystem
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest=ElevatorSystemTest
```

```
A is at 3 going UP, B is idle at 0
call UP at 2: cost for A 33 floors, for B 2 floors
sent: B  (A is nearer, but it has gone past and must reach 19 first)
pressed 12, 5, 9; stopped at [5, 9, 12]
```
