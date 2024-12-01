package de.mcterranova.proficisci.guis;

import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.services.ShipService;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import de.mcterranova.terranovaLib.roseGUI.RosePagination;
import de.mcterranova.terranovaLib.utils.Chat;
import de.terranova.nations.api.SettleAPI;
import de.terranova.nations.settlements.AccessLevelEnum;
import de.terranova.nations.settlements.Settle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ShipGUI extends RoseGUI {

    private static final int ROWS_PER_PAGE = 5; // 9x4 (excluding border and navigation slots)
    public static final int DISTANCE = 6000;
    private final RosePagination pagination = new RosePagination(this);
    private final BarrelDatabase barrelDatabase;
    private final ShipService shipService;

    public ShipGUI(@NotNull Player player) throws SQLException {
        super(player, "ship-gui", Chat.blueFade("<b>Reise Möglichkeiten"), ROWS_PER_PAGE);
        this.barrelDatabase = BarrelDatabase.getInstance();
        this.shipService = new ShipService();
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
                .displayName(Chat.redFade("<b>Nächste Seite"))
                .build()
                .onClick(e -> pagination.goNextPage());

        RoseItem close = new RoseItem.Builder()
                .material(Material.BARRIER)
                .displayName(Chat.redFade("<b>Menü verlassen"))
                .build().onClick(e -> pagination.goLastPage());
        addItem(close, 40);

        RoseItem last = new RoseItem.Builder()
                .material(Material.SPECTRAL_ARROW)
                .displayName(Chat.redFade("<b>Vorherige Seite"))
                .build();

        Location currentLocation = player.getLocation();
        try {
            Map<String, Location> locations = barrelDatabase.loadTeleportLocations().entrySet().stream()
                    .sorted(Comparator.comparingDouble(entry -> entry.getValue().distance(currentLocation)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            boolean hasNearbyLocation = false;

            for (Map.Entry<String, Location> entry : locations.entrySet()) {
                String regionName = capitalize(entry.getKey());
                Location loc = entry.getValue();

                // Wenn die Distanz größer ist als der Grenzwert, überspringen wir
                if (loc.distance(player.getLocation()) <= DISTANCE) {
                    hasNearbyLocation = true;
                    addTeleportOption(player, loc, regionName, currentLocation);
                }
            }

            // Falls kein Ziel innerhalb der Reichweite gefunden wurde, das nächstgelegene anbieten
            if (!hasNearbyLocation) {
                Location nearestLocation = shipService.getNearestShip(player.getLocation());
                if (nearestLocation != null) {
                    String regionName = capitalize(barrelDatabase.getRegionNameByLocation(nearestLocation));
                    addTeleportOption(player, nearestLocation, regionName, currentLocation);
                } else {
                    player.sendMessage(Chat.errorFade("Keine anderen Schiffe verfügbar."));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Chat.errorFade("An error occurred while loading teleport locations."));
        }

        if (!pagination.isFirstPage()) addItem(last, 44);
        if (!pagination.isLastPage()) addItem(next, 36);
    }

    private void addTeleportOption(Player player, Location loc, String regionName, Location currentLocation) {
        Optional<Settle> settle = SettleAPI.getSettle(loc);
        RoseItem locationItem;
        boolean test = loc.distance(currentLocation) <= 3;
        locationItem = new RoseItem.Builder()
                .material(test ? Material.BARRIER : Material.ENDER_PEARL)
                .displayName(test ? Chat.greenFade("<b>" + regionName.replaceAll("_", " ") + " (Deine Position)") : Chat.blueFade("<b>" + regionName.replaceAll("_", " ")))
                .addLore(settle.isEmpty() ? "<red>Besitzer: <gray>Server" : "<red>Besitzer: <gray>" + Bukkit.getOfflinePlayer(settle.get().getEveryMemberNameWithCertainAccessLevel(AccessLevelEnum.MAJOR).stream().findFirst().get()).getName(),
                        "<red>Koordinaten: <gray>" + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ(),
                        "<red>Distanz: <gray>" + (int) loc.distance(currentLocation) + "m",
                        "<red>Reisekosten: <gray>1 Silver")
                .build();
        if (!test) locationItem.onClick(e -> {
            try {
                new ConfirmGUI(player, regionName, loc).open();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
        pagination.addItem(locationItem);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

}