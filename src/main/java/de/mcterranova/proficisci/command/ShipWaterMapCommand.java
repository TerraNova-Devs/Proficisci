package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.terranovaLib.commands.CommandAnnotation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShipWaterMapCommand {
    private static final int RENDER_RADIUS_BLOCKS = 1000;

    @CommandAnnotation(
            domain = "watermap",
            permission = "proficisci.admin",
            description = "Renders the water map around 0/0.",
            usage = "/ship watermap"
    )
    public boolean onCommand(CommandSender sender, String[] args) {
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(Component.text("Watermap konnte nicht gestartet werden: keine Welt geladen."));
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
