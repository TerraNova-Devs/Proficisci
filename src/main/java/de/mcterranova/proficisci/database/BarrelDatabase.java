package de.mcterranova.proficisci.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BarrelDatabase {

    private final HikariCPDatabase hikariCPDatabase;

    public BarrelDatabase(HikariCPDatabase hikariCPDatabase) {
        this.hikariCPDatabase = hikariCPDatabase;
    }

    public Map<String, Location> loadTeleportLocations() throws SQLException {
        Map<String, Location> locations = new HashMap<>();
        String query = "SELECT region_name, world, x, y, z FROM barrel_locations";
        try (Connection conn = hikariCPDatabase.dataSource.getConnection();
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

    public void saveBarrelLocation(Location loc, String regionName) throws SQLException {
        String insert = "INSERT INTO barrel_locations (region_name, world, x, y, z) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = hikariCPDatabase.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setString(1, regionName);
            stmt.setString(2, loc.getWorld().getName());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.executeUpdate();
        }
    }

    public boolean isSpecialBarrelLocation(Location loc) throws SQLException {
        String query = "SELECT COUNT(*) FROM barrel_locations WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = hikariCPDatabase.dataSource.getConnection();
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
        try (Connection conn = hikariCPDatabase.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(delete)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setDouble(2, loc.getX());
            stmt.setDouble(3, loc.getY());
            stmt.setDouble(4, loc.getZ());
            stmt.executeUpdate();
        }
    }
}
