package com.familyhub.demo;

import net.fortuna.ical4j.model.Recur;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Ical4jSpikeTest {

    @Test
    void weeklyTuesdayThursday_expandsDatesInRange() {
        // RRULE string without "RRULE:" prefix
        Recur<LocalDate> recur = new Recur<>("FREQ=WEEKLY;BYDAY=TU,TH");

        LocalDate seed = LocalDate.of(2025, 6, 3); // Tuesday
        LocalDate rangeStart = LocalDate.of(2025, 6, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 6, 15);

        List<LocalDate> dates = recur.getDates(seed, rangeStart, rangeEnd);

        // Should include Tuesdays and Thursdays in range:
        // Jun 3 (Tue), Jun 5 (Thu), Jun 10 (Tue), Jun 12 (Thu)
        assertThat(dates).containsExactly(
                LocalDate.of(2025, 6, 3),
                LocalDate.of(2025, 6, 5),
                LocalDate.of(2025, 6, 10),
                LocalDate.of(2025, 6, 12)
        );
    }

    @Test
    void dailyRecurrence_expandsCorrectly() {
        Recur<LocalDate> recur = new Recur<>("FREQ=DAILY");

        LocalDate seed = LocalDate.of(2025, 6, 1);
        LocalDate rangeStart = LocalDate.of(2025, 6, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 6, 5);

        List<LocalDate> dates = recur.getDates(seed, rangeStart, rangeEnd);

        assertThat(dates).containsExactly(
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 2),
                LocalDate.of(2025, 6, 3),
                LocalDate.of(2025, 6, 4),
                LocalDate.of(2025, 6, 5)
        );
    }

    @Test
    void monthlyRecurrence_expandsCorrectly() {
        Recur<LocalDate> recur = new Recur<>("FREQ=MONTHLY;BYDAY=1SU");

        LocalDate seed = LocalDate.of(2025, 6, 1); // First Sunday of June
        LocalDate rangeStart = LocalDate.of(2025, 6, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 9, 30);

        List<LocalDate> dates = recur.getDates(seed, rangeStart, rangeEnd);

        assertThat(dates).containsExactly(
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 7, 6),
                LocalDate.of(2025, 8, 3),
                LocalDate.of(2025, 9, 7)
        );
    }
}
