package de.mcterranova.proficisci.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.Proficisci;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;

public class BarrelListener implements Listener {
    private final BarrelDatabase barrelDatabase;
    private final Proficisci plugin;

    public BarrelListener(BarrelDatabase barrelDatabase, Proficisci plugin) {
        this.barrelDatabase = barrelDatabase;
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ItemStack itemInHand = event.getItemInHand();
        if (plugin.isSpecialBarrel(itemInHand)) {
            // Check if the barrel is placed in an ocean or river biome
            Biome biome = block.getBiome();
            if (biome != Biome.OCEAN && biome != Biome.RIVER) {
                event.getPlayer().sendMessage(Component.text("Special barrels can only be placed in ocean or river biomes!"));
                event.setCancelled(true);
                return;
            }

            // Check if the barrel is placed inside a WorldGuard region
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(block.getWorld()));
            ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(block.getLocation()));

            if (regions.size() == 0) {
                event.getPlayer().sendMessage(Component.text("Special barrels can only be placed inside a WorldGuard region!"));
                event.setCancelled(true);
                return;
            }

            // Add the barrel location to the list and database
            try {
                String regionName = regions.iterator().next().getId();
                barrelDatabase.saveBarrelLocation(block.getLocation(), regionName);
                event.getPlayer().sendMessage(Component.text("Special barrel placed successfully!"));
            } catch (SQLException e) {
                e.printStackTrace();
                event.getPlayer().sendMessage(Component.text("An error occurred while saving the barrel location."));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        try {
            if (barrelDatabase.isSpecialBarrelLocation(block.getLocation())) {
                barrelDatabase.deleteBarrelLocation(block.getLocation());
                event.setDropItems(false); // Prevent default item drop
                event.getPlayer().sendMessage(Component.text("Special barrel removed."));
                block.getWorld().dropItemNaturally(block.getLocation(), plugin.getSpecialBarrelItem());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            event.getPlayer().sendMessage(Component.text("An error occurred while removing the barrel location."));
        }
    }

    @EventHandler
    public void onBlockDropItem(BlockDropItemEvent event) {
        Block block = event.getBlock();
        try {
            if (barrelDatabase.isSpecialBarrelLocation(block.getLocation())) {
                event.setCancelled(true); // Cancel the default drop event
                block.getWorld().dropItemNaturally(block.getLocation(), plugin.getSpecialBarrelItem());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            event.getPlayer().sendMessage(Component.text("An error occurred while handling the item drop."));
        }
    }
}
