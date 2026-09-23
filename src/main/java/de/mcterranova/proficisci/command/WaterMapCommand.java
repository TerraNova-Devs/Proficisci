package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.Proficisci;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class WaterMapCommand implements CommandExecutor {
    private static final int RENDER_RADIUS_BLOCKS = 1000;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(Component.text("Watermap could not start: no world is loaded."));
            return true;
        }

        Proficisci.getInstance().waterMapService.renderAroundOrigin(sender, world, RENDER_RADIUS_BLOCKS);
        return true;
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getWorld();
        }

        World defaultWorld = Bukkit.getWorld("world");
        if (defaultWorld != null) {
            return defaultWorld;
        }

        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }
}
