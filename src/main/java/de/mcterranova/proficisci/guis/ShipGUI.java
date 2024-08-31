package de.mcterranova.proficisci.guis;

import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.guiutil.RoseGUI;
import de.mcterranova.proficisci.guiutil.RoseItem;
import de.mcterranova.proficisci.guiutil.RosePagination;
import de.mcterranova.proficisci.utils.Chat;
import de.terranova.nations.api.SettlementAPI;
import de.terranova.nations.settlements.AccessLevelEnum;
import de.terranova.nations.settlements.Settlement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public class ShipGUI extends RoseGUI {

    private static final int ROWS_PER_PAGE = 5; // 9x4 (excluding border and navigation slots)
    public static final int DISTANCE = 6000;
    private final RosePagination pagination = new RosePagination(this);
    private final BarrelDatabase barrelDatabase;

    public ShipGUI(@NotNull Player player) throws SQLException {
        super(player, "ship-gui", Chat.blueFade("<b>Reise Möglichkeiten"), ROWS_PER_PAGE);
        this.barrelDatabase = BarrelDatabase.getInstance();
        pagination.registerPageSlotsBetween(10, 16);
        pagination.registerPageSlotsBetween(19, 25);
        pagination.registerPageSlotsBetween(28, 34);
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        RoseItem filler = new RoseItem.Builder()
                .material(Material.BLACK_STAINED_GLASS_PANE)
                .displayName("")
                .build();
        fillGui(filler);

        RoseItem next = new RoseItem.Builder()
                .material(Material.SPECTRAL_ARROW)
                .displayName(Chat.redFade("<b>Next Page"))
                .build()
                .onClick(e -> {
                    pagination.goNextPage();
                });

        RoseItem close = new RoseItem.Builder()
                .material(Material.BARRIER)
                .displayName(Chat.redFade("<b>Exit Menu"))
                .build().onClick(e -> {
                    pagination.goLastPage();
                });
        addItem(close, 40);

        RoseItem last = new RoseItem.Builder()
                .material(Material.SPECTRAL_ARROW)
                .displayName(Chat.redFade("<b>Last Page"))
                .build();

        Location currentLocation = player.getLocation();
        try {
            Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
            locations.forEach((regionName, loc) -> {
                if (loc.distance(player.getLocation()) > DISTANCE)
                    return;
                Optional<Settlement> settle = SettlementAPI.getSettlement(loc);
                RoseItem locationItem;
                boolean test = loc.distance(currentLocation) <= 3;
                locationItem = new RoseItem.Builder()
                        .material(test ? Material.BARRIER : Material.ENDER_PEARL)
                        .displayName(test ? Chat.greenFade("<b>" + regionName.replaceAll("_", " ") + " (Deine Position)") : Chat.blueFade("<b>" + regionName.replaceAll("_", " ")))
                        .addLore(settle.isEmpty() ? "<red>Besitzer: <gray>Server" : "<red>Besitzer: <gray>" + Bukkit.getOfflinePlayer(settle.get().getEveryMemberNameWithCertainAccessLevel(AccessLevelEnum.MAJOR).stream().findFirst().get()).getName(),
                                "<red>Koordinaten: <gray>" + (int) loc.x() + ", " + (int) loc.y() + ", " + (int) loc.z(),
                                "<red>Distanz: <gray>" + (int) loc.distance(currentLocation) + "m",
                                "<red>Reisekosten: <gray>1 Silver")
                        .build();
                if (!test) locationItem.onClick(e -> {
                    Location targetLoc = locations.get(regionName);
                    if (targetLoc != null) {
                        try {
                            new ConfirmGUI(player, regionName, loc).open();
                        } catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                    } else {
                        player.sendMessage(Chat.errorFade("Reiseziel nicht gefunden."));
                    }
                });
                pagination.addItem(locationItem);
            });
            pagination.update();
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Chat.errorFade("An error occurred while loading teleport locations."));
        }

        if (!pagination.isFirstPage()) addItem(last, 44);
        if (!pagination.isLastPage()) addItem(next, 36);


    }


}
