package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.database.BarrelDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.*;

public class InventoryClickListener implements Listener {
    private final BarrelDatabase barrelDatabase;
    private final Map<String, Location> teleportLocations = new HashMap<>();
    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 36; // 9x4 (excluding border and navigation slots)

    public InventoryClickListener(BarrelDatabase barrelDatabase) {
        this.barrelDatabase = barrelDatabase;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Component titleComponent = event.getView().title();
        if (titleComponent instanceof TextComponent) {
            String title = ((TextComponent) titleComponent).content();
            if (title.startsWith("Teleport Options - Page ")) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                Player player = (Player) event.getWhoClicked();

                if (item == null || item.getType() == Material.AIR) {
                    return;
                }

                if (item.getType() == Material.ARROW) {
                    int currentPage = Integer.parseInt(title.split(" ")[3]);
                    if (((TextComponent) item.getItemMeta().displayName()).content().equals("Next Page")) {
                        openTeleportMenu(player, currentPage + 1, null);
                    } else if (((TextComponent) item.getItemMeta().displayName()).content().equals("Previous Page")) {
                        openTeleportMenu(player, currentPage - 1, null);
                    }
                    return;
                }

                if (item.getType() == Material.ENDER_PEARL) {
                    String regionName = ((TextComponent) item.getItemMeta().displayName()).content();
                    Location targetLoc = teleportLocations.get(regionName);
                    if (targetLoc != null) {
                        Location safeLocation = getSafeLocation(targetLoc);
                        player.teleport(safeLocation);
                        player.sendMessage(Component.text("Teleported to " + regionName));
                    } else {
                        player.sendMessage(Component.text("Teleport location not found."));
                    }
                }
            }
        }
    }

    public void openTeleportMenu(Player player, int page, Location currentLocation) {
        Inventory teleportMenu = Bukkit.createInventory(null, INVENTORY_SIZE, Component.text("Teleport Options - Page " + page));

        // Add border
        ItemStack borderItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderItem.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" "));
            borderItem.setItemMeta(borderMeta);
        }
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i < 9 || i >= INVENTORY_SIZE - 9 || i % 9 == 0 || i % 9 == 8) {
                teleportMenu.setItem(i, borderItem);
            }
        }

        // Add teleport locations
        try {
            Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
            int start = (page - 1) * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, locations.size());
            List<Map.Entry<String, Location>> locationList = new ArrayList<>(locations.entrySet());

            for (int i = start; i < end; i++) {
                Map.Entry<String, Location> entry = locationList.get(i);
                String regionName = entry.getKey();
                Location loc = entry.getValue();

                ItemStack locationItem;
                ItemMeta meta;

                if (currentLocation != null && loc.equals(currentLocation)) {
                    // Mark current location
                    locationItem = new ItemStack(Material.BARRIER);
                    meta = locationItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Component.text(regionName + " (Current Location)"));
                        locationItem.setItemMeta(meta);
                    }
                } else {
                    teleportLocations.put(regionName, loc);

                    locationItem = new ItemStack(Material.ENDER_PEARL);
                    meta = locationItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Component.text(regionName));
                        locationItem.setItemMeta(meta);
                    }
                }

                teleportMenu.addItem(locationItem);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Component.text("An error occurred while loading teleport locations."));
        }

        // Add navigation arrows
        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            if (prevMeta != null) {
                prevMeta.displayName(Component.text("Previous Page"));
                prevPage.setItemMeta(prevMeta);
            }
            teleportMenu.setItem(INVENTORY_SIZE - 9, prevPage);
        }

        if ((page * ITEMS_PER_PAGE) < teleportLocations.size()) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            if (nextMeta != null) {
                nextMeta.displayName(Component.text("Next Page"));
                nextPage.setItemMeta(nextMeta);
            }
            teleportMenu.setItem(INVENTORY_SIZE - 1, nextPage);
        }

        player.openInventory(teleportMenu);
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
