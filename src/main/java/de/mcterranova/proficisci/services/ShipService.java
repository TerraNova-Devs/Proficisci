package de.mcterranova.proficisci.services;

import de.mcterranova.proficisci.database.BarrelDatabase;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class ShipService {
    private final BarrelDatabase barrelDatabase;

    private Map<String, Set<String>> shipRoutes;

    public ShipService() throws SQLException {
        this.barrelDatabase = BarrelDatabase.getInstance();
        initShipRoutes();
    }

    public List<String> listShips() throws SQLException {
        return new ArrayList<>(barrelDatabase.loadTeleportLocations().keySet());
    }

    public Location getShipLocation(String regionName) throws SQLException {
        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
        return locations.get(regionName);
    }

    public String getCurrentShip(Location currentLocation) throws SQLException {
        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();

        Location found = null;

        for (Location location : locations.values()) {
            double distance = currentLocation.distance(location);
            if(distance < 10)
                found = location;
        }

        return barrelDatabase.getRegionNameByLocation(found);
    }

    public Location getNearestShip(Location currentLocation) throws SQLException {
        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
        Location nearestLocation = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Location location : locations.values()) {
            double distance = currentLocation.distance(location);
            if(distance < 10)
                continue;
            if (distance < nearestDistance && !currentLocation.equals(location)) {
                nearestDistance = distance;
                nearestLocation = location;
            }
        }

        return nearestLocation;
    }

    public void initShipRoutes() throws SQLException {
        Map<String, Location> ships = barrelDatabase.loadTeleportLocations();
        this.shipRoutes = new LinkedHashMap<>();

        for (String shipName : ships.keySet()) {
            shipRoutes.put(shipName, new LinkedHashSet<>());
        }

        final double MAX_DISTANCE = 6000.0;

        for (Map.Entry<String, Location> entryA : ships.entrySet()) {
            String shipA = entryA.getKey();
            Location locA = entryA.getValue();

            for (Map.Entry<String, Location> entryB : ships.entrySet()) {
                String shipB = entryB.getKey();
                if (shipA.equals(shipB)) continue;
                Location locB = entryB.getValue();

                double distance = seaDistance(locA, locB);
                if (distance <= MAX_DISTANCE) {
                    shipRoutes.get(shipA).add(shipB);
                }
            }
        }

       for (Map.Entry<String, Location> entry : ships.entrySet()) {
            String shipName = entry.getKey();
            if (shipRoutes.get(shipName).isEmpty()) {
                String nearestShip = null;
                double nearestDistance = Double.MAX_VALUE;
                Location loc = entry.getValue();
                for (Map.Entry<String, Location> otherEntry : ships.entrySet()) {
                    if (otherEntry.getKey().equals(shipName)) continue;
                    double d = seaDistance(loc, otherEntry.getValue());
                    if (d < nearestDistance) {
                        nearestDistance = d;
                        nearestShip = otherEntry.getKey();
                    }
                }

                if (nearestShip != null) {
                    shipRoutes.get(shipName).add(nearestShip);
                    shipRoutes.get(nearestShip).add(shipName);
                }
            }
        }
    }

    /**
     * Blocks between two harbours by sea. Ships sail round the world: who leaves at the border on one side comes in
     * at the other (in the overworld at +12000 and -12000), on both axes, so towns at the edge have neighbours across
     * it too. The border comes from the world; in the nether and the end it is so far out that nothing wraps.
     * Infinite between worlds or without a location.
     */
    public static double seaDistance(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        double size = a.getWorld().getWorldBorder().getSize();
        double dx = wrap(b.getX() - a.getX(), size);
        double dz = wrap(b.getZ() - a.getZ(), size);
        double dy = b.getY() - a.getY();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The shorter way along one axis of a world {@code size} wide: {@code d}, or round the world the other way. */
    public static double wrap(double d, double size) {
        d %= size;
        if (d > size / 2) return d - size;
        if (d < -size / 2) return d + size;
        return d;
    }

    /**
     * {@code items} with the same {@code group} side by side: the groups by their nearest item, within a group by
     * {@code distance}. Two groups exactly as near are never mixed, and on a tie the item first by its own order.
     */
    public static <T extends Comparable<T>> List<T> byGroupThenDistance(Collection<T> items, Function<T, ?> group,
                                                                       ToDoubleFunction<T> distance) {
        Map<T, Object> groups = new HashMap<>();
        Map<T, Double> distances = new HashMap<>();
        Map<Object, Double> nearest = new HashMap<>();
        for (T item : items) {
            Object g = group.apply(item);
            double d = distance.applyAsDouble(item);
            groups.put(item, g);
            distances.put(item, d);
            nearest.merge(g, d, Math::min);
        }
        return items.stream()
                .sorted(Comparator.<T>comparingDouble(i -> nearest.get(groups.get(i)))
                        .thenComparing(i -> String.valueOf(groups.get(i)))
                        .thenComparingDouble(distances::get)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    public Set<String> getConnectedShips(String shipName) {
        return shipRoutes.getOrDefault(shipName, new LinkedHashSet<>());
    }

    public Map<String, Set<String>> getAllRoutes() {
        return shipRoutes;
    }

    public void teleportShip(Player player, String regionName) throws SQLException {
        Location loc = getShipLocation(regionName);
        if (loc != null) {
            player.teleport(loc);
        }
    }

    public void setShipName(String regionName, String name) throws SQLException {
        barrelDatabase.setShipName(regionName, name);
    }

    public String getShipName(String regionName) throws SQLException {
        return barrelDatabase.getShipName(regionName);
    }

    public void setShipOwner(String regionName, UUID owner) throws SQLException {
        barrelDatabase.setShipOwner(regionName, owner);
    }

    public UUID getShipOwner(String regionName) throws SQLException {
        return barrelDatabase.getShipOwner(regionName);
    }

    /** Free trips run out at midnight German time, with the reset of the votes, whatever the zone of the server. */
    private static final ZoneId FREE_TRIP_ZONE = ZoneId.of("Europe/Berlin");

    /** The next midnight in Germany after {@code now}. */
    public static Instant nextFreeTripExpiry(Instant now) {
        return now.atZone(FREE_TRIP_ZONE).toLocalDate().plusDays(1).atStartOfDay(FREE_TRIP_ZONE).toInstant();
    }

    /** Gives free trips until the next midnight and returns how many the player has now. */
    public int giveFreeTrips(UUID player, int trips) throws SQLException {
        Instant now = Instant.now();
        barrelDatabase.giveFreeTrips(player, trips, now.toEpochMilli(), nextFreeTripExpiry(now).toEpochMilli());
        return barrelDatabase.freeTrips(player, now.toEpochMilli()).orElse(0);
    }
}
