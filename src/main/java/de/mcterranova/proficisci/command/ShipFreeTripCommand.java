package de.mcterranova.proficisci.command;

import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.commands.CommandAnnotation;
import de.mcterranova.terranovaLib.utils.Chat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class ShipFreeTripCommand {
    private final ShipService shipService;

    public ShipFreeTripCommand(ShipService shipService) {
        this.shipService = shipService;
    }

    @CommandAnnotation(
            domain = "freifahrt.$ONLINEPLAYERS.$<anzahl>",
            permission = "proficisci.admin",
            description = "Gives a player free trips until 4:00.",
            usage = "/ship freifahrt <player> <anzahl>"
    )
    public boolean onCommand(Player p, String[] args) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            p.sendMessage(Chat.errorFade("Spieler " + args[1] + " ist unbekannt."));
            return true;
        }
        int trips;
        try {
            trips = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            trips = 0;
        }
        if (trips < 1) {
            p.sendMessage(Chat.errorFade("Die Anzahl muss eine Zahl ab 1 sein."));
            return true;
        }

        try {
            int total = shipService.giveFreeTrips(target.getUniqueId(), trips);
            p.sendMessage(Chat.greenFade(target.getName() + " hat jetzt " + total + " Freifahrten bis 4 Uhr."));
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage(Chat.greenFade("Du hast " + trips + " Freifahrten bekommen, sie gelten bis 4 Uhr."));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            p.sendMessage(Chat.errorFade("An error occurred while giving the free trips."));
        }
        return true;
    }
}
