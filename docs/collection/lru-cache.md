# Pamięć podręczna LRU

Kod: [`LruCache.java`](../../src/java/collection/LruCache.java),
testy: [`LruCacheTest.java`](../../test/java/collection/LruCacheTest.java).

## Treść

Zaprojektuj cache o stałej pojemności z operacjami `get(key)` i `put(key, value)` w O(1). Gdy
cache jest pełny, wyrzuca element najdawniej używany (least recently used).

## Założenia

| | |
|---|---|
| Pojemność | stała, dodatnia, podana w konstruktorze |
| Użycie | `get` i `put` odświeżają element; `put` istniejącego klucza nadpisuje wartość |
| Brak klucza | `get` zwraca `null` |
| Wątki | jeden (wersje wielowątkowe w dopytaniach) |
| Koszt | `get` i `put` w O(1) |

## Rozwiązanie

Dwie struktury naraz:

```
HashMap: klucz -> węzeł
               |
head <-> [c] <-> [a] <-> [b] <-> tail
         najnowszy          najstarszy, wyrzucany pierwszy
```

- `get`: znajdź węzeł w mapie, wyjmij go z listy, wstaw na początek.
- `put` nowego klucza przy pełnym cache: usuń `tail.prev` z listy i z mapy, nowy węzeł wstaw na
  początek.

## Dlaczego taki algorytm

| Podejście | `get` | `put` przy pełnym | Dlaczego nie |
|---|---|---|---|
| `HashMap` + znacznik czasu w każdym wpisie | O(1) | O(n): szukanie najstarszego | Wyrzucanie przegląda wszystko |
| `HashMap` + `TreeMap<czas, klucz>` | O(log n) | O(log n) | Działa, ale nie O(1) |
| `HashMap` + `ArrayDeque` kluczy | O(n) | O(1) | Przeniesienie klucza na początek wymaga usunięcia ze środka kolejki |
| **`HashMap` + własna lista dwukierunkowa** | **O(1)** | **O(1)** | **Wybrane** |
| `LinkedHashMap(accessOrder = true)` | O(1) | O(1) | To samo w środku. Pokazać jako „w produkcji”, ale rekruter zwykle prosi o wersję ręczną |

## Dlaczego takie struktury danych

- **`HashMap<K, Node>`, nie `HashMap<K, V>`.** Mapa wskazuje węzeł, więc z klucza od razu
  dostajemy się do miejsca w liście, bez szukania.
- **Własna lista, a nie `java.util.LinkedList`.** `LinkedList` nie udostępnia swoich węzłów, więc
  `remove(obiekt)` musi go najpierw znaleźć, w O(n). Własny węzeł zna sąsiadów: wyjęcie to cztery
  przypisania.
- **Lista dwukierunkowa, nie jednokierunkowa.** Żeby wyjąć węzeł, trzeba poprawić `next`
  poprzednika, a jednokierunkowa lista go nie zna.
- **Dwa węzły-strażnicy (`head`, `tail`).** Lista nigdy nie jest naprawdę pusta, więc żadna operacja
  nie sprawdza `null` i nie ma przypadków brzegowych „pierwszy element” i „ostatni element”.

## Dopytania

- **Wiele wątków?** Najprościej `synchronized` na `get` i `put`. Uwaga: `get` też modyfikuje
  listę, więc `ReadWriteLock` nic nie da. Lepiej podzielić cache na segmenty z osobnymi
  blokadami. W produkcji: biblioteka Caffeine.
- **LFU (najrzadziej używany)?** Mapa klucz → licznik i mapa licznik → `LinkedHashSet` kluczy, plus
  zmienna z najmniejszym licznikiem. Wszystko w O(1).
- **Wygasanie po czasie (TTL)?** Znacznik czasu w węźle. Przy `get` sprawdzamy, czy wygasł, a
  osobny wątek sprząta w tle.
