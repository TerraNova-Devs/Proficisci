package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;

import de.mcterranova.terranovaLib.commands.CommandAnnotation;
import de.mcterranova.terranovaLib.utils.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class ShipViewCommand {
    private final ShipService shipService;

    public ShipViewCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @CommandAnnotation(
            domain = "view.$REGION_NAMES",
            permission = "proficisci.admin",
            description = "Shows you a ship",
            usage = "/ship view <regionName>"
    )
    public boolean onCommand(Player p, String[] args) {

        try {
            Location loc = shipService.getShipLocation(args[1]);
            if (loc != null) {
                UUID owner = shipService.getShipOwner(args[1]);
                String name = shipService.getShipName(args[1]);
                p.sendMessage(Chat.greenFade( "Schiff Info:"));
                p.sendMessage(Chat.greenFade("Name: " + name));
                p.sendMessage(Chat.greenFade(  "Owner: " + owner));
                p.sendMessage(Chat.greenFade( "Location: " + loc));
            } else {
                p.sendMessage(Component.text("Schiff nicht gefunden."));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            p.sendMessage(Component.text("An error occurred while viewing the ship."));
        }

        return true;
    }
}
