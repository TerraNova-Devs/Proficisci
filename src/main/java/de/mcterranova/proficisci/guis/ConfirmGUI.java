package de.mcterranova.proficisci.guis;

import de.mcterranova.proficisci.database.BarrelDatabase;


import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import de.mcterranova.terranovaLib.utils.Chat;
import io.th0rgal.oraxen.api.OraxenItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;

public class ConfirmGUI extends RoseGUI {

    private static final int TELEPORT_COST = 1; // Cost in silver
    private final BarrelDatabase barrelDatabase;
    String regionName;

    public ConfirmGUI(@NotNull Player player, @NotNull String regionName, @NotNull Location targetLoc) throws SQLException {
        super(player, "confirm-gui", Component.text("Bestätige Reise zu " + regionName), 1);
        this.regionName = regionName;
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
                    Location targetLoc = locations.get(regionName);
                    if (targetLoc != null) {

                        if (!(chargeStrict(player, OraxenItems.getItemById("terranova_silver").build(), TELEPORT_COST, true) == -1)) {
                            Location safeLocation = getSafeLocation(targetLoc);
                            playTeleportEffects(player.getLocation());
                            player.teleport(safeLocation);
                            playTeleportEffects(safeLocation);
                            player.sendMessage(Chat.greenFade("Du bist in " + regionName + " angekommen."));
                        } else {
                            player.sendMessage(Component.text("Du brauchst mindestens " + TELEPORT_COST + " Silber für die Reise."));
                        }
                    } else {
                        player.sendMessage(Chat.errorFade("Reiseziel nicht gefunden."));
                    }
                });
        RoseItem cancel = new RoseItem.Builder()
                .material(Material.RED_WOOL)
                .displayName(Component.text("Abbrechen"))
                .build()
                .onClick(e -> {
                    this.setClosed(true);
                });
        addItem(3, confirm);
        addItem(5, cancel);
    }

    private Integer chargeStrict(Player p, ItemStack item, int amount, boolean onlyFullCharge) {
        ItemStack[] stacks = p.getInventory().getContents();
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || !stack.isSimilar(item)) continue;
            total += stack.getAmount();
        }
        if (onlyFullCharge && total < amount) return -1;

        total = amount;


        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] == null || !stacks[i].isSimilar(item)) continue;


            int stackAmount = stacks[i].getAmount();
            int n = total;
            if (stackAmount < total) {
                stacks[i] = null;
                total -= stackAmount;
            } else {
                stacks[i].setAmount(stackAmount - total);
                total -= total;
                break;
            }
        }
        p.getInventory().setContents(stacks);
        p.updateInventory();
        return amount - total;
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

