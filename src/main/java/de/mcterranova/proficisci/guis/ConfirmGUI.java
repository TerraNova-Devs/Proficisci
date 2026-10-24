package de.mcterranova.proficisci.guis;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;


import de.mcterranova.terranovaLib.InventoryUtil.ItemTransfer;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import de.mcterranova.terranovaLib.utils.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;

public class ConfirmGUI extends RoseGUI {

    private static final int TELEPORT_COST = 1; // Cost in silver
    // how far from where the ship menu was opened a trip may start, as ShipService.getCurrentShip finds a harbour
    private static final double AT_HARBOUR = 10;
    private final BarrelDatabase barrelDatabase;
    String regionName;
    // where the player stood when the ship menu opened; after a trip they stand at another harbour
    private final Location origin;
    // one trip per menu, also for clicks in the same tick before it is closed
    private boolean travelling;

    public ConfirmGUI(@NotNull Player player, @NotNull String regionName, @NotNull Location targetLoc, @NotNull Location origin) throws SQLException {
        super(player, "confirm-gui", Component.text("Bestätige Reise zu " + regionName), 1);
        this.regionName = regionName;
        this.origin = origin;
        this.barrelDatabase = BarrelDatabase.getInstance();
    }

    @Override
    public void onOpen(InventoryOpenEvent event) throws SQLException {
        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
        RoseItem confirm = new RoseItem.Builder()
                .material(Material.GREEN_WOOL)
                .displayName(Component.text("Bestätigen"))
                .build()
                .onClick(e -> {
                    if (travelling) return;
                    travelling = true;
                    // closing within the click is not safe; a tick later the menu closes first, then the trip,
                    // so it never stays open when the teleport is refused
                    Bukkit.getScheduler().runTask(Proficisci.getInstance(), () -> {
                        player.closeInventory();
                        travel(locations.get(regionName.toLowerCase()));
                    });
                });
        RoseItem cancel = new RoseItem.Builder()
                .material(Material.RED_WOOL)
                .displayName(Component.text("Abbrechen"))
                .build()
                .onClick(e -> Bukkit.getScheduler().runTask(Proficisci.getInstance(), () -> player.closeInventory()));
        addItem(3, confirm);
        addItem(5, cancel);
    }

    private void travel(Location targetLoc) {
        if (targetLoc == null) {
            player.sendMessage(Chat.errorFade("Reiseziel nicht gefunden."));
            return;
        }
        // only from the harbour the menu was opened at
        if (!player.getWorld().equals(origin.getWorld()) || player.getLocation().distance(origin) > AT_HARBOUR) {
            player.sendMessage(Chat.errorFade("Du bist nicht mehr am Hafen, an dem du das Schiffsmenü geöffnet hast."));
            return;
        }
        boolean free = useFreeTrip();
        if (!free && ItemTransfer.charge(player, "terranova_silver", TELEPORT_COST, true) == -1) {
            player.sendMessage(Component.text("Du brauchst mindestens " + TELEPORT_COST + " Silber für die Reise."));
            return;
        }
        Location safeLocation = getSafeLocation(targetLoc);
        Location from = player.getLocation();
        boolean arrived;
        if (player.isInsideVehicle() && (player.getVehicle() instanceof Horse || player.getVehicle() instanceof Mule || player.getVehicle() instanceof Donkey)) {
            Entity mount = player.getVehicle();
            mount.eject();
            mount.teleport(safeLocation);
            arrived = player.teleport(safeLocation);
            mount.addPassenger(player);
        } else {
            arrived = player.teleport(safeLocation);
        }
        if (!arrived) {
            // refused, e.g. by another plugin: the trip is not paid
            refund(free);
            player.sendMessage(Chat.errorFade("Die Reise ging nicht los, du bekommst sie zurück."));
            return;
        }
        playTeleportEffects(from);
        playTeleportEffects(safeLocation);
        player.sendMessage(Chat.greenFade("Du bist in " + regionName + " angekommen."));
        if (free) {
            player.sendMessage(Chat.greenFade("Die Reise war eine Freifahrt."));
        }
    }

    /** Uses up a free trip if the player has one; on a database error the trip costs silver as before. */
    private boolean useFreeTrip() {
        try {
            return barrelDatabase.useFreeTrip(player.getUniqueId(), System.currentTimeMillis());
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void refund(boolean free) {
        if (!free) {
            ItemTransfer.credit(player, "terranova_silver", TELEPORT_COST, false);
            return;
        }
        try {
            Proficisci.getInstance().shipService.giveFreeTrips(player.getUniqueId(), 1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void playTeleportEffects(Location location) {
        // Play particle effects
        location.getWorld().spawnParticle(Particle.SPLASH, location, 100);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 100);

        // Play sound effects
        location.getWorld().playSound(location, Sound.ENTITY_BOAT_PADDLE_WATER, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private Location getSafeLocation(Location targetLoc) {
        // This method finds a safe location next to the target location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location potentialLoc = targetLoc.clone().add(dx, 0, dz);
                if (isSafeLocation(potentialLoc)) {
                    return potentialLoc;
                }
            }
        }
        return targetLoc; // Fallback to target location if no safe spot is found
    }

    private boolean isSafeLocation(Location loc) {
        return loc.getBlock().isPassable() && loc.add(0, 1, 0).getBlock().isPassable();
    }
}
