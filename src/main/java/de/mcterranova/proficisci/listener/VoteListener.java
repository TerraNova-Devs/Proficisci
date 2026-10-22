package de.mcterranova.proficisci.listener;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.terranovaLib.utils.Chat;
import de.mcterranova.terranovaLib.vote.VoteEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.sql.SQLException;
import java.util.logging.Level;

/** Every vote gives the player free trips until midnight. */
public class VoteListener implements Listener {
    public static final int VOTE_FREE_TRIPS = 5;

    private final Proficisci plugin;

    public VoteListener(Proficisci plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVote(VoteEvent event) {
        OfflinePlayer player = event.getPlayer();
        // the database off the main thread, votes come in while others play
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.shipService.giveFreeTrips(player.getUniqueId(), VOTE_FREE_TRIPS);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Freifahrten für den Vote von " + player.getName() + " nicht vergeben", e);
                return;
            }
            Player online = player.getPlayer();
            if (online != null) {
                online.sendMessage(Chat.greenFade("Danke fürs Voten! Du hast " + VOTE_FREE_TRIPS + " Freifahrten bekommen, sie gelten bis Mitternacht."));
            }
        });
    }
}
