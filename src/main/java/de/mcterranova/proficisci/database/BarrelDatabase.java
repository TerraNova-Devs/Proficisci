package de.mcterranova.proficisci.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BarrelDatabase {
    private static BarrelDatabase instance;
    private final HikariCPDatabase hikariCPDatabase;

    private BarrelDatabase(HikariCPDatabase hikariCPDatabase) {
        this.hikariCPDatabase = hikariCPDatabase;
    }

    public static synchronized BarrelDatabase getInstance() throws SQLException {
        if (instance == null) {
            instance = new BarrelDatabase(HikariCPDatabase.getInstance());
        }
        return instance;
    }

    public Map<String, Location> loadTeleportLocations() throws SQLException {
        Map<String, Location> locations = new HashMap<>();
        String query = "SELECT region_name, world, x, y, z, name, owner FROM barrel_locations";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String regionName = rs.getString("region_name");
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                locations.put(regionName, loc);
            }
        }

        return locations;
    }

    public void saveBarrelLocation(Location loc, String regionName, String name, UUID owner) throws SQLException {
        String insert = "INSERT INTO barrel_locations (region_name, world, x, y, z, name, owner) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setString(1, regionName);
            stmt.setString(2, loc.getWorld().getName());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setString(6, name);
            stmt.setString(7, owner.toString());
            stmt.executeUpdate();
        }
    }

    public boolean isSpecialBarrelLocation(Location loc) throws SQLException {
        String query = "SELECT COUNT(*) FROM barrel_locations WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setDouble(2, loc.getX());
            stmt.setDouble(3, loc.getY());
            stmt.setDouble(4, loc.getZ());
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    public void deleteBarrelLocation(Location loc) throws SQLException {
        String delete = "DELETE FROM barrel_locations WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(delete)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setDouble(2, loc.getX());
            stmt.setDouble(3, loc.getY());
            stmt.setDouble(4, loc.getZ());
            stmt.executeUpdate();
        }
    }

    public void setShipName(String regionName, String name) throws SQLException {
        String update = "UPDATE barrel_locations SET name = ? WHERE region_name = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(update)) {
            stmt.setString(1, name);
            stmt.setString(2, regionName);
            stmt.executeUpdate();
        }
    }

    public String getShipName(String regionName) throws SQLException {
        String query = "SELECT name FROM barrel_locations WHERE region_name = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, regionName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        }
        return null;
    }

    public void setShipOwner(String regionName, UUID owner) throws SQLException {
        String update = "UPDATE barrel_locations SET owner = ? WHERE region_name = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(update)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, regionName);
            stmt.executeUpdate();
        }
    }

    public UUID getShipOwner(String regionName) throws SQLException {
        String query = "SELECT owner FROM barrel_locations WHERE region_name = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, regionName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("owner"));
            }
        }
        return null;
    }

    /** Player -> the towns where they switched the ship button on. */
    public Map<UUID, Set<UUID>> loadRouteButtons() throws SQLException {
        Map<UUID, Set<UUID>> on = new HashMap<>();
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT player, town FROM ship_route_buttons");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                on.computeIfAbsent(UUID.fromString(rs.getString("player")), p -> new HashSet<>())
                        .add(UUID.fromString(rs.getString("town")));
            }
        }
        return on;
    }

    public void setRouteButton(UUID player, UUID town, boolean on) throws SQLException {
        String sql = on
                ? "INSERT IGNORE INTO ship_route_buttons (player, town) VALUES (?, ?)"
                : "DELETE FROM ship_route_buttons WHERE player = ? AND town = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.toString());
            stmt.setString(2, town.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * Adds free trips valid until {@code expires}; trips that ran out before {@code now} are not added to.
     * The update reads expires before it sets it, so the old value decides.
     */
    public void giveFreeTrips(UUID player, int trips, long now, long expires) throws SQLException {
        String sql = "INSERT INTO free_trips (player, trips, expires) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE trips = IF(expires > ?, trips + VALUES(trips), VALUES(trips)), " +
                "expires = VALUES(expires)";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.toString());
            stmt.setInt(2, trips);
            stmt.setLong(3, expires);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        }
    }

    /** Uses up one free trip if the player has one left at {@code now}; in one statement, so never twice. */
    public boolean useFreeTrip(UUID player, long now) throws SQLException {
        String sql = "UPDATE free_trips SET trips = trips - 1 WHERE player = ? AND trips > 0 AND expires > ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.toString());
            stmt.setLong(2, now);
            return stmt.executeUpdate() == 1;
        }
    }

    /** The free trips the player has left at {@code now}. */
    public int freeTrips(UUID player, long now) throws SQLException {
        String sql = "SELECT trips FROM free_trips WHERE player = ? AND expires > ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.toString());
            stmt.setLong(2, now);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("trips") : 0;
        }
    }

    public boolean regionHasBarrel(String regionName) throws SQLException {
        String query = "SELECT COUNT(*) FROM barrel_locations WHERE region_name = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, regionName);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    public String getRegionNameByLocation(Location loc) throws SQLException {
        String query = "SELECT region_name FROM barrel_locations WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = hikariCPDatabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setDouble(2, loc.getX());
            stmt.setDouble(3, loc.getY());
            stmt.setDouble(4, loc.getZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("region_name");
            }
        }
        return null;
    }

}
