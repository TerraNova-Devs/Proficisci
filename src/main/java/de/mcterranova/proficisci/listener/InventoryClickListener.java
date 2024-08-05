package de.mcterranova.proficisci.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.Location;

public class InventoryClickListener implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (event.getView().title().equals(Component.text("Teleport Options"))) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.ENDER_PEARL) {
                Player player = (Player) event.getWhoClicked();
                TextComponent displayNameComponent = (TextComponent) item.getItemMeta().displayName();
                String displayName = displayNameComponent.content();
                String[] locParts = displayName.split(" ");
                if (locParts.length == 6) {
                    String worldName = locParts[2];
                    int x = Integer.parseInt(locParts[3].replace(",", ""));
                    int y = Integer.parseInt(locParts[4].replace(",", ""));
                    int z = Integer.parseInt(locParts[5].replace(",", ""));
                    Location targetLoc = new Location(player.getServer().getWorld(worldName), x, y, z);
                    player.teleport(targetLoc);
                    player.sendMessage(Component.text("Teleported to " + worldName + " " + x + ", " + y + ", " + z));
                } else {
                    player.sendMessage(Component.text("Invalid teleport location."));
                }
            }
        }
    }
}
