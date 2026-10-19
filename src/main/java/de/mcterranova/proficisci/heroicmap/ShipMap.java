package de.mcterranova.proficisci.heroicmap;

import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapButton;
import com.nekyia.heroicmap.api.MapButtonClickEvent;
import com.nekyia.heroicmap.api.MapObject;
import com.nekyia.heroicmap.api.PlayerLayer;
import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.terranova.nations.regions.RegionManager;
import de.terranova.nations.regions.grid.SettleRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ship button in the panel of every town of Nations with a harbour, and for each player the ship routes between
 * the towns where they switched it on, as dashed lines only they see. Names the API of HeroicMap, so Proficisci
 * loads it only when HeroicMap is enabled. HeroicMap keeps the states in memory only; the database keeps them here.
 */
public final class ShipMap implements Listener {

    /** The layer of the towns of Nations; its objects are named by the UUID of the town. */
    private static final String TOWNS = "nations:staedte";
    private static final MapObject.Stroke ROUTE = MapObject.Stroke.of("#DBB050").withDash(10, 10);

    private final Proficisci plugin;
    private final Layer layer;
    private final MapButton button;
    // player -> towns with the button on, as in the database; a player stays with an empty set to clear the lines
    private final Map<UUID, Set<UUID>> on;
    // harbour (region name) -> its town, from the last sync; harbours outside a town, as at the spawn, are missing
    private Map<String, UUID> towns = Map.of();
    // player -> the lines they have on the layer
    private final Map<UUID, Map<String, MapObject>> shown = new HashMap<>();
    // writes of the button states, off the main thread and in click order; a daemon, so it never holds up the stop
    private final ExecutorService writes = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Proficisci-Schiffsknopf");
        t.setDaemon(true);
        return t;
    });

    private ShipMap(Proficisci plugin, Layer layer, MapButton button, Map<UUID, Set<UUID>> on) {
        this.plugin = plugin;
        this.layer = layer;
        this.button = button;
        this.on = on;
    }

    /** Creates layer and button, and syncs them every 30 seconds from the ships in memory. */
    public static void start(Proficisci plugin) throws SQLException {
        HeroicMapApi api = Bukkit.getServicesManager().load(HeroicMapApi.class);
        if (api == null) {
            throw new IllegalStateException("HeroicMap bietet keinen HeroicMapApi-Service an");
        }
        Layer layer = api.layer(plugin, "schiffsrouten");
        layer.name("Schiffsrouten", "Ship routes");
        layer.order(103);
        MapButton button = api.button(plugin, "schiff", "Schiff", "Ship");
        ShipMap map = new ShipMap(plugin, layer, button, BarrelDatabase.getInstance().loadRouteButtons());
        Bukkit.getPluginManager().registerEvents(map, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, map::update, 20L, 20L * 30);
    }

    @EventHandler
    public void onClick(MapButtonClickEvent e) {
        if (!e.button().id().equals(button.id())) return;
        UUID town;
        try {
            town = UUID.fromString(e.object());
        } catch (IllegalArgumentException ex) {
            return;
        }
        UUID player = e.player();
        boolean now = !e.on();
        button.state(player, e.layer(), e.object(), now);
        Set<UUID> mine = on.computeIfAbsent(player, p -> new HashSet<>());
        if (now) mine.add(town); else mine.remove(town);
        writes.execute(() -> {
            try {
                BarrelDatabase.getInstance().setRouteButton(player, town, now);
            } catch (SQLException ex) {
                plugin.getLogger().warning("Schiffsknopf von " + player + " nicht gespeichert: " + ex);
            }
        });
        sync(player);
    }

    private void update(BukkitTask task) {
        try {
            Map<String, UUID> now = new HashMap<>();
            plugin.specialBarrelLocations.forEach((harbour, loc) -> {
                if (loc.getWorld() != null) {
                    RegionManager.retrieveRegion(SettleRegion.class, loc).ifPresent(s -> now.put(harbour, s.getId()));
                }
            });
            Set<UUID> before = new HashSet<>(towns.values());
            Set<UUID> after = new HashSet<>(now.values());
            for (UUID town : after) {
                if (before.contains(town)) continue;
                button.attach(TOWNS, town.toString());
                on.forEach((player, mine) -> {
                    if (mine.contains(town)) button.state(player, TOWNS, town.toString(), true);
                });
            }
            for (UUID town : before) {
                if (!after.contains(town)) button.detach(TOWNS, town.toString());
            }
            towns = now;
            on.keySet().forEach(this::sync);
        } catch (IllegalStateException e) {
            // only after layer or button are gone, as while HeroicMap is being disabled
            plugin.getLogger().warning("HeroicMap: Schiffsrouten sind weg: " + e);
            task.cancel();
        }
    }

    /** Puts the lines of this player that changed and removes those no route has any more. */
    private void sync(UUID player) {
        Map<String, Location> ships = plugin.specialBarrelLocations;
        Map<String, MapObject> next = new HashMap<>();
        for (List<String> pair : pairs(plugin.shipService.getAllRoutes(), towns, on.getOrDefault(player, Set.of()))) {
            Location from = ships.get(pair.get(0));
            Location to = ships.get(pair.get(1));
            if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
                continue;
            }
            String id = UUID.nameUUIDFromBytes(String.join("\n", pair).getBytes(StandardCharsets.UTF_8)).toString();
            next.put(id, MapObject.Line.through(id, List.of(point(from), point(to)))
                    .withDimension(from.getWorld().getKey().asString())
                    .withStroke(ROUTE));
        }
        PlayerLayer own = layer.forPlayer(player);
        Map<String, MapObject> old = shown.getOrDefault(player, Map.of());
        next.forEach((id, line) -> {
            if (!line.equals(old.get(id))) own.put(line);
        });
        old.keySet().stream().filter(id -> !next.containsKey(id)).forEach(own::remove);
        shown.put(player, next);
    }

    /**
     * The routes whose harbours both lie in towns of {@code mine}, each once as its two harbours, the smaller name
     * first.
     */
    static Set<List<String>> pairs(Map<String, Set<String>> routes, Map<String, UUID> towns, Set<UUID> mine) {
        Set<List<String>> pairs = new HashSet<>();
        routes.forEach((a, targets) -> {
            UUID townA = towns.get(a);
            if (townA == null || !mine.contains(townA)) return;
            for (String b : targets) {
                UUID townB = towns.get(b);
                if (townB != null && mine.contains(townB)) {
                    pairs.add(a.compareTo(b) < 0 ? List.of(a, b) : List.of(b, a));
                }
            }
        });
        return pairs;
    }

    private static MapObject.Point point(Location l) {
        return new MapObject.Point(l.getBlockX() + 0.5, l.getBlockZ() + 0.5);
    }
}
