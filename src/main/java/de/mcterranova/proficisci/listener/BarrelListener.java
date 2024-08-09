package de.mcterranova.proficisci.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.utils.ChatUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

public class BarrelListener implements Listener {
    private final Proficisci plugin;

    public BarrelListener(Proficisci plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ItemStack itemInHand = event.getItemInHand();
        Player player = event.getPlayer();
        if (plugin.isSpecialBarrel(itemInHand)) {
            ArrayList<String> allowedBiomes = new ArrayList<>(Arrays.asList("RIVER", "DEEP_COLD_OCEAN", "COLD_OCEAN", "DEEP_LUKEWARM_OCEAN", "LUKEWARM_OCEAN", "OCEAN", "DEEP_OCEAN", "WARM_OCEAN", "DEEP_WARM_OCEAN", "BEACH", "GRAVEL_BEACH", "SNOWY_BEACH"));
            if (!allowedBiomes.contains(block.getBiome().name())) {
                ChatUtils.sendErrorMessage(player, "Der Schiffsblock kann nur in Ozean oder Flussbiomen platziert werden.");
                event.setCancelled(true);
                return;
            }

            try {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionManager regionManager = container.get(BukkitAdapter.adapt(block.getWorld()));
                ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(block.getLocation()));

                if (regions.size() == 0) {
                    ChatUtils.sendErrorMessage(player, "Der Schiffsblock kann nur innerhalb einer Stadt platziert werden.");
                    event.setCancelled(true);
                    return;
                }

                BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
                String regionName = regions.iterator().next().getId();

                if(barrelDatabase.regionHasBarrel(regionName)) {
                    barrelDatabase.saveBarrelLocation(block.getLocation(), regionName, regionName, event.getPlayer().getUniqueId());
                    ChatUtils.sendSuccessMessage(player, "Schiffsblock erfolgreich platziert.");
                } else {
                    ChatUtils.sendErrorMessage(player, "In dieser Stadt gibt es bereits ein Schiff.");
                    event.setCancelled(true);
                }

            } catch (SQLException e) {
                e.printStackTrace();
                ChatUtils.sendErrorMessage(player, "An error occurred while saving the barrel location.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event){
        Block block = event.getBlock();
        Player player = event.getPlayer();

        try {
            BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
            if (barrelDatabase.isSpecialBarrelLocation(block.getLocation())) {
                if(barrelDatabase.getShipOwner(block.getLocation()) == player.getUniqueId()) {
                    barrelDatabase.deleteBarrelLocation(block.getLocation());
                    ChatUtils.sendSuccessMessage(player, "Schiff entfernt.");
                    block.getWorld().dropItemNaturally(block.getLocation(), plugin.getSpecialBarrelItem());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            ChatUtils.sendErrorMessage(player, "An error occurred while removing the barrel location.");
        }
    }
}
