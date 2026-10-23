package de.mcterranova.proficisci.heroicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleBiFunction;
import org.junit.jupiter.api.Test;

class ShipMapTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID X = UUID.randomUUID(); // a town with its button off

    /** Routes both ways, as ShipService stores them; each argument is "a-b". */
    private static Map<String, Set<String>> routes(String... legs) {
        Map<String, Set<String>> routes = new HashMap<>();
        for (String leg : legs) {
            String[] h = leg.split("-");
            routes.computeIfAbsent(h[0], k -> new HashSet<>()).add(h[1]);
            routes.computeIfAbsent(h[1], k -> new HashSet<>()).add(h[0]);
        }
        return routes;
    }

    /**
     * Blocks between harbours from their x and z, each harbour as "name x z"; as in ShipMap.sync, infinite for a
     * harbour without a location.
     */
    private static ToDoubleBiFunction<String, String> at(String... harbours) {
        Map<String, double[]> xz = new HashMap<>();
        for (String h : harbours) {
            String[] p = h.split(" ");
            xz.put(p[0], new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2])});
        }
        return (a, b) -> !xz.containsKey(a) || !xz.containsKey(b)
                ? Double.POSITIVE_INFINITY
                : Math.hypot(xz.get(a)[0] - xz.get(b)[0], xz.get(a)[1] - xz.get(b)[1]);
    }

    private static final ToDoubleBiFunction<String, String> FLAT = (a, b) -> 1000;

    @Test
    void directRoutesAsBefore() {
        Map<String, Set<String>> routes = routes("a-b", "b-c", "a-spawn");
        Map<String, UUID> towns = Map.of("a", A, "b", B, "c", C);
        assertEquals(Set.of(), ShipMap.segments(routes, FLAT, towns, Set.of()));
        assertEquals(Set.of(), ShipMap.segments(routes, FLAT, towns, Set.of(A)));
        assertEquals(Set.of(List.of("a", "b")), ShipMap.segments(routes, FLAT, towns, Set.of(A, B)));
        assertEquals(Set.of(List.of("a", "b"), List.of("b", "c")), ShipMap.segments(routes, FLAT, towns, Set.of(A, B, C)));
    }

    @Test
    void overOneHarbour() {
        Map<String, Set<String>> routes = routes("a-x", "x-b");
        Map<String, UUID> towns = Map.of("a", A, "b", B, "x", X);
        assertEquals(Set.of(List.of("a", "x"), List.of("b", "x")), ShipMap.segments(routes, FLAT, towns, Set.of(A, B)));
    }

    @Test
    void overTwoHarbours() {
        Map<String, Set<String>> routes = routes("a-x", "x-y", "y-b");
        Map<String, UUID> towns = Map.of("a", A, "b", B, "x", X, "y", X);
        assertEquals(
                Set.of(List.of("a", "x"), List.of("x", "y"), List.of("b", "y")),
                ShipMap.segments(routes, FLAT, towns, Set.of(A, B)));
    }

    @Test
    void overAHarbourOutsideATown() {
        Map<String, Set<String>> routes = routes("a-spawn", "spawn-b");
        Map<String, UUID> towns = Map.of("a", A, "b", B); // the spawn is no town
        assertEquals(
                Set.of(List.of("a", "spawn"), List.of("b", "spawn")),
                ShipMap.segments(routes, FLAT, towns, Set.of(A, B)));
    }

    @Test
    void fewestTripsFirstThenFewestBlocks() {
        // two ways of 2 trips, over x (far) and over y (near), and a straight one of 3 trips, shortest in blocks
        Map<String, Set<String>> routes = routes("a-x", "x-b", "a-y", "y-b", "a-p", "p-q", "q-b");
        Map<String, UUID> towns = Map.of("a", A, "b", B);
        ToDoubleBiFunction<String, String> blocks =
                at("a 0 0", "b 10000 0", "x 5000 4000", "y 5000 1000", "p 3000 0", "q 6000 0");
        assertEquals(
                Set.of(List.of("a", "y"), List.of("b", "y")),
                ShipMap.segments(routes, blocks, towns, Set.of(A, B)));
    }

    @Test
    void noWayNoLine() {
        Map<String, Set<String>> routes = routes("a-x", "b-y");
        Map<String, UUID> towns = Map.of("a", A, "b", B);
        assertEquals(Set.of(), ShipMap.segments(routes, FLAT, towns, Set.of(A, B)));
    }

    @Test
    void harbourWithoutLocationIsNoWay() {
        Map<String, Set<String>> routes = routes("a-x", "x-b");
        Map<String, UUID> towns = Map.of("a", A, "b", B);
        // x has no location: no way over it, and nothing throws
        assertEquals(Set.of(), ShipMap.segments(routes, at("a 0 0", "b 8000 0"), towns, Set.of(A, B)));
        // counter-check: with its location the way over x is there
        assertEquals(
                Set.of(List.of("a", "x"), List.of("b", "x")),
                ShipMap.segments(routes, at("a 0 0", "b 8000 0", "x 4000 0"), towns, Set.of(A, B)));
    }

    @Test
    void sharedLegOnce() {
        Map<String, Set<String>> routes = routes("a-h", "b-h", "c-h");
        Map<String, UUID> towns = Map.of("a", A, "b", B, "c", C);
        assertEquals(
                Set.of(List.of("a", "h"), List.of("b", "h"), List.of("c", "h")),
                ShipMap.segments(routes, FLAT, towns, Set.of(A, B, C)));
    }

    @Test
    void twoHarboursOfOneTownAsBefore() {
        Map<String, UUID> towns = Map.of("a1", A, "a2", A);
        // a direct route between them is drawn, as before
        assertEquals(Set.of(List.of("a1", "a2")), ShipMap.segments(routes("a1-a2"), FLAT, towns, Set.of(A)));
        // a way over another harbour is not, as before
        assertEquals(Set.of(), ShipMap.segments(routes("a1-x", "x-a2"), FLAT, towns, Set.of(A)));
    }

    /** The pieces of a leg in the overworld: 24000 wide around 0.5 0.5, so the border is at -11999.5 and 12000.5. */
    private static List<List<Double>> pieces(double x0, double z0, double x1, double z1) {
        return ShipMap.pieces(x0, z0, x1, z1, 0.5, 0.5, 24000).stream()
                .map(p -> List.of(p[0], p[1], p[2], p[3]))
                .toList();
    }

    @Test
    void legWithinTheWorldInOnePiece() {
        assertEquals(List.of(List.of(100.5, 200.5, 3100.5, -800.5)), pieces(100.5, 200.5, 3100.5, -800.5));
    }

    @Test
    void legOverTheBorderUpToItAndOnFromTheOtherSide() {
        // east to west: from x 11500 out at 12000.5, in again at -11999.5, on to x -11500
        assertEquals(
                List.of(List.of(11500.0, 0.0, 12000.5, 0.0), List.of(-11999.5, 0.0, -11500.0, 0.0)),
                pieces(11500, 0, -11500, 0));
        // north to south the same on z
        assertEquals(
                List.of(List.of(50.0, -11500.0, 50.0, -11999.5), List.of(50.0, 12000.5, 50.0, 11500.0)),
                pieces(50, -11500, 50, 11500));
    }

    @Test
    void legOverACornerInThreePieces() {
        // from near the north-east corner to near the south-west one, the short way over the corner:
        // first over the north border, then over the east one
        List<List<Double>> p = pieces(11800, -11900, -11800, 11800);
        assertEquals(3, p.size());
        assertEquals(List.of(11800.0, -11900.0), p.get(0).subList(0, 2));
        assertEquals(-11999.5, p.get(0).get(3), 1e-9);
        assertEquals(12000.5, p.get(1).get(1), 1e-9);
        assertEquals(12000.5, p.get(1).get(2), 1e-9);
        assertEquals(-11999.5, p.get(2).get(0), 1e-9);
        assertEquals(List.of(-11800.0, 11800.0), p.get(2).subList(2, 4));
        // every piece lies inside the world
        for (List<Double> piece : p) {
            for (double c : piece) assertTrue(c >= -11999.5 && c <= 12000.5, piece.toString());
        }
    }
}
