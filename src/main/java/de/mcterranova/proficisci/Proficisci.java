package de.mcterranova.proficisci;

import de.mcterranova.proficisci.command.ShipCommand;
import de.mcterranova.proficisci.database.HikariCPDatabase;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.guiutil.RoseGUIListener;
import de.mcterranova.proficisci.listener.*;
import de.mcterranova.proficisci.pl3xmap.Pl3xMapShipRouteLayer;
import de.mcterranova.proficisci.utils.SilverManager;
import de.terranova.nations.pl3xmap.Pl3xMapSettlementLayer;
import net.kyori.adventure.text.TextComponent;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.registry.Registry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.*;

public final class Proficisci extends JavaPlugin {
    private static Proficisci instance;
    private HikariCPDatabase hikariCPDatabase;
    private BarrelDatabase barrelDatabase;
    public Map<String, Location> specialBarrelLocations;
    private Registry<@NotNull Layer> layerRegistry;

    public static Proficisci getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        try {
            SilverManager.init();
            hikariCPDatabase = HikariCPDatabase.getInstance();
            barrelDatabase = BarrelDatabase.getInstance();

            getServer().getPluginManager().registerEvents(new BarrelListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
            getServer().getPluginManager().registerEvents(new RoseGUIListener(),this);
            getServer().getPluginManager().registerEvents(new BarrelClickListener(this), this);
            getCommand("ship").setExecutor(new ShipCommand());
            specialBarrelLocations = barrelDatabase.loadTeleportLocations();
        } catch (SQLException e) {
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.addRecipe(getSpecialBarrelRecipe());

        // OPTIONALER PL3XMAP SUPPORT
        if(Bukkit.getPluginManager().getPlugin("Pl3xMap") != null) {
            try {
                pl3xmapMarkerRegistry();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onDisable() {
        if (hikariCPDatabase != null) {
            hikariCPDatabase.closeConnection();
        }
    }

    private void pl3xmapMarkerRegistry() throws SQLException {
        this.layerRegistry = Objects.requireNonNull(Pl3xMap.api().getWorldRegistry().get("world")).getLayerRegistry();
        layerRegistry.register("ship-route-layer",new Pl3xMapShipRouteLayer(Objects.requireNonNull(Pl3xMap.api().getWorldRegistry().get("world"))));
    }

    public ItemStack getSpecialBarrelItem() {
        ItemStack specialBarrel = new ItemStack(Material.BARREL);
        ItemMeta meta = specialBarrel.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Reiseschiff-Block"));
            List<TextComponent> lores = new ArrayList<>();
            lores.add(Component.text("Kann nur in Ozean oder River Biomen platziert werden."));
            lores.add(Component.text("Dient zur Schnellreise zu anderen Schiffen innerhalb von <=6000 Blöcken."));
            meta.lore(lores);
            specialBarrel.setItemMeta(meta);
        }
        return specialBarrel;
    }

    public ShapedRecipe getSpecialBarrelRecipe() {
        NamespacedKey key = new NamespacedKey(this, "special_barrel");
        ShapedRecipe recipe = new ShapedRecipe(key, getSpecialBarrelItem());
        RecipeChoice choice = new RecipeChoice.MaterialChoice(
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.JUNGLE_PLANKS,
                Material.DARK_OAK_PLANKS, Material.ACACIA_PLANKS, Material.MANGROVE_PLANKS,
                Material.BAMBOO_PLANKS, Material.BIRCH_PLANKS, Material.CHERRY_PLANKS,
                Material.CRIMSON_PLANKS, Material.WARPED_PLANKS);
        recipe.shape("EPE", "PBP", "EPE");
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('P', choice);
        recipe.setIngredient('B', Material.BARREL);
        return recipe;
    }

    public boolean isSpecialBarrel(ItemStack item) {
        if (item == null || item.getType() != Material.BARREL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return Component.text("Reiseschiff-Block").equals(meta.displayName()) &&
                meta.lore() != null &&
                meta.lore().contains(Component.text("Kann nur in Ozean oder River Biomen platziert werden.")) &&
                meta.lore().contains(Component.text("Dient zur Schnellreise zu anderen Schiffen innerhalb von <=6000 Blöcken."));
    }

}
