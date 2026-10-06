package string;

/// Pierwsza litera, która występuje w napisie dokładnie raz, bez alokowania pamięci dynamicznej.
///
/// **Treść.** W `"swiss"` pierwszą unikalną literą jest `w`, na pozycji 1.
///
/// **Pomysł.** Zwykle liczymy wystąpienia w `int[26]` albo w `HashMap`, ale w Javie
/// to obiekt na stercie. Wystarczą dwie zmienne typu `int`, czyli 32 bity, z których
/// używamy 26:
///
/// - `seen`: bit litery jest ustawiony, jeśli litera wystąpiła co najmniej raz;
///
/// - `repeated`: bit jest ustawiony, jeśli litera wystąpiła co najmniej dwa razy.
///
/// Po pierwszym przejściu `seen & ~repeated` to litery unikalne. Drugie przejście znajduje
/// pierwszą z nich w kolejności napisu.
///
/// **Złożoność.** O(n) czasu, O(1) pamięci: dwie zmienne lokalne na stosie, zero obiektów.
/// Wielkość liter nie ma znaczenia. Znaki spoza a–z pomijamy.
public class FirstUniqueLetter {

    public static int firstUnique(String s) {
        int unique = uniqueLetters(s);
        for (int i = 0; i < s.length(); i++) {
            if ((bit(s.charAt(i)) & unique) != 0) {
                return i;
            }
        }
        return -1;
    }

    private static int uniqueLetters(String s) {
        int seen = 0, repeated = 0;
        for (int i = 0; i < s.length(); i++) {
            int bit = bit(s.charAt(i));
            repeated |= seen & bit;   // już była: teraz jest powtórzona
            seen |= bit;
        }
        return seen & ~repeated;
    }

    private static int bit(char c) {
        char lower = Character.toLowerCase(c);
        return lower >= 'a' && lower <= 'z' ? 1 << (lower - 'a') : 0;
    }

    void main() {
        IO.println(firstUnique("swiss"));       // 1 ('w')
        IO.println(firstUnique("Aabbc"));       // 4 ('c'), bo 'A' i 'a' to ta sama litera
        IO.println(firstUnique("aabb"));        // -1
    }
}
