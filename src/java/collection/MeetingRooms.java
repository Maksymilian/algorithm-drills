package collection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

/// Najmniejsza liczba sal potrzebna na wszystkie spotkania.
///
/// **Treść.** Spotkanie trwa w przedziale `[start, end)`. Gdy jedno kończy się o 10:00,
/// drugie może zacząć się o 10:00 w tej samej sali. Ile sal trzeba, żeby żadne dwa spotkania w
/// jednej sali się nie nakładały? Dla `[0,30) [5,10) [15,20)` są potrzebne **2** sale.
///
/// **Pomysł 1: sortowanie i kopiec końców ([PriorityQueue]).** Bierzemy spotkania według
/// początku. Kopiec trzyma godziny zakończenia spotkań w zajętych salach, z najwcześniejszą na
/// szczycie. Jeśli najwcześniejsze zakończenie wypada nie później niż nowy start, sala jest wolna:
/// zdejmujemy ją z kopca. Potem wkładamy koniec nowego spotkania. Rozmiar kopca na końcu to
/// liczba sal.
///
/// **Pomysł 2: zamiatanie ([TreeMap]).** W chwili `start` liczba trwających spotkań
/// rośnie o 1, w chwili `end` maleje o 1. `TreeMap` sumuje zmiany w tej samej chwili i
/// oddaje je w kolejności czasu. Odpowiedź to największa suma bieżąca. Koniec i start w tej samej
/// chwili się znoszą, co zgadza się z przedziałami `[start, end)`.
///
/// **Złożoność.** Obie wersje O(n log n) czasu i O(n) pamięci.
///
/// **Dopytania.** Czy jedna osoba zdąży na wszystkie? Sortujemy i sprawdzamy sąsiadów, czyli
/// [#canAttendAll]. Która sala dla którego spotkania? Drugi kopiec z numerami wolnych sal.
public class MeetingRooms {

    public record Meeting(int start, int end) {
        public Meeting {
            if (start >= end) {
                throw new IllegalArgumentException("pusty przedział: " + start + ".." + end);
            }
        }
    }

    public static int minRooms(List<Meeting> meetings) {
        PriorityQueue<Integer> endTimes = new PriorityQueue<>();   // końce spotkań w zajętych salach
        for (Meeting m : sortedByStart(meetings)) {
            if (!endTimes.isEmpty() && endTimes.peek() <= m.start()) {
                endTimes.poll();   // najwcześniej zwalniana sala jest już wolna
            }
            endTimes.add(m.end());
        }
        return endTimes.size();
    }

    public static int minRoomsBySweep(List<Meeting> meetings) {
        int running = 0, most = 0;
        for (int change : changesInTimeOrder(meetings).values()) {
            running += change;
            most = Math.max(most, running);
        }
        return most;
    }

    private static TreeMap<Integer, Integer> changesInTimeOrder(List<Meeting> meetings) {
        TreeMap<Integer, Integer> change = new TreeMap<>();
        for (Meeting m : meetings) {
            change.merge(m.start(), 1, Integer::sum);
            change.merge(m.end(), -1, Integer::sum);
        }
        return change;
    }

    public static boolean canAttendAll(List<Meeting> meetings) {
        List<Meeting> byStart = sortedByStart(meetings);
        for (int i = 1; i < byStart.size(); i++) {
            if (byStart.get(i).start() < byStart.get(i - 1).end()) {
                return false;
            }
        }
        return true;
    }

    private static List<Meeting> sortedByStart(List<Meeting> meetings) {
        List<Meeting> byStart = new ArrayList<>(meetings);
        byStart.sort(Comparator.comparingInt(Meeting::start));
        return byStart;
    }

    void main() {
        List<Meeting> meetings = List.of(new Meeting(0, 30), new Meeting(5, 10), new Meeting(15, 20), new Meeting(100,200), new Meeting(112, 120));
        IO.println(minRooms(meetings));          // 2
        IO.println(minRoomsBySweep(meetings));   // 2
        IO.println(canAttendAll(meetings));      // false
    }
}
