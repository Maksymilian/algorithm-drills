package collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import collection.MeetingRooms.Meeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingRoomsTest {

    @Test
    void roomsForOverlappingMeetings() {
        List<Meeting> meetings = List.of(new Meeting(0, 30), new Meeting(5, 10), new Meeting(15, 20));
        assertEquals(2, MeetingRooms.minRooms(meetings));
        assertEquals(2, MeetingRooms.minRoomsBySweep(meetings));
        assertFalse(MeetingRooms.canAttendAll(meetings));
    }

    @Test
    void meetingEndingAtTenFreesTheRoomForOneStartingAtTen() {
        List<Meeting> backToBack = List.of(new Meeting(9, 10), new Meeting(10, 11), new Meeting(11, 12));
        assertEquals(1, MeetingRooms.minRooms(backToBack));
        assertEquals(1, MeetingRooms.minRoomsBySweep(backToBack));
        assertTrue(MeetingRooms.canAttendAll(backToBack));
        assertEquals(0, MeetingRooms.minRooms(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Meeting(5, 5));
    }

    @Test
    void heapAgreesWithSweepAndWithCountingEveryMinute() {
        Random random = new Random(8);
        for (int round = 0; round < 300; round++) {
            List<Meeting> meetings = randomMeetings(random);
            int expected = mostMeetingsInOneMinute(meetings);
            assertEquals(expected, MeetingRooms.minRooms(meetings), meetings.toString());
            assertEquals(expected, MeetingRooms.minRoomsBySweep(meetings), meetings.toString());
        }
    }

    private static List<Meeting> randomMeetings(Random random) {
        List<Meeting> meetings = new ArrayList<>();
        int count = random.nextInt(10);
        for (int i = 0; i < count; i++) {
            int start = random.nextInt(25);
            meetings.add(new Meeting(start, start + 1 + random.nextInt(5)));
        }
        return meetings;
    }

    private static int mostMeetingsInOneMinute(List<Meeting> meetings) {
        int[] busy = new int[30];
        for (Meeting m : meetings) {
            for (int t = m.start(); t < m.end(); t++) {
                busy[t]++;
            }
        }
        return Arrays.stream(busy).max().orElse(0);
    }
}
