package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.utils.ChatUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import java.sql.SQLException;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class PlayerMoveListener implements Listener {
    private final Proficisci plugin;
    private final BarrelDatabase barrelDatabase;
    private final Map<UUID, Long> playerStayTimes;

    public PlayerMoveListener(Proficisci plugin) throws SQLException {
        this.plugin = plugin;
        this.barrelDatabase = BarrelDatabase.getInstance();
        this.playerStayTimes = new HashMap<>();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        try {
            if (isNearSpecialBarrelLocation(to)) {
                if (playerStayTimes.containsKey(player.getUniqueId())) {
                    long stayTime = playerStayTimes.get(player.getUniqueId());
                    if (System.currentTimeMillis() - stayTime > 10000) { // 10 seconds
                        playerStayTimes.remove(player.getUniqueId());
                        plugin.getInventoryClickListener().openTeleportMenu(player, 1, to);
                    }
                } else {
                    if(!player.hasMetadata("travelInv")) {
                        ChatUtils.sendMessage(player, "Kapitän: Ahoi! Mach es dir gemütlich in 10 Sekunden geht es los.");
                        playerStayTimes.put(player.getUniqueId(), System.currentTimeMillis());
                    }
                }
            } else {
                if(playerStayTimes.containsKey(player.getUniqueId()))
                    ChatUtils.sendMessage(player, "Kapitän: Vielleicht ein anderes mal.");
                playerStayTimes.remove(player.getUniqueId());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isNearSpecialBarrelLocation(Location loc) throws SQLException {
        Map<String, Location> specialBarrelLocations = barrelDatabase.loadTeleportLocations();
        for (Location specialLoc : specialBarrelLocations.values()) {
            if (loc.getWorld().equals(specialLoc.getWorld()) && loc.distance(specialLoc) <= 3) {
                return true;
            }
        }
        return false;
    }
}
