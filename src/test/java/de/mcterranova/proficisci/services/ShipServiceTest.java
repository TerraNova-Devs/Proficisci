package de.mcterranova.proficisci.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShipServiceTest {

    @Test
    void sameBannersSideBySideNearestGroupFirst() {
        // harbour -> banner and distance
        Map<String, String> banner = Map.of("a", "white", "b", "n1", "c", "white", "d", "n1", "e", "n2", "f", "spawn");
        Map<String, Double> blocks = Map.of("a", 100.0, "b", 50.0, "c", 300.0, "d", 400.0, "e", 200.0, "f", 250.0);
        // n1 from 50 on, white from 100, n2 from 200, spawn from 250; within each group by distance
        assertEquals(List.of("b", "d", "a", "c", "e", "f"),
                ShipService.byGroupThenDistance(banner.keySet(), banner::get, blocks::get));
    }

    @Test
    void groupsAsNearAreNotMixed() {
        Map<String, String> banner = Map.of("a", "n1", "b", "n2", "c", "n1", "d", "n2");
        Map<String, Double> blocks = Map.of("a", 100.0, "b", 100.0, "c", 300.0, "d", 200.0);
        assertEquals(List.of("a", "c", "b", "d"),
                ShipService.byGroupThenDistance(banner.keySet(), banner::get, blocks::get));
    }

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

    @Test
    void shipsSailTheShortWayRoundTheWorld() {
        // the overworld is 24000 wide, from -12000 to +12000
        assertEquals(5000, ShipService.wrap(5000, 24000));
        assertEquals(-5000, ShipService.wrap(-5000, 24000));
        // from x 11500 to x -11500: 1000 blocks on over the border, not 23000 back across the world
        assertEquals(1000, ShipService.wrap(-11500 - 11500, 24000));
        assertEquals(-1000, ShipService.wrap(11500 - -11500, 24000));
        // exactly half the world is as long either way
        assertEquals(12000, ShipService.wrap(12000, 24000));
        // in the nether and the end the border is so far out that nothing wraps
        assertEquals(-23000, ShipService.wrap(-23000, 59999968));
    }
}
