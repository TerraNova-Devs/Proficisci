package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

import java.sql.SQLException;
import java.util.List;

public class PlayerMoveListener implements Listener {
    private final BarrelDatabase barrelDatabase;
    private final Proficisci plugin;

    public PlayerMoveListener(Proficisci plugin, BarrelDatabase barrelDatabase) {
        this.plugin = plugin;
        this.barrelDatabase = barrelDatabase;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();

        List<Location> barrelLocations;
        try {
            barrelLocations = barrelDatabase.loadBarrelLocations();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        for (Location barrelLoc : barrelLocations) {
            if (loc.distance(barrelLoc) <= 3) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && player.getLocation().distance(barrelLoc) <= 3) {
                            openTeleportInventory(player, barrelLocations);
                        }
                    }
                }.runTaskLater(plugin, 200L); // 200 ticks = 10 seconds
                return;
            }
        }
    }

    private void openTeleportInventory(Player player, List<Location> barrelLocations) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Teleport Options"));
        for (Location loc : barrelLocations) {
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Teleport to " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        player.openInventory(inv);
    }
}
