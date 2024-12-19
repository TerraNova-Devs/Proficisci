package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.commands.CommandAnnotation;
import de.mcterranova.terranovaLib.utils.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipNameCommand {
    private final ShipService shipService;

    public ShipNameCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @CommandAnnotation(
            domain = "name.$REGION_NAMES.$<name>",
            permission = "proficisci.admin",
            description = "Renames a ship..",
            usage = "/ship name <regionName> <name>"
    )
    public boolean onCommand(Player p, String[] args) {

        try {
            shipService.setShipName(args[1], args[2]);
            p.sendMessage(Chat.greenFade("Schiffsname zu " + args[2] + " geändert."));
        } catch (SQLException e) {
            e.printStackTrace();
            p.sendMessage(Chat.errorFade("An error occurred while setting the ship name."));
        }

        return true;
    }
}
