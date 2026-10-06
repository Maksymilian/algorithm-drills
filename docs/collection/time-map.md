# Magazyn klucz → wartość z historią

Kod: [`TimeMap.java`](../../src/java/collection/TimeMap.java),
testy: [`TimeMapTest.java`](../../test/java/collection/TimeMapTest.java).

## Treść

Zaprojektuj magazyn, który pamięta historię wartości:

- `set(key, value, t)` zapisuje wartość klucza w chwili `t`;
- `get(key, t)` zwraca wartość z ostatniego zapisu nie późniejszego niż `t`.

```
set(BTC, 60000, 10); set(BTC, 62000, 20); set(BTC, 58000, 30)
get(BTC, 25) -> 62000      get(BTC, 5) -> null
```

## Założenia

| | |
|---|---|
| Klucze | `String` |
| Czas | `int`; zapisy mogą przychodzić w dowolnej kolejności czasu |
| Ten sam czas | późniejszy zapis nadpisuje wcześniejszy |
| Brak wartości | `null` (nie ma klucza albo zapytanie sprzed pierwszego zapisu) |
| Dodatkowo | wszystkie zmiany klucza w przedziale czasu |

## Rozwiązanie

```
HashMap<String, TreeMap<Integer, String>>
  "BTC" -> {10: 60000, 20: 62000, 30: 58000}
```

`get` to jedno wywołanie: `versions.floorEntry(t)`, czyli największy czas ≤ t.

## Dlaczego takie struktury danych

| Podejście | `set` | `get` | Dlaczego nie |
|---|---|---|---|
| Jedna lista wszystkich zapisów | O(1) | O(n) | Przegląda wszystko |
| `HashMap` → `ArrayList` par, wyszukiwanie binarne | O(1) tylko przy rosnącym czasie; inaczej O(v) | O(log v) | Świetne, **jeśli** czasy rosną (np. logi). W ogólnym przypadku wstawianie w środek kosztuje O(v) |
| **`HashMap` → `TreeMap`** | **O(log v)** | **O(log v)** | **Wybrane: dowolna kolejność, gotowe `floorEntry` i `subMap`** |

- **`HashMap` na zewnątrz.** Po kluczu tylko szukamy, kolejność kluczy nie jest potrzebna. Daje
  O(1).
- **`TreeMap` w środku.** Drzewo czerwono-czarne, czyli zrównoważone drzewo BST. Ma operacje
  „najbliższy w dół / w górę” (`floorEntry`, `ceilingEntry`) i widoki przedziałów (`subMap`,
  `headMap`, `tailMap`), wszystkie w O(log v). Tego nie ma żadna kolekcja haszująca.
- **`computeIfAbsent`.** Tworzy drzewo przy pierwszym zapisie klucza, w jednej linijce i bez
  podwójnego szukania.
- **`unmodifiableNavigableMap` w `changes`.** `subMap` to widok na żywe drzewo. Bez opakowania
  wywołujący mógłby przez widok zmienić historię.

## Dopytania

- **Wiele wątków?** `ConcurrentHashMap` + `ConcurrentSkipListMap`. Skip-lista ma te same
  `floorEntry` i `subMap`, ale bez blokad.
- **Za dużo historii?** `versions.headMap(granica).clear()` usuwa stare wersje.
- **Wersje zamiast czasu (snapshoty)?** Ta sama struktura z licznikiem wersji zamiast czasu.
