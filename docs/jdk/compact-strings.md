# `String` to tablica bajtów: Compact Strings i ile oszczędza Latin-1

Notatka do [`src/java/jdk/CompactStrings.java`](../../src/java/jdk/CompactStrings.java), testy:
[`test/java/jdk/CompactStringsTest.java`](../../test/java/jdk/CompactStringsTest.java).

Do Javy 8 `String` trzymał znaki w `char[]`, czyli zawsze 2 bajty na znak. Od Javy 9 (JEP 254, Compact
Strings) trzyma je w `byte[]` i w jednym z dwóch kodowań, wybieranym przy tworzeniu napisu.

## Budowa

Pola instancji klasy `String` na JDK 25, odczytane refleksją (`CompactStrings.instanceFields()`):

| Pole | Typ | Rola |
|---|---|---|
| `value` | `byte[]` | znaki napisu |
| `coder` | `byte` | jak czytać `value`: `LATIN1` (0) albo `UTF16` (1) |
| `hash` | `int` | zapamiętany `hashCode()` |
| `hashIsZero` | `boolean` | czy policzony hash naprawdę wynosi 0 |

Pola `char[]` nie ma. Nazwę i typ pola można odczytać bez żadnych flag; dopiero odczyt _wartości_
prywatnego pola wymaga `--add-opens java.base/java.lang=ALL-UNNAMED`, więc kod z repozytorium się bez
tego obywa.

| Kodowanie | Kiedy | Bajtów na znak |
|---|---|---:|
| `LATIN1` | każdy znak napisu ma kod 0–255 (ISO-8859-1) | 1 |
| `UTF16` | choć jeden znak spoza Latin-1 | 2, dla **całego** napisu |

**Polski tekst.** Z polskich liter w Latin-1 jest tylko `ó` (U+00F3). `ą ć ę ł ń ś ź ż` (i wielkie
odpowiedniki) już nie, więc „zażółć” albo dowolne zdanie z którąkolwiek z nich to UTF-16.

## Jak wykazać, że Latin-1 zmniejsza zużycie pamięci

### 1. Licznik alokacji wątku: dokładnie, co do bajta

`com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes()` mówi, ile bajtów zaalokował bieżący
wątek. Różnica przed i po utworzeniu napisu to rozmiar obiektu `String` i jego tablicy `byte[]`
(`CompactStrings.allocatedBytes`). Kod jest najpierw rozgrzewany, a wynik trafia do pola `volatile`,
żeby JIT nie mógł usunąć alokacji:

```java
long before = THREADS.getCurrentThreadAllocatedBytes();
sink = action.get();
return THREADS.getCurrentThreadAllocatedBytes() - before;
```

Wynik `java -cp target/classes jdk.CompactStrings` (JDK 25.0.1):

| Napis | domyślnie | `-XX:-CompactStrings` |
|---|---:|---:|
| 1000 × `a` | **1040 B** | 2040 B |
| 1000 × `ó` (jest w Latin-1) | **1040 B** | 2040 B |
| 1000 × `ą` (spoza Latin-1) | 2040 B | 2040 B |
| `a` + 999 × `a` | **1040 B** | 2040 B |
| `ą` + 999 × `a` | 2040 B | 2040 B |

Skąd dokładnie te liczby: obiekt `String` to 24 B (12 B nagłówka, referencja 4 B, `hash` 4 B, `coder` i
`hashIsZero` po 1 B, wyrównane do 8), a `byte[1000]` to 16 B nagłówka i 1000 B danych. W Latin-1:
24 + 16 + 1000 = 1040. W UTF-16: 24 + 16 + 2000 = 2040. Kompaktowe nagłówki obiektów z JDK 25
(`-XX:+UseCompactObjectHeaders`) dają dla tych napisów te same sumy, bo oszczędność zjada wyrównanie.

