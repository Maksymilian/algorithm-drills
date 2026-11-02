# Zasady dla kodu w tym repozytorium

## Styl kodu Java

- **Po `if` zawsze nawiasy klamrowe**, także gdy ciało ma jedną instrukcję. Dotyczy też `else`.
  Instrukcja nigdy nie stoi w tej samej linii co warunek.

  Niedopuszczalne:
  ```java
  if (index < 0 || ++needed[index] > available[index]) return false;
  ```

  Poprawnie:
  ```java
  if (index < 0 || ++needed[index] > available[index]) {
      return false;
  }
  ```

- **Metody najwyżej 10 linii, idealnie do 5.** Liczymy linie ciała między klamrami metody, bez
  sygnatury, klamry zamykającej, pustych linii, komentarzy i Javadoca. Dłuższą metodę dzielimy na
  mniejsze, nazwane według tego, co robią (np. `countLetters`, `canBuild`, `pathTo`). Nie skracamy
  metod przez upychanie kilku instrukcji w jednej linii ani przez pomijanie klamer: to łamie
  regułę wyżej.

- **Lambdy idealnie jedno wyrażenie, najwyżej 3 linie.**
  - Najlepiej referencja do metody albo lambda-wyrażenie bez klamer:
    `Integer::sum`, `w -> new ArrayList<>()`, `e -> e.getValue() > 1`.
  - Lambda z blokiem `{ ... }` dłuższym niż 3 linie to sygnał, żeby wydzielić prywatną metodę
    i przekazać ją referencją (`this::process`, `Klasa::process`).
  - Linie lambdy wliczają się do limitu metody, w której stoi.

  Niedopuszczalne:
  ```java
  counts.forEach((word, count) -> {
      if (count > best) {
          best = count;
          winner = word;
      }
      seen.add(word);
  });
  ```

  Poprawnie:
  ```java
  counts.forEach(this::consider);
  ```

- **Metoda `main` w uproszczonej formie z Javy 25:** `void main()`, bez `public`, `static` i
  `String[] args`. Launcher tworzy wtedy obiekt klasy, więc klasa musi mieć konstruktor bez
  parametrów. Jeśli go nie ma (np. `LruCache(int capacity)`), piszemy `static void main()`.

  Niedopuszczalne:
  ```java
  public static void main(String[] args) {
  ```

  Poprawnie:
  ```java
  void main() {
  ```

- **Wypisywanie przez `IO`, nie `System.out`:** `IO.println(...)` i `IO.print(...)`. Klasa `IO` jest
  w `java.lang`, więc nie wymaga importu. `IO` nie ma `printf`, więc formatujemy napis przez
  `formatted`:
  ```java
  IO.println("A jest na %d piętrze".formatted(floor));   // zamiast System.out.printf("...%n", floor)
  ```

## Każde rozwiązanie

- **Zawsze walidujemy parametry publicznych metod**, na samym początku metody, z komunikatem po
  polsku. Wyjątek zależy od rodzaju błędu, tak jak w JDK:

  | Błąd | Wyjątek | Jak |
  |---|---|---|
  | `null` jako parametr albo element kolekcji | `NullPointerException` | `Objects.requireNonNull(x, "brak napisu")` |
  | zła wartość: spoza zakresu, pusta kolekcja, zły format | `IllegalArgumentException` | `if` z `throw` |
  | indeks spoza `0..size-1` | `IndexOutOfBoundsException` | `Objects.checkIndex(i, size)` |
  | poprawne parametry, ale obiekt w złym stanie | `IllegalStateException` | `if` z `throw` |
  | przepełnienie | `ArithmeticException` | `Math.addExact`, `Math.multiplyExact`, `Math.toIntExact` |

  `NullPointerException` rzucamy od razu przy wejściu do metody, a nie z głębi kodu. Wyjątek: gdy
  zadanie nadaje `null` znaczenie (np. `null` w siatce liczy się jako 0), opisujemy to w tabeli
  założeń. Nie zwracamy „magicznej” wartości (np. 0 albo −1) dla
  błędnego wejścia, jeśli może ona być też poprawnym wynikiem. Co jest błędem, a co poprawnym
  przypadkiem brzegowym (np. pusty napis), zapisujemy w tabeli założeń w notatce i w Javadocu klasy.

- **Zawsze szukamy przypadków brzegowych i pokrywamy je testami:** puste i jednoelementowe wejście,
  wartości skrajne (`Integer.MIN_VALUE`, `MAX_VALUE`, przepełnienie), wartości ujemne, duplikaty,
  `null` wewnątrz kolekcji, nieparzyste długości, duże dane (wydajność, głęboka rekurencja),
  Unicode (wielkie litery, polskie znaki, emoji). Gdzie się da, porównujemy wynik z prostym
  wzorcem (przegląd wszystkich możliwości) na losowych danych.

- **Zawsze refaktoryzujemy przed oddaniem:** czytelne nazwy zamiast skrótów, nazwane stałe zamiast
  magicznych liczb, warunki rozbite na nazwane zmienne lub metody, bez martwego kodu i nieużywanych
  importów, metody w limicie z sekcji wyżej. Gdy poprawiamy cudze rozwiązanie, wypisujemy, co
  zmieniliśmy i dlaczego.

## Dokumentacja

- **Cała dokumentacja po polsku:** notatki w `docs/`, README, Javadoc i komentarze w kodzie.
  Po angielsku zostają tylko identyfikatory (nazwy klas, metod, zmiennych, testów).

- **Javadoc tylko nad deklaracją `public class`.** Bez komentarzy `///` na metodach, polach,
  rekordach, typach wyliczeniowych i klasach zagnieżdżonych: to, co warto wiedzieć o metodach,
  opisujemy w dokumentacji klasy albo w notatce w `docs/`. Krótkie komentarze `//` w kodzie
  źródłowym są dozwolone tam, gdzie wyjaśniają coś nieoczywistego.

- **Testy bez dokumentacji i bez komentarzy:** ani `///`, ani `//`. Test ma się tłumaczyć sam:
  nazwą metody testowej i komunikatem asercji.

- **Javadoc w Markdown (JEP 467, Java 23+):** komentarze `///` zamiast `/** ... */` i znaczników
  HTML.

  | Zamiast | Piszemy |
  |---|---|
  | `/** ... */` | każda linia zaczyna się od `///` |
  | `{@code x}` | `` `x` `` |
  | `{@link Klasa}`, `{@link #metoda}` | `[Klasa]`, `[#metoda]` |
  | `<p>` | pusta linia `///` |
  | `<b>tekst</b>`, `<i>tekst</i>` | `**tekst**`, `_tekst_` |
  | `<ul><li>`, `<ol><li>` | `- ` i `1. ` |
  | `<pre>` | blok kodu w potrójnych odwrotnych apostrofach |
  | `<table>` | tabela Markdown |

  Znaki specjalne w zwykłym tekście trzeba uważać: `[x]` to w Markdown link do elementu programu,
  więc `best[i]` piszemy w odwrotnych apostrofach (`` `best[i]` ``), tak samo `*`.

  Niedopuszczalne:
  ```java
  /**
   * Najwięcej kamieni na drodze z {@code A} do {@code B}. <b>Pamięć</b> O(w).
   */
  ```

  Poprawnie:
  ```java
  /// Najwięcej kamieni na drodze z `A` do `B`. **Pamięć** O(w).
  ```
