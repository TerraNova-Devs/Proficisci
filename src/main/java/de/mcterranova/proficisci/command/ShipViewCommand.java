package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.proficisci.utils.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class ShipViewCommand implements CommandExecutor {
    private final ShipService shipService;

    public ShipViewCommand(ShipService shipService) {
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
            player.sendMessage(Component.text("Usage: /ship view <regionName>"));
            return true;
        }

        try {
            Location loc = shipService.getShipLocation(args[1]);
            if (loc != null) {
                UUID owner = shipService.getShipOwner(args[1]);
                String name = shipService.getShipName(args[1]);
                player.sendMessage(Chat.greenFade( "Schiff Info:"));
                player.sendMessage(Chat.greenFade("Name: " + name));
                player.sendMessage(Chat.greenFade(  "Owner: " + owner));
                player.sendMessage(Chat.greenFade( "Location: " + loc));
            } else {
                player.sendMessage(Component.text("Schiff nicht gefunden."));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Component.text("An error occurred while viewing the ship."));
        }

        return true;
    }
}
