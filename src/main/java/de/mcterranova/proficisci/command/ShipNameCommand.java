package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.proficisci.utils.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipNameCommand implements CommandExecutor {
    private final ShipService shipService;

    public ShipNameCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Command darf nur von Spielern genutzt werden.");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(Chat.errorFade( "Usage: /ship name <regionName> <name>"));
            return true;
        }

        try {
            shipService.setShipName(args[1], args[2]);
            player.sendMessage(Chat.greenFade("Schiffsname zu " + args[2] + " geändert."));
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Chat.errorFade("An error occurred while setting the ship name."));
        }

        return true;
    }
}
