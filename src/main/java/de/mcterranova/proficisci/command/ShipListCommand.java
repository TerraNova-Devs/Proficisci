package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipListCommand implements CommandExecutor {
    private final ShipService shipService;

    public ShipListCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Command darf nur von Spielern genutzt werden.");
            return true;
        }

        try {
            shipService.listShips().forEach(ship -> player.sendMessage(Component.text("- " + ship)));
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Component.text("An error occurred while listing ships."));
        }

        return true;
    }
}
