# Zrównoważone nawiasy: stos na `ArrayDeque`, a nie porównanie końców

Kod: [`BalancedBrackets.java`](../../src/java/string/BalancedBrackets.java),
testy: [`BalancedBracketsTest.java`](../../test/java/string/BalancedBracketsTest.java).

**Zadanie (HackerRank „Balanced Brackets”).** Napis z nawiasów `(){}[]`. Zwróć `YES`, jeśli każdy
nawias ma parę tego samego typu, a zawartość każdej pary też jest zrównoważona; inaczej `NO`.
Przykłady: `{[()]}` → `YES`, `{[(])}` → `NO`.

## Założenia

| Pytanie | Przyjęte |
|---|---|
| Pusty napis | `YES`: nic nie jest niedopasowane |
| Znak spoza `(){}[]` | pomijany: metoda sprawdza nawiasy także w tekście i kodzie (`f(a[0]) { }` → `YES`) |
| `null` | `IllegalArgumentException` (a nie `UnsupportedOperationException`: to błędny argument, a nie brak operacji) |
| Głębokość zagnieżdżenia | dowolna, ogranicza ją tylko sterta |

## Dlaczego porównanie pierwszego z ostatnim nie działa

Pierwsza wersja zdejmowała znak z początku i z końca i sprawdzała, czy pasują. To założenie, że
napis jest **lustrem**: `{[()]}`. Pary mogą jednak stać obok siebie:

```
{(([])[])[]}[]
^            ^   '{' z ']' → NO, a poprawna odpowiedź to YES
```

Nawias zamykający paruje się z **ostatnim niezamkniętym otwierającym**, który może być gdziekolwiek
wcześniej. Ostatni włożony, pierwszy wyjęty: stos. Wersja z porównywaniem końców ma jeszcze dwa
błędy: przy nieparzystej długości środkowy znak zostaje (`while (size > 1)`), więc `(` daje `YES`,
a `)(` paruje `)` z `(` przez mapę tylko dlatego, że `get(')')` zwraca `null`, czyli tu akurat
`NO`, ale przypadkiem.

## Rozwiązanie

1. Otwierający: `push`.
1. Zamykający: `poll`; jeśli stos był pusty (`null`) albo typ inny → `NO`.
1. Na końcu: `YES` tylko przy pustym stosie (`(()` zostawia `(`).

Mapa `zamykający → otwierający` daje oba sprawdzenia: `containsValue(c)` to „otwierający”,
`get(c) == null` to „nie nawias”, więc znak jest pomijany. To zastępuje osobne wyrażenie
regularne, które przechodziło napis drugi raz.

**Pomijać czy odrzucać inne znaki?** Pomijanie robi z metody ogólne narzędzie (nawiasy w kodzie,
we wzorach). Koszt: literówka w danych, które miały zawierać same nawiasy, przechodzi po cichu.
W zadaniu wejście gwarantuje tylko `(){}[]`, więc wynik się nie zmienia.

Czas O(n), pamięć O(n) (same otwierające). Szybkiego filtra „nieparzysta długość → `NO`” tu nie
ma i być nie może: przy pomijanych znakach `(a)` ma długość 3 i jest zrównoważone.

## Jak długi może być napis i czy ogranicza to stos

**Stos wątku tu nie gra roli.** Domyślny rozmiar stosu wątku (`-Xss`, flaga `ThreadStackSize`) to
**1024 KB** na Linuksie x64 (sprawdzone: `java -XX:+PrintFlagsFinal -version`, JDK 25). Ma on
znaczenie tylko dla **rekurencji**: rekurencyjna wersja (jedno wywołanie na poziom zagnieżdżenia)
wywraca się `StackOverflowError` po kilkudziesięciu tysiącach poziomów. `ArrayDeque` trzyma
elementy na **stercie**, więc milion poziomów przechodzi (test
`handlesMillionLevelsOfNestingWithoutStackOverflow`).

**Długość `String`** ogranicza tablica pod spodem: indeks to `int`, więc co najwyżej około
2³¹ − 1 elementów. Nawiasy to znaki Latin-1, więc dzięki compact strings to `byte[]`, około
2 mld znaków i 2 GB. W praktyce wcześniej kończy się sterta: domyślnie `-Xmx` to 1/4 pamięci RAM.

**Ile kosztuje stos `ArrayDeque<Character>`.** `Character` dla wartości do 127 pochodzi z pamięci
podręcznej `Character.valueOf`, więc nie tworzy obiektów, ale każde pole tablicy deque to
referencja (4 B przy compressed oops) zamiast 1–2 B. Dla napisów rzędu miliardów znaków lepszy
byłby własny stos `char[]` albo `byte[]` z indeksem szczytu. W zadaniu `n ≤ 1000`, więc nie warto.

## Dopytania

- **Tylko jeden typ nawiasu:** stos niepotrzebny, wystarczy licznik (nigdy ujemny, na końcu 0), O(1)
  pamięci.
- **Najdłuższy zrównoważony fragment:** stos indeksów zamiast znaków.
- **Nawiasy w literałach i komentarzach (`"("`, `// )`):** pomijanie znaków nie wystarczy, trzeba
  śledzić stan „jestem w napisie / komentarzu”.
