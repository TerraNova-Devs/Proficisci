package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.utils.ChatUtils;
import de.mcterranova.proficisci.utils.SilverManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.sql.SQLException;
import java.util.*;

public class InventoryClickListener implements Listener {
    private final BarrelDatabase barrelDatabase;
    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 36; // 9x4 (excluding border and navigation slots)
    private static final int TELEPORT_COST = 2; // Cost in silver
    private static final int DISTANCE = 6000;

    public InventoryClickListener() throws SQLException {
        this.barrelDatabase = BarrelDatabase.getInstance();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) throws SQLException {
        Component titleComponent = event.getView().title();
        if (titleComponent instanceof TextComponent) {
            String title = ((TextComponent) titleComponent).content();
            if (title.startsWith("Reise Möglichkeiten - Seite ")) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                Player player = (Player) event.getWhoClicked();
                Map<String, Location> locations = barrelDatabase.loadTeleportLocations();

                if (item == null || item.getType() == Material.AIR) {
                    return;
                }

                if (item.getType() == Material.ARROW) {
                    int currentPage = Integer.parseInt(title.split(" ")[3]);
                    if (((TextComponent) item.getItemMeta().displayName()).content().equals("Nächste Seite")) {
                        openTeleportMenu(player, currentPage + 1, null);
                    } else if (((TextComponent) item.getItemMeta().displayName()).content().equals("Vorherige Seite")) {
                        openTeleportMenu(player, currentPage - 1, null);
                    }
                    return;
                }

                if (item.getType() == Material.ENDER_PEARL) {
                    String regionName = ((TextComponent) item.getItemMeta().displayName()).content();
                    Location targetLoc = locations.get(regionName);

                    if (targetLoc != null) {
                        confirmTeleport(player, targetLoc, regionName);
                    } else {
                        ChatUtils.sendErrorMessage(player, "Reiseziel nicht gefunden.");
                    }
                }
            } else if (title.startsWith("Bestätige Reise zu ")) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                Player player = (Player) event.getWhoClicked();
                Map<String, Location> locations = barrelDatabase.loadTeleportLocations();

                if (item == null || item.getType() == Material.AIR) {
                    return;
                }

                if (item.getType() == Material.GREEN_WOOL) {
                    String regionName = title.substring("Bestätige Reise zu ".length());
                    Location targetLoc = locations.get(regionName);
                    if (targetLoc != null) {
                        if (chargePlayer(player, TELEPORT_COST)) {
                            Location safeLocation = getSafeLocation(targetLoc);
                            playTeleportEffects(player.getLocation());
                            player.teleport(safeLocation);
                            playTeleportEffects(safeLocation);
                            ChatUtils.sendSuccessMessage(player, "Du bist in " + regionName + " angekommen.");
                        } else {
                            player.sendMessage(Component.text("Du brauchst mindestens " + TELEPORT_COST + " Silber für die Reise."));
                        }
                    } else {
                        ChatUtils.sendErrorMessage(player, "Reiseziel nicht gefunden.");
                    }
                } else if (item.getType() == Material.RED_WOOL) {
                    player.closeInventory();
                }
            }
        }
    }

    public void openTeleportMenu(Player player, int page, Location currentLocation) throws SQLException {
        Inventory teleportMenu = Bukkit.createInventory(null, INVENTORY_SIZE, Component.text("Reise Möglichkeiten - Seite " + page));

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
                if (loc.distance(player.getLocation()) > DISTANCE)
                    continue;

                ItemStack locationItem;
                ItemMeta meta;

                if (currentLocation != null && loc.distance(currentLocation) <= 3) {
                    // Mark current location
                    locationItem = new ItemStack(Material.BARRIER);
                    meta = locationItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ChatUtils.returnGreenFade(regionName + " (Deine Position)"));
                        locationItem.setItemMeta(meta);
                    }
                } else {
                    locationItem = new ItemStack(Material.ENDER_PEARL);
                    meta = locationItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ChatUtils.stringToComponent(regionName));
                        meta.lore(Collections.singletonList(ChatUtils.stringToComponent("x:" + loc.x() + ",  y:" + loc.y() + ", z:" + loc.z())));
                        locationItem.setItemMeta(meta);
                    }
                }

                teleportMenu.addItem(locationItem);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            ChatUtils.sendErrorMessage(player, "An error occurred while loading teleport locations.");
        }

        // Add navigation arrows
        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            if (prevMeta != null) {
                prevMeta.displayName(Component.text("Vorherige Seite"));
                prevPage.setItemMeta(prevMeta);
            }
            teleportMenu.setItem(INVENTORY_SIZE - 9, prevPage);
        }

        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
        if ((page * ITEMS_PER_PAGE) < locations.size()) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            if (nextMeta != null) {
                nextMeta.displayName(Component.text("Nächste Seite"));
                nextPage.setItemMeta(nextMeta);
            }
            teleportMenu.setItem(INVENTORY_SIZE - 1, nextPage);
        }

        player.setMetadata("travelInv", new FixedMetadataValue(Proficisci.getInstance(), player.getLocation()));
        player.openInventory(teleportMenu);
    }

    private void confirmTeleport(Player player, Location targetLoc, String regionName) {
        Inventory confirmMenu = Bukkit.createInventory(null, 9, Component.text("Bestätige Reise zu " + regionName));

        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.displayName(Component.text("Bestätigen"));
            confirmItem.setItemMeta(confirmMeta);
        }

        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(Component.text("Abbrechen"));
            cancelItem.setItemMeta(cancelMeta);
        }

        confirmMenu.setItem(3, confirmItem);
        confirmMenu.setItem(5, cancelItem);

        player.setMetadata("travelInv", new FixedMetadataValue(Proficisci.getInstance(), player.getLocation()));
        player.openInventory(confirmMenu);
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

    private boolean chargePlayer(Player player, int cost) {
        ItemStack[] contents = player.getInventory().getContents();
        int totalSilver = 0;
        ItemStack placeholder = SilverManager.get().placeholder();

        for (ItemStack item : contents) {
            if (item != null && item.isSimilar(placeholder)) {
                totalSilver += item.getAmount();
            }
        }

        if (totalSilver >= cost) {
            int remainingCost = cost;
            for (ItemStack item : contents) {
                if (item != null && item.isSimilar(placeholder)) {
                    int itemAmount = item.getAmount();
                    if (itemAmount <= remainingCost) {
                        player.getInventory().remove(item);
                        remainingCost -= itemAmount;
                    } else {
                        item.setAmount(itemAmount - remainingCost);
                        remainingCost = 0;
                    }
                    if (remainingCost == 0) {
                        break;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void playTeleportEffects(Location location) {
        // Play particle effects
        location.getWorld().spawnParticle(Particle.SPLASH, location, 100);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 100);

        // Play sound effects
        location.getWorld().playSound(location, Sound.ENTITY_BOAT_PADDLE_WATER, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void animateTeleport(Player player, Location from, Location to) {
        int steps = 20; // Number of steps for animation
        for (int i = 0; i <= steps; i++) {
            int finalI = i;
            Bukkit.getScheduler().runTaskLater(Proficisci.getInstance(), () -> {
                double t = (double) finalI / steps;
                Location interpolated = from.clone().add(to.clone().subtract(from).toVector().multiply(t));
                player.teleport(interpolated);
            }, i);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.hasMetadata("travelInv")) {
            player.removeMetadata("travelInv", Proficisci.getInstance());
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("travelInv")) {
            player.removeMetadata("travelInv", Proficisci.getInstance());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("travelInv")) {
            player.removeMetadata("travelInv", Proficisci.getInstance());
        }
    }
}
