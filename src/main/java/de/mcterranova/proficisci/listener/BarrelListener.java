package de.mcterranova.proficisci.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.utils.BiomeUtil;
import de.mcterranova.terranovaLib.utils.Chat;
import de.terranova.nations.regions.grid.SettleRegion;
import de.terranova.nations.regions.modules.access.Access;
import de.terranova.nations.regions.modules.access.AccessLevel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BarrelListener implements Listener {
    private final Proficisci plugin;
    private ShipService shipService;

    public BarrelListener(Proficisci plugin) {
        this.plugin = plugin;
        this.shipService = plugin.shipService;
    }

    // after WorldGuard and Nations; a placement they refused saves no ship
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ItemStack itemInHand = event.getItemInHand();
        Player player = event.getPlayer();
        if (plugin.isSpecialBarrel(itemInHand)) {
            List<String> biomeTranslationKeys =
                    List.of(
                            // Minecraft biomes
                            "minecraft:deep_ocean",
                            "minecraft:ocean",
                            "minecraft:warm_ocean",
                            "minecraft:frozen_ocean",
                            "minecraft:lukewarm_ocean",
                            "minecraft:cold_ocean",
                            "minecraft:deep_frozen_ocean",
                            "minecraft:deep_lukewarm_ocean",
                            "minecraft:deep_cold_ocean",
                            "minecraft:river",
                            "minecraft:beach",
                            "minecraft:snowy_beach",
                            "minecraft:frozen_river",
                            // Terralith biomes
                            "terralith:gravel_beach",
                            "terralith:snowy_beach",
                            "terralith:oceanic_plateau",
                            "terralith:tropical_bay",
                            "terralith:rocky_coast",
                            "terralith:white_cliffs",
                            "terralith:sandstone_valley");

            if (!BiomeUtil.isBiomeInList(block.getLocation(), biomeTranslationKeys)) {
                player.sendMessage(Chat.errorFade("Der Schiffsblock kann nur in Ozean oder Flussbiomen platziert werden."));
                event.setCancelled(true);
                return;
            }

            try {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionManager regionManager = container.get(BukkitAdapter.adapt(block.getWorld()));
                assert regionManager != null;
                ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(block.getLocation()));
                if (regions.size() == 0) {
                    player.sendMessage(Chat.errorFade("Der Schiffsblock kann nur innerhalb einer Stadt platziert werden."));
                    event.setCancelled(true);
                    return;
                }
                if (!mayManageShip(player, block.getLocation())) {
                    player.sendMessage(Chat.errorFade("Nur Vize und Bürgermeister dieser Stadt können hier ein Schiff setzen."));
                    event.setCancelled(true);
                    return;
                }

                BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
                String regionName = regions.iterator().next().getId();

                if(!barrelDatabase.regionHasBarrel(regionName)) {
                    barrelDatabase.saveBarrelLocation(block.getLocation(), regionName, regionName, event.getPlayer().getUniqueId());
                    player.sendMessage(Chat.greenFade("Schiffsblock erfolgreich platziert."));
                    shipService.initShipRoutes();
                } else {
                    player.sendMessage(Chat.errorFade("In dieser Stadt gibt es bereits ein Schiff."));
                    event.setCancelled(true);
                }
                Proficisci.getInstance().specialBarrelLocations = barrelDatabase.loadTeleportLocations();
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage(Chat.greenFade( "An error occurred while saving the barrel location."));
                event.setCancelled(true);
            }
        }
    }

    // after WorldGuard and Nations; a break they refused neither removes the ship nor drops a second barrel
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event){
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (block.getType() != Material.BARREL) return;

        try {
            BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
            if (barrelDatabase.isSpecialBarrelLocation(block.getLocation())) {
                if (mayManageShip(player, block.getLocation())) {
                    barrelDatabase.deleteBarrelLocation(block.getLocation());
                    player.sendMessage(Chat.greenFade("Schiff entfernt."));
                    block.getWorld().dropItemNaturally(block.getLocation(), plugin.getSpecialBarrelItem());
                    event.setDropItems(false);
                    shipService.initShipRoutes();
                } else {
                    player.sendMessage(Chat.errorFade("Nur Vize und Bürgermeister dieser Stadt können das Schiff abbauen."));
                    event.setCancelled(true);
                }
            }
            Proficisci.getInstance().specialBarrelLocations = barrelDatabase.loadTeleportLocations();
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Chat.errorFade( "An error occurred while removing the barrel location."));
        }
    }

    /**
     * Vize and above of the Nations town at the location, as for claims and renames in Nations; holders of
     * proficisci.admin anywhere, as at the spawn, which is no town. The owner in the database no longer counts.
     */
    private static boolean mayManageShip(Player player, Location loc) {
        if (player.hasPermission("proficisci.admin")) return true;
        return de.terranova.nations.regions.RegionManager.retrieveRegion(SettleRegion.class, loc)
                .map(settle -> Access.hasAccess(settle.getAccess().getAccessLevel(player.getUniqueId()), AccessLevel.VICE))
                .orElse(false);
    }
}
