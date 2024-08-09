package de.mcterranova.proficisci.services;

import de.mcterranova.proficisci.database.BarrelDatabase;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShipService {
    private final BarrelDatabase barrelDatabase;

    public ShipService() throws SQLException {
        this.barrelDatabase = BarrelDatabase.getInstance();
    }

    public List<String> listShips() throws SQLException {
        return barrelDatabase.loadTeleportLocations().keySet().stream().collect(Collectors.toList());
    }

    public Location getShipLocation(String regionName) throws SQLException {
        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
        return locations.get(regionName);
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
