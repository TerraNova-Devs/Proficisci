package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.proficisci.utils.ChatUtils;
import net.kyori.adventure.text.Component;
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
            ChatUtils.sendErrorMessage(player, "Usage: /ship name <regionName> <name>");
            return true;
        }

        try {
            shipService.setShipName(args[1], args[2]);
            ChatUtils.sendSuccessMessage(player,"Schiffsname zu " + args[2] + " geändert.");
        } catch (SQLException e) {
            e.printStackTrace();
            ChatUtils.sendErrorMessage(player, "An error occurred while setting the ship name.");
        }

        return true;
    }
}
