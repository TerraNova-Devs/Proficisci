package de.mcterranova.proficisci.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipMapTest {

    private static final UUID AINCRAD = UUID.randomUUID();
    private static final UUID BURHAVE = UUID.randomUUID();
    private static final UUID CASTRELON = UUID.randomUUID();

    // routes as ShipService stores them, both ways; the spawn is no town
    private static final Map<String, Set<String>> ROUTES = Map.of(
            "aincrad", Set.of("burhave", "spawn"),
            "burhave", Set.of("aincrad", "castrelon"),
            "castrelon", Set.of("burhave"),
            "spawn", Set.of("aincrad"));
    private static final Map<String, UUID> TOWNS = Map.of(
            "aincrad", AINCRAD, "burhave", BURHAVE, "castrelon", CASTRELON);

    @Test
    void onlyRoutesBetweenTownsSwitchedOnEachOnce() {
        assertEquals(Set.of(), ShipMap.pairs(ROUTES, TOWNS, Set.of()));
        assertEquals(Set.of(), ShipMap.pairs(ROUTES, TOWNS, Set.of(AINCRAD)));
        assertEquals(Set.of(), ShipMap.pairs(ROUTES, TOWNS, Set.of(AINCRAD, CASTRELON)));
        assertEquals(Set.of(List.of("aincrad", "burhave")), ShipMap.pairs(ROUTES, TOWNS, Set.of(AINCRAD, BURHAVE)));
        assertEquals(
                Set.of(List.of("aincrad", "burhave"), List.of("burhave", "castrelon")),
                ShipMap.pairs(ROUTES, TOWNS, Set.of(AINCRAD, BURHAVE, CASTRELON)));
    }
}