Wiersz `ą + 999 × a` to najważniejszy wniosek: **jedna litera spoza Latin-1 podwaja pamięć całego
napisu**, nie tylko swojego znaku. Testy sprawdzają to różnicami, które nie zależą od rozmiaru nagłówków:
`"a".repeat(2000)` kosztuje dokładnie 1000 B więcej niż `"a".repeat(1000)`, a `"ą".repeat(2000)` 2000 B
więcej niż `"ą".repeat(1000)`.

### 2. To samo z wyłączonym `-XX:-CompactStrings`

Flaga przywraca zachowanie z Javy 8: każdy napis w UTF-16. Ten sam program zużywa wtedy 2040 B na napis
z samych `a`. Test `withCompactStringsOffLatin1TextTakesTwoBytesPerCharacterToo` uruchamia osobną JVM z
tą flagą i sprawdza, że tekst w Latin-1 kosztuje wtedy tyle samo co tekst z `ą`. To najprostszy dowód, że
oszczędność daje właśnie kodowanie Latin-1, a nie coś innego.

### 3. Cała sterta: histogram klas

100 000 napisów po 100 znaków trzymanych w liście, potem `jcmd <pid> GC.class_histogram`:

| Tekst | `[B` (tablice bajtów) | `java.lang.String` |
|---|---:|---:|
| same `a`, domyślnie | **12,4 MB** | 2,6 MB |
| z `ą`, domyślnie | 22,0 MB | 2,6 MB |
| same `a`, `-XX:-CompactStrings` | 21,8 MB | 2,5 MB |

Na napis to 120 B wobec 216 B tablicy (`byte[100]` i `byte[200]` z nagłówkiem i wyrównaniem). Różnica
9,6 MB na 100 000 napisów zgadza się co do kilobajta z różnicą z punktu 1. Na tym samym histogramie
widać, że tablice `[C` (znaków) w ogóle nie służą napisom.

## Co z tego wynika

- **Tekst angielski, identyfikatory, JSON z kluczami ASCII, liczby i daty zajmują w pamięci o połowę
  mniej niż w Javie 8.** Dla aplikacji, w których napisy to duża część sterty, to realna oszczędność
  pamięci i pracy GC.
- **Polski tekst prawie zawsze jest UTF-16**, bo wystarczy jedna litera z ogonkiem. Na produkcji z
  polskimi danymi oszczędność dotyczy więc głównie kluczy, identyfikatorów i liczb, a nie treści.
- **Kodowanie wybiera się przy tworzeniu napisu**, a napis jest niezmienny. `"ą" + tekst` tworzy nowy
  napis UTF-16, a wycięcie z niego fragmentu bez `ą` (`substring`) daje znowu Latin-1.
- **`length()` liczy jednostki UTF-16 (`char`), a nie bajty**, niezależnie od kodowania wewnętrznego.
  Kodowanie wewnętrzne nie ma też nic wspólnego z UTF-8 na dysku i w sieci: `getBytes(UTF_8)` przelicza
  napis zawsze.
- **Tworzenie napisu z `char[]` kosztuje więcej, niż widać.** `new String(new char[1000])` zaalokowało
  3056 B, czyli o 2016 B więcej niż sam wynik: konstruktor najpierw próbuje skompresować znaki do Latin-1 i
  potrzebuje na to tymczasowej tablicy. `repeat`, sklejanie przez `+` i `StringBuilder.toString()`
  alokują tylko wynik.
- **Wyłączanie `CompactStrings` ma sens tylko wtedy, gdy prawie wszystkie napisy i tak są UTF-16**
  (np. tekst chiński): oszczędza się wtedy sprawdzanie, czy napis da się skompresować. Dla typowej
  aplikacji to podwojenie pamięci napisów, jak w tabelach wyżej.

## Uruchomienie

```sh
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest=CompactStringsTest
java -cp target/classes jdk.CompactStrings
java -XX:-CompactStrings -cp target/classes jdk.CompactStrings
jcmd <pid> GC.class_histogram | grep -E " \[B | java.lang.String "
```
