package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.proficisci.utils.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipTeleportCommand implements CommandExecutor {
    private final ShipService shipService;

    public ShipTeleportCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Command darf nur von Spielern genutzt werden.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /ship tp <regionName>"));
            return true;
        }

        try {
            shipService.teleportShip(player, args[1]);
            player.sendMessage(Chat.greenFade("Teleportiert zu " + args[1]));
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Component.text("An error occurred while teleporting to the ship."));
        }

        return true;
    }
}
