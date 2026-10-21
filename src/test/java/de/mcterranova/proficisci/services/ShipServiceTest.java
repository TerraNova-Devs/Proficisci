package de.mcterranova.proficisci.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShipServiceTest {

    private static Instant next(String now) {
        return ShipService.nextFreeTripExpiry(Instant.parse(now));
    }

    @Test
    void freeTripsRunOutAtTheNextFourOClockInGermany() {
        // summer time, 4:00 in Germany is 02:00 UTC
        assertEquals(Instant.parse("2026-10-21T02:00:00Z"), next("2026-10-21T01:59:00Z")); // 3:59
        assertEquals(Instant.parse("2026-10-22T02:00:00Z"), next("2026-10-21T02:00:00Z")); // 4:00 sharp
        assertEquals(Instant.parse("2026-10-22T02:00:00Z"), next("2026-10-21T21:00:00Z")); // 23:00
        // the night summer time ends, 4:00 is then 03:00 UTC
        assertEquals(Instant.parse("2026-10-25T03:00:00Z"), next("2026-10-24T20:00:00Z"));
        // the night summer time begins, 4:00 is then 02:00 UTC
        assertEquals(Instant.parse("2027-03-28T02:00:00Z"), next("2027-03-27T21:00:00Z"));
    }
}
