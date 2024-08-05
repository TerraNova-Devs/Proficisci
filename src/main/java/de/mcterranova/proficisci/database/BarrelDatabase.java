package de.mcterranova.proficisci.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BarrelDatabase {

    private final HikariCPDatabase hikariCPDatabase;

    public BarrelDatabase(HikariCPDatabase hikariCPDatabase) {
        this.hikariCPDatabase = hikariCPDatabase;
    }

    public List<Location> loadBarrelLocations() throws SQLException {
        List<Location> barrelLocations = new ArrayList<>();
        String query = "SELECT world, x, y, z FROM barrel_locations";
        try (Connection conn = hikariCPDatabase.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                barrelLocations.add(new Location(Bukkit.getWorld(world), x, y, z));
            }
        }
        return barrelLocations;
    }

    public void saveBarrelLocation(Location loc) throws SQLException {
        String insert = "INSERT INTO barrel_locations (world, x, y, z) VALUES (?, ?, ?, ?)";
        try (Connection conn = hikariCPDatabase.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setDouble(2, loc.getX());
            stmt.setDouble(3, loc.getY());
            stmt.setDouble(4, loc.getZ());
            stmt.executeUpdate();
        }
    }

    public boolean isSpecialBarrel(ItemStack item) {
        if (item == null || item.getType() != Material.BARREL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getLore() != null && meta.getLore().contains("Special Barrel");
    }
}
