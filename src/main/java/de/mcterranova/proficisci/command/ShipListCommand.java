package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.commands.CommandAnnotation;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipListCommand {

    private final ShipService shipService;

    public ShipListCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @CommandAnnotation(
            domain = "list",
            permission = "proficisci.admin",
            description = "Shows you all the existing ships.",
            usage = "/ship list"
    )
    public boolean onlist(Player p, String[] args) {

        try {
            shipService.listShips().forEach(ship -> p.sendMessage(Component.text("- " + ship)));
        } catch (SQLException e) {
            e.printStackTrace();
            p.sendMessage(Component.text("An error occurred while listing ships."));
        }

        return true;
    }
}
