package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.commands.CommandAnnotation;
import de.mcterranova.terranovaLib.utils.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipTeleportCommand {
    private final ShipService shipService;

    public ShipTeleportCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @CommandAnnotation(
            domain = "tp.$REGION_NAMES",
            permission = "proficisci.admin",
            description = "Shows you all the existing ships.",
            usage = "/ship tp <regionName>"
    )
    public boolean onCommand(Player p, String[] args) {

        try {
            shipService.teleportShip(p, args[1]);
            p.sendMessage(Chat.greenFade("Teleportiert zu " + args[1]));
        } catch (SQLException e) {
            e.printStackTrace();
            p.sendMessage(Component.text("An error occurred while teleporting to the ship."));
        }

        return true;
    }
}
