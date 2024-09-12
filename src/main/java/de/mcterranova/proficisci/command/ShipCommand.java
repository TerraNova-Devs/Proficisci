package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;

import de.mcterranova.terranovaLib.utils.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ShipCommand implements CommandExecutor {
    private final ShipService shipService;
    private final Map<String, CommandExecutor> subCommands = new HashMap<>();

    public ShipCommand() throws SQLException {
        this.shipService = new ShipService();
        subCommands.put("list", new ShipListCommand(shipService));
        subCommands.put("tp", new ShipTeleportCommand(shipService));
        subCommands.put("name", new ShipNameCommand(shipService));
        subCommands.put("view", new ShipViewCommand(shipService));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Command darf nur von Spielern genutzt werden.");
            return true;
        }
        if(player.hasPermission("proficisci.admin")){
            if (args.length == 0) {
                player.sendMessage(Chat.errorFade("Usage: /ship <list|tp|name|view>"));
                return true;
            }

            CommandExecutor subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                return subCommand.onCommand(sender, command, label, args);
            }

            player.sendMessage(Chat.errorFade("Usage: /ship <list|tp|name|view>"));
        }
        return true;
    }
}
