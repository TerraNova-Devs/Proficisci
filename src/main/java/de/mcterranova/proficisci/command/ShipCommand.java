package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.commands.AbstractCommand;
import de.terranova.nations.regions.base.Region;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipCommand extends AbstractCommand {

    private final ShipService shipService;

    public ShipCommand() throws SQLException {
        this.shipService = new ShipService();
        addPlaceholder("$REGION_NAMES", Region::getNameCache);
        addPlaceholder("$ONLINEPLAYERS", () -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        registerSubCommand(new ShipListCommand(shipService), "list");
        registerSubCommand(new ShipTeleportCommand(shipService), "tp");
        registerSubCommand(new ShipNameCommand(shipService), "name");
        registerSubCommand(new ShipViewCommand(shipService), "view");
        registerSubCommand(new ShipWaterMapCommand(), "watermap");
        registerSubCommand(new ShipFreeTripCommand(shipService), "freifahrt");
        setupHelpCommand();
        initialize();
    }


}
