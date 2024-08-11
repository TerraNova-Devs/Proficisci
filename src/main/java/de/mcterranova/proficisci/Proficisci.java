package de.mcterranova.proficisci;

import de.mcterranova.proficisci.command.ShipCommand;
import de.mcterranova.proficisci.database.HikariCPDatabase;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.listener.*;
import de.mcterranova.proficisci.utils.SilverManager;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Proficisci extends JavaPlugin {
    private static Proficisci instance;
    private HikariCPDatabase hikariCPDatabase;
    private BarrelDatabase barrelDatabase;
    private InventoryClickListener inventoryClickListener;

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
            inventoryClickListener = new InventoryClickListener();
            getServer().getPluginManager().registerEvents(inventoryClickListener, this);
            getServer().getPluginManager().registerEvents(new BarrelClickListener(this), this);
            getCommand("ship").setExecutor(new ShipCommand());
        } catch (SQLException e) {
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.addRecipe(getSpecialBarrelRecipe());


    }

    @Override
    public void onDisable() {
        if (hikariCPDatabase != null) {
            hikariCPDatabase.closeConnection();
        }
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

    public InventoryClickListener getInventoryClickListener() {
        return inventoryClickListener;
    }
}
