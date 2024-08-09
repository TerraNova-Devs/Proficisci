package de.mcterranova.proficisci.utils;

import org.bukkit.Material;

public enum SilverState {
    NUGGET(Material.IRON_NUGGET),
    INGOT(Material.IRON_INGOT),
    BLOCK(Material.IRON_BLOCK);

    public Material material;

    SilverState(Material material) {
        this.material = material;
    }

    public Material getMaterial() {
        return material;
    }
}