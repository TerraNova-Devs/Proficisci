package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerMoveListener implements Listener {
    private final Proficisci plugin;
    private final BarrelDatabase barrelDatabase;
    private final Map<UUID, Long> playerTimers = new HashMap<>();

    public PlayerMoveListener(Proficisci plugin, BarrelDatabase barrelDatabase) {
        this.plugin = plugin;
        this.barrelDatabase = barrelDatabase;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        try {
            for (Map.Entry<String, Location> entry : barrelDatabase.loadTeleportLocations().entrySet()) {
                Location barrelLocation = entry.getValue();
                if (playerLocation.getWorld().equals(barrelLocation.getWorld()) &&
                        playerLocation.distance(barrelLocation) <= 3) {

                    if (!playerTimers.containsKey(player.getUniqueId())) {
                        playerTimers.put(player.getUniqueId(), System.currentTimeMillis());
                    } else {
                        long timeSpent = System.currentTimeMillis() - playerTimers.get(player.getUniqueId());
                        if (timeSpent >= 10000) { // 10 seconds
                            openTeleportMenu(player, barrelLocation);
                            playerTimers.remove(player.getUniqueId());
                        }
                    }
                    return;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Component.text("An error occurred while checking barrel locations."));
        }

        playerTimers.remove(player.getUniqueId()); // Reset timer if player is out of range
    }

    private void openTeleportMenu(Player player, Location currentLocation) {
        InventoryClickListener inventoryClickListener = plugin.getInventoryClickListener();
        if (inventoryClickListener != null) {
            inventoryClickListener.openTeleportMenu(player, 1, currentLocation);
        }
    }
}
