package de.mcterranova.proficisci.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.UUID;

public class BarrelClickListener implements Listener {
    private final Proficisci plugin;
    private static final String ANVIL_RENAME_META_KEY = "AnvilRenameShip";

    public BarrelClickListener(Proficisci plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if(event.getAction().isLeftClick()) return;
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.BARREL) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            try {
                BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
                if (barrelDatabase.isSpecialBarrelLocation(block.getLocation())) {
                    event.setCancelled(true);
//                    UUID owner = barrelDatabase.getShipOwner(block.getLocation());
//                    if (owner != null && owner.equals(player.getUniqueId())) {
//                        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
//                        RegionManager regionManager = container.get(BukkitAdapter.adapt(block.getWorld()));
//                        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(block.getLocation()));
//                        String regionName = regions.iterator().next().getId();
//                        String currentName = barrelDatabase.getShipName(regionName);
//                        if (currentName != null) {
//                            openAnvilRename(player, currentName);
//                        }
//                    } else {
//                        player.sendMessage(Component.text("You do not own this ship."));
//                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage(Component.text("An error occurred while retrieving the ship name."));
            }

        }
    }

    private void openAnvilRename(Player player, String currentName) {
        ItemStack nameTag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = nameTag.getItemMeta();
        meta.displayName(Component.text(currentName));
        nameTag.setItemMeta(meta);

        Inventory anvilInventory = Bukkit.createInventory(null, InventoryType.ANVIL);
        anvilInventory.setItem(0, nameTag);

        player.setMetadata(ANVIL_RENAME_META_KEY, new FixedMetadataValue(plugin, true));
        player.openInventory(anvilInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.ANVIL && event.getWhoClicked().hasMetadata(ANVIL_RENAME_META_KEY)) {
            if (event.getRawSlot() == 2) { // Result slot
                event.getWhoClicked().sendMessage(Component.text("Pommes"));
                ItemStack resultItem = event.getCurrentItem();
                if (resultItem != null && resultItem.getType() == Material.NAME_TAG) {
                    event.getWhoClicked().sendMessage(Component.text("Mayo"));
                    // Manually retrieve the rename text from the first slot's item
                    ItemStack firstSlotItem = event.getInventory().getItem(0);
                    String newName = ((TextComponent)resultItem.displayName()).content();

                    if (newName != null && !newName.isEmpty()) {
                        event.getWhoClicked().sendMessage(Component.text("Ketchup"));
                        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                        RegionManager regionManager = container.get(BukkitAdapter.adapt(event.getWhoClicked().getWorld()));
                        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(event.getWhoClicked().getLocation()));
                        String regionName = regions.iterator().next().getId();

                        try {
                            BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
                            barrelDatabase.setShipName(regionName, newName);
                            ((Player) event.getWhoClicked()).sendMessage(Component.text("Ship renamed to " + newName));
                        } catch (SQLException e) {
                            e.printStackTrace();
                            ((Player) event.getWhoClicked()).sendMessage(Component.text("An error occurred while renaming the ship."));
                        }

                        event.setCancelled(true);
                        event.getWhoClicked().removeMetadata(ANVIL_RENAME_META_KEY, plugin);
                        event.getWhoClicked().closeInventory();
                    }
                }
            }
        }
    }
}
