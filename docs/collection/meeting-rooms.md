# Ile sal potrzeba na spotkania

Kod: [`MeetingRooms.java`](../../src/java/collection/MeetingRooms.java),
testy: [`MeetingRoomsTest.java`](../../test/java/collection/MeetingRoomsTest.java).

## Treść

Dana jest lista spotkań z godziną rozpoczęcia i zakończenia. Ile najmniej sal potrzeba, żeby
żadne dwa spotkania w jednej sali się nie nakładały?

```
[0, 30), [5, 10), [15, 20)  ->  2 sale
```

## Założenia

| | |
|---|---|
| Spotkanie | przedział `[start, end)`, `start < end`; czas w minutach (`int`) |
| Styk | spotkanie kończące się o 10:00 zwalnia salę dla zaczynającego się o 10:00 |
| Kolejność | dowolna; nie zakładamy posortowania |
| Wynik | liczba sal (= największa liczba spotkań trwających naraz) |

## Rozwiązanie 1: sortowanie i kopiec końców

Bierzemy spotkania według początku. Kopiec minimalny trzyma godziny zakończenia spotkań w
zajętych salach. Jeśli najwcześniejsze zakończenie ≤ nowy start, ta sala jest wolna: zdejmujemy ją.
Potem wkładamy koniec nowego spotkania. Rozmiar kopca na końcu to liczba sal.

## Rozwiązanie 2: zamiatanie

Start to +1 trwające spotkanie, koniec to −1. `TreeMap` sumuje zmiany w tej samej chwili i oddaje
je w kolejności czasu. Odpowiedź to największa suma bieżąca. Koniec i start w tej samej chwili się
znoszą, co zgadza się z przedziałami `[start, end)`.

## Dlaczego taki algorytm

| Podejście | Czas | Komentarz |
|---|---|---|
| Licz trwające spotkania w każdej minucie | O(n · T) | Zależy od długości dnia, nie od liczby spotkań |
| Każda para spotkań | O(n²) | Liczba nakładających się par to nie liczba sal |
| **Sortowanie + kopiec końców** | **O(n log n)** | **Wybrane. Łatwo rozszerzyć o przydział konkretnych sal** |
| **Zamiatanie na `TreeMap`** | **O(n log n)** | **Najkrótszy kod. Ta sama technika liczy np. maks. obciążenie serwera** |
| Dwie posortowane tablice (starty, końce) + dwa wskaźniki | O(n log n) | Bez kolekcji, tylko tablice. Też bardzo dobre |

O(n log n) to dolna granica dla tego zadania, bo bez porządku czasowego się nie obejdzie.

## Dlaczego takie struktury danych

- **`PriorityQueue<Integer>` (kopiec minimalny) na końce.** Interesuje nas tylko *najwcześniej*
  zwalniana sala: `peek` w O(1), `poll` i `add` w O(log n). Posortowana lista wymagałaby
  wstawiania w środek, w O(n).
- **`TreeMap<Integer, Integer>` w zamiataniu.** Robi dwie rzeczy naraz: `merge` łączy zdarzenia z tej
  samej chwili, a iteracja idzie rosnąco po czasie. `HashMap` wymagałby osobnego sortowania kluczy.
- **`record Meeting`.** Niezmienny, a walidacja `start < end` siedzi w konstruktorze, więc
  błędnego spotkania nie da się utworzyć.
- **Kopia listy przed sortowaniem.** Nie zmieniamy listy wywołującego, a `List.of(...)` i tak jest
  niemodyfikowalna.

## Dopytania

- **Czy jedna osoba zdąży na wszystkie?** Posortuj i sprawdź sąsiadów (`canAttendAll`), O(n log n).
- **Która sala dla którego spotkania?** Drugi kopiec z numerami wolnych sal; przy zwolnieniu
  oddajemy numer.
- **Sal jest tylko k, spotkania czekają?** Symulacja zdarzeń z kopcem `(czas zwolnienia, sala)`.
