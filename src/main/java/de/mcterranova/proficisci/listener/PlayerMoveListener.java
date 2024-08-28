package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.utils.Chat;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class PlayerMoveListener implements Listener {
    private final Proficisci plugin;
    private final BarrelDatabase barrelDatabase;
    private final Map<UUID, BukkitRunnable> countdownTasks;

    public PlayerMoveListener(Proficisci plugin) throws SQLException {
        this.plugin = plugin;
        this.barrelDatabase = BarrelDatabase.getInstance();
        this.countdownTasks = new HashMap<>();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        try {
            if (isNearSpecialBarrelLocation(to)) {
                if (!countdownTasks.containsKey(player.getUniqueId())) {
                    player.sendMessage(Chat.stringToComponent("Kapitän: Ahoi! Mach es dir gemütlich in 10 Sekunden geht es los."));
                    BukkitRunnable task = new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                if (isNearSpecialBarrelLocation(player.getLocation())) {
                                    plugin.getInventoryClickListener().openTeleportMenu(player, 1, player.getLocation());
                                    countdownTasks.remove(player.getUniqueId());
                                }
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                    task.runTaskLater(plugin, 200L); // 200 ticks = 10 seconds
                    countdownTasks.put(player.getUniqueId(), task);
                }
            } else {
                if (countdownTasks.containsKey(player.getUniqueId())) {
                    countdownTasks.get(player.getUniqueId()).cancel();
                    countdownTasks.remove(player.getUniqueId());
                    player.sendMessage(Chat.stringToComponent("Kapitän: Vielleicht ein anderes mal."));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isNearSpecialBarrelLocation(Location loc) throws SQLException {
        Map<String, Location> specialBarrelLocations = Proficisci.getInstance().specialBarrelLocations;
        for (Location specialLoc : specialBarrelLocations.values()) {
            if (loc.getWorld().equals(specialLoc.getWorld()) && loc.distance(specialLoc) <= 3) {
                return true;
            }
        }
        return false;
    }
}
