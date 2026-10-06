# Pierwsza unikalna litera bez pamięci dynamicznej

Kod: [`FirstUniqueLetter.java`](../../src/java/string/FirstUniqueLetter.java),
testy: [`FirstUniqueLetterTest.java`](../../test/java/string/FirstUniqueLetterTest.java).

## Treść

W napisie znajdź pierwszą literę, która występuje dokładnie raz. Nie alokuj pamięci dynamicznej.

```
"swiss"  ->  1 ('w')
```

## Założenia

| | |
|---|---|
| Alfabet | litery a–z; `A` i `a` to ta sama litera; inne znaki pomijamy |
| „Bez pamięci dynamicznej” | żadnego `new`, żadnej kolekcji: tylko zmienne lokalne typów prostych (na stosie) |
| Wynik | indeks w napisie albo −1 |

## Rozwiązanie

Dwie maski bitowe w zmiennych `int` (32 bity, z których używamy 26):

```java
repeated |= seen & bit;   // litera już była: teraz jest powtórzona
seen     |= bit;
```

Po pierwszym przejściu `seen & ~repeated` to zbiór liter unikalnych. Drugie przejście zwraca
pierwszy znak napisu z tego zbioru.

## Dlaczego maski bitowe

| Podejście | Czas | Pamięć | Dlaczego nie |
|---|---|---|---|
| `LinkedHashMap<Character, Integer>` | O(n) | sterta | Najczęstsza odpowiedź, ale łamie warunek zadania |
| `int[26]` liczników | O(n) | sterta | W Javie każda tablica to obiekt na stercie (`new`). W C/C++ `int counts[26]` leży na stosie i byłby w porządku |
| Dla każdej litery sprawdź resztę napisu | O(n²) | O(1) | Dobre dla dowolnego alfabetu, za wolne dla długich napisów |
| **Dwie maski `int`** | **O(n)** | **O(1), stos** | **Wybrane** |

Wystarczą dwa stany na literę: „widziana” i „powtórzona”. Nie trzeba dokładnej liczby wystąpień,
więc wystarczy jeden bit na stan.

## Dlaczego takie struktury danych

Żadne, i o to chodzi. Zmienne lokalne typów prostych żyją w ramce stosu (albo w rejestrach) i
znikają po wyjściu z metody, bez pracy dla garbage collectora. `charAt` niczego nie alokuje,
`toCharArray()` kopiowałoby napis.

## Dopytania

- **Pełne ASCII (128 znaków)?** Dwie pary zmiennych `long`: bity 0–63 i 64–127.
- **Dowolny Unicode?** Stała pamięć nie wystarczy: wersja O(n²) (`firstUniqueQuadratic`).
- **Strumień znaków, odpowiedź po każdym znaku?** `LinkedHashMap` albo kolejka kandydatów z
  licznikami. Z czoła kolejki zdejmujemy litery, które się powtórzyły.
