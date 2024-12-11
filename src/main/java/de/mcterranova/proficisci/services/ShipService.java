package de.mcterranova.proficisci.services;

import de.mcterranova.proficisci.database.BarrelDatabase;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class ShipService {
    private final BarrelDatabase barrelDatabase;

    private Map<String, Set<String>> shipRoutes;

    public ShipService() throws SQLException {
        this.barrelDatabase = BarrelDatabase.getInstance();
        initShipRoutes();
    }

    public List<String> listShips() throws SQLException {
        return barrelDatabase.loadTeleportLocations().keySet().stream().collect(Collectors.toList());
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

                double distance = locA.distance(locB);
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
                    double d = loc.distance(otherEntry.getValue());
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
}
