package de.mcterranova.proficisci.utils;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import de.mcterranova.proficisci.utils.ChatUtils;

/**
 * @author sakubami
 * @since 1.0
 */
public class SilverManager {

    public static SilverManager manager;
    private ItemStack placeHolder;

    /**
     * initializes the {@link SilverManager} class
     * @since 1.0
     */
    public static void init() {
        if (manager != null) return;
        manager = new SilverManager();
    }

    public void setPlaceHolder(ItemStack placeHolder) {
        this.placeHolder = placeHolder;
    }

    /**
     * uses this placeholder if the {@link ItemStack} placeholder has not been changed
     * @return the {@link ItemStack} that is used by this class
     * @since 1.0
     */
    public ItemStack placeholder() {
        if (placeHolder != null)
            return placeHolder;
        ItemStack itemStack = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = itemStack.getItemMeta();
        meta.addEnchant(Enchantment.CHANNELING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.displayName(ChatUtils.stringToComponent("<gradient:#AAA9AD:#D8D8D8><bold>Silber</bold></gradient>"));
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    /**
     * is meant to be accessed via interactions with an {@link org.bukkit.inventory.Inventory}
     * or another type of GUI where the {@link org.bukkit.entity.Player} can choose from 3 different
     * actions where the state can directly be passed on to this method
     * @param convertible the {@link ItemStack} that is to be converted
     * @param to the {@link SilverState} that the {@link ItemStack} is converted to
     * @return the conversion of the initial {@link ItemStack}
     * @since 1.0
     */
    public ItemStack convert(ItemStack convertible, SilverState to) {
        if (!convertible.hasItemMeta())
            return convertible;
        if (convertible.getType().equals(to.getMaterial()))
            return convertible;
        ItemStack newStack = new ItemStack(to.getMaterial());
        ItemMeta oldMeta = convertible.getItemMeta();
        newStack.setItemMeta(oldMeta);
        return newStack;
    }

    /**
     * @return this instance of the {@link SilverManager} class
     * @since 1.0
     */
    public static SilverManager get() {
        return manager;
    }
}