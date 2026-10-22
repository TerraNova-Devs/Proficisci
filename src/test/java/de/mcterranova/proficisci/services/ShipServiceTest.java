package de.mcterranova.proficisci.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShipServiceTest {

    private static Instant next(String now) {
        return ShipService.nextFreeTripExpiry(Instant.parse(now));
    }

    @Test
    void freeTripsRunOutAtTheNextMidnightInGermany() {
        // summer time, midnight in Germany is 22:00 UTC the day before
        assertEquals(Instant.parse("2026-10-21T22:00:00Z"), next("2026-10-21T21:59:00Z")); // 23:59
        assertEquals(Instant.parse("2026-10-22T22:00:00Z"), next("2026-10-21T22:00:00Z")); // midnight sharp
        assertEquals(Instant.parse("2026-10-21T22:00:00Z"), next("2026-10-21T02:00:00Z")); // 4:00
        // summer time ends in the night to the 25th, so that midnight is 23:00 UTC
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), next("2026-10-24T20:00:00Z")); // 22:00 on the 24th
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), next("2026-10-25T10:00:00Z")); // 11:00 on the 25th
        // summer time begins in the night to 28 March, midnight before it is 23:00 UTC, after it 22:00 UTC
        assertEquals(Instant.parse("2027-03-27T23:00:00Z"), next("2027-03-27T21:00:00Z")); // 22:00 on the 27th
        assertEquals(Instant.parse("2027-03-28T22:00:00Z"), next("2027-03-28T10:00:00Z")); // noon on the 28th
    }
}
