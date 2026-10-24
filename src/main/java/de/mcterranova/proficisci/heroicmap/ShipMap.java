package de.mcterranova.proficisci.heroicmap;

import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapButton;
import com.nekyia.heroicmap.api.MapButtonClickEvent;
import com.nekyia.heroicmap.api.MapObject;
import com.nekyia.heroicmap.api.PlayerLayer;
import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.services.ShipService;
import de.terranova.nations.regions.RegionManager;
import de.terranova.nations.regions.grid.SettleRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.ToDoubleBiFunction;

/**
 * The ship button in the panel of every town of Nations with a harbour, and for each player the fastest ways by ship
 * between the towns where they switched it on, also over other harbours, as dashed lines only they see. Names the API of HeroicMap, so Proficisci
 * loads it only when HeroicMap is enabled. HeroicMap keeps the states in memory only; the database keeps them here.
 */
public final class ShipMap implements Listener {

    /** The layer of the towns of Nations; its objects are named by the UUID of the town. */
    private static final String TOWNS = "nations:staedte";
    private static final MapObject.Stroke ROUTE = MapObject.Stroke.of("#DBB050").withDash(10, 10);
    // the ship of the Aseprite designer (design/HeroicMap/schiff.aseprite), images of the layer
    private static final MapObject.Symbol SHIP = new MapObject.Symbol("images/schiff_16.png", "images/schiff_9.png");
    private static final String PIN_COLOR = "#DBB050";
    // objects per layer for everyone, and per player and layer; HeroicMap throws above, which would stop the sync
    static final int LIMIT = 1000;
    private boolean warnedLimit;

    private final Proficisci plugin;
    private final Layer layer;
    private final MapButton button;
    // player -> towns with the button on, as in the database; a player stays with an empty set to clear the lines
    private final Map<UUID, Set<UUID>> on;
    // harbour (region name) -> its town, from the last sync; harbours outside a town, as at the spawn, are missing
    private Map<String, UUID> towns = Map.of();
    // player -> the lines they have on the layer
    private final Map<UUID, Map<String, MapObject>> shown = new HashMap<>();
    // harbour -> its pin on the layer for everyone, from the last sync
    private Map<String, MapObject> pins = Map.of();
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
        for (String image : List.of("schiff_16.png", "schiff_9.png")) {
            try (InputStream in = plugin.getResource("heroicmap/" + image)) {
                layer.image("images/" + image, in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
            // a ship on every ship block, for everyone; the name only outside a town, whose banner names it otherwise
            Map<String, MapObject> next = new HashMap<>();
            plugin.specialBarrelLocations.forEach((harbour, loc) -> {
                if (loc.getWorld() != null) {
                    MapObject.Pin pin = pin("hafen-", harbour, loc, MapObject.Size.MEDIUM);
                    next.put(harbour, now.containsKey(harbour) ? pin : pin.withName(name(harbour)));
                }
            });
            Map<String, MapObject> kept = capped(next, "alle");
            kept.forEach((harbour, pin) -> {
                if (!pin.equals(pins.get(harbour))) layer.put(pin);
            });
            pins.forEach((harbour, pin) -> {
                if (!kept.containsKey(harbour)) layer.remove(pin.id());
            });
            pins = kept;
            on.keySet().forEach(this::sync);
        } catch (IllegalStateException e) {
            // only after layer or button are gone, as while HeroicMap is being disabled
            plugin.getLogger().warning("HeroicMap: Schiffsrouten sind weg: " + e);
            task.cancel();
        }
    }

    /**
     * Puts the lines of this player that changed and removes those no route has any more. The stops show by the ships
     * of the layer for everyone; a larger ship only on one player's ways is not possible, as objects of one player may
     * name no image.
     */
    private void sync(UUID player) {
        Map<String, Location> ships = plugin.specialBarrelLocations;
        Map<String, MapObject> next = new HashMap<>();
        // no way between harbours without a location or in different worlds (infinite); the search leaves such routes out
        ToDoubleBiFunction<String, String> blocks = (a, b) -> ShipService.seaDistance(ships.get(a), ships.get(b));
        for (List<String> pair : segments(plugin.shipService.getAllRoutes(), blocks, towns, on.getOrDefault(player, Set.of()))) {
            Location from = ships.get(pair.get(0));
            Location to = ships.get(pair.get(1));
            if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
                continue;
            }
            // a leg round the world is drawn up to the border and on from the other side
            WorldBorder border = from.getWorld().getWorldBorder();
            List<double[]> pieces = pieces(from.getBlockX() + 0.5, from.getBlockZ() + 0.5,
                    to.getBlockX() + 0.5, to.getBlockZ() + 0.5,
                    border.getCenter().getX(), border.getCenter().getZ(), border.getSize());
            for (int i = 0; i < pieces.size(); i++) {
                double[] p = pieces.get(i);
                String id = UUID.nameUUIDFromBytes((String.join("\n", pair) + "\n" + i).getBytes(StandardCharsets.UTF_8)).toString();
                next.put(id, MapObject.Line.through(id, List.of(new MapObject.Point(p[0], p[1]), new MapObject.Point(p[2], p[3])))
                        .withDimension(from.getWorld().getKey().asString())
                        .withStroke(ROUTE));
            }
        }
        Map<String, MapObject> kept = capped(next, "Spieler " + player);
        PlayerLayer own = layer.forPlayer(player);
        Map<String, MapObject> old = shown.getOrDefault(player, Map.of());
        kept.forEach((id, object) -> {
            if (!object.equals(old.get(id))) own.put(object);
        });
        old.keySet().stream().filter(id -> !kept.containsKey(id)).forEach(own::remove);
        shown.put(player, kept);
    }

    /**
     * The legs to draw: for every two harbours in towns of {@code mine}, the fastest way between them through the
     * routes, over any harbour, also one outside a town; between two harbours of one town only a direct route, as
     * before. Fastest is the fewest trips, each a teleport for one silver; among those the shortest in blocks. Each leg
     * once, as its two harbours, the smaller name first. Routes go both ways, as ShipService stores them; a route
     * whose {@code blocks} are not finite is no way.
     */
    static Set<List<String>> segments(Map<String, Set<String>> routes, ToDoubleBiFunction<String, String> blocks,
                                      Map<String, UUID> towns, Set<UUID> mine) {
        List<String> harbours = towns.keySet().stream().filter(h -> mine.contains(towns.get(h))).toList();
        Set<List<String>> legs = new HashSet<>();
        // One search per harbour of the player, a few milliseconds per sync with 39 harbours; should that ever show,
        // keep the result of each harbour for the whole sync instead of searching again for every player.
        for (String from : harbours) {
            Map<String, String> before = fastestFrom(from, routes, blocks);
            for (String to : harbours) {
                if (from.compareTo(to) >= 0) continue;
                if (towns.get(from).equals(towns.get(to))) {
                    if (routes.getOrDefault(from, Set.of()).contains(to)) legs.add(List.of(from, to));
                    continue;
                }
                for (String at = to; before.containsKey(at); at = before.get(at)) {
                    String prev = before.get(at);
                    legs.add(prev.compareTo(at) < 0 ? List.of(prev, at) : List.of(at, prev));
                }
            }
        }
        return legs;
    }

    /**
     * For every harbour that {@code from} reaches: the harbour before it on the fastest way there. A search by layers,
     * one trip each; within a layer the way shortest in blocks wins, and on a tie the harbour first by name.
     */
    private static Map<String, String> fastestFrom(String from, Map<String, Set<String>> routes,
                                                   ToDoubleBiFunction<String, String> blocks) {
        Map<String, String> before = new HashMap<>();
        Map<String, Double> way = new HashMap<>(Map.of(from, 0.0));
        List<String> layer = List.of(from);
        while (!layer.isEmpty()) {
            Map<String, Double> next = new TreeMap<>();
            for (String at : layer) { // sorted, so on a tie the first by name stays
                for (String to : routes.getOrDefault(at, Set.of())) {
                    if (way.containsKey(to)) continue; // reached with fewer trips
                    double step = blocks.applyAsDouble(at, to);
                    if (!Double.isFinite(step)) continue; // no way, see blocks
                    double w = way.get(at) + step;
                    Double best = next.get(to);
                    if (best == null || w < best) {
                        next.put(to, w);
                        before.put(to, at);
                    }
                }
            }
            way.putAll(next);
            layer = List.copyOf(next.keySet());
        }
        return before;
    }

    /** At most {@link #LIMIT} of {@code objects}, the first by key; one log line per server start when any are left out. */
    private Map<String, MapObject> capped(Map<String, MapObject> objects, String whose) {
        if (objects.size() <= LIMIT) return objects;
        if (!warnedLimit) {
            warnedLimit = true;
            plugin.getLogger().warning("HeroicMap: " + objects.size() + " Objekte der Schiffsrouten für " + whose
                    + ", nur " + LIMIT + " gezeigt (Grenze der Ebene)");
        }
        Map<String, MapObject> kept = new TreeMap<>();
        new TreeMap<>(objects).entrySet().stream().limit(LIMIT).forEach(e -> kept.put(e.getKey(), e.getValue()));
        return kept;
    }

    /** The ship on the ship block of {@code harbour}; the id from the harbour, as the ids of the lines. */
    private static MapObject.Pin pin(String prefix, String harbour, Location loc, MapObject.Size size) {
        String id = prefix + UUID.nameUUIDFromBytes(harbour.getBytes(StandardCharsets.UTF_8));
        return MapObject.Pin.at(id, loc.getBlockX() + 0.5, loc.getBlockZ() + 0.5)
                .withDimension(loc.getWorld().getKey().asString())
                .withY(loc.getBlockY())
                .withSize(size)
                .withSymbol(SHIP)
                .withColor(PIN_COLOR);
    }

    /** "san_gatos" as "San Gatos". */
    static String name(String harbour) {
        StringBuilder name = new StringBuilder();
        for (String word : harbour.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    /**
     * The straight line from (x0, z0) to (x1, z1) the short way round a world {@code size} wide around (cx, cz), as
     * ShipService.seaDistance measures it, cut at the border and each piece moved back into the world: one piece
     * without wrapping, two over one border, three over a corner. Each piece is {x, z, x, z}.
     */
    static List<double[]> pieces(double x0, double z0, double x1, double z1, double cx, double cz, double size) {
        double dx = ShipService.wrap(x1 - x0, size);
        double dz = ShipService.wrap(z1 - z0, size);
        double minX = cx - size / 2;
        double minZ = cz - size / 2;
        // where the way crosses a border line; it crosses each axis at most once, being at most half the world long
        TreeSet<Double> cuts = new TreeSet<>(List.of(0.0, 1.0));
        for (double edge : new double[] {minX, minX + size}) {
            if (dx != 0 && (edge - x0) / dx > 0 && (edge - x0) / dx < 1) cuts.add((edge - x0) / dx);
        }
        for (double edge : new double[] {minZ, minZ + size}) {
            if (dz != 0 && (edge - z0) / dz > 0 && (edge - z0) / dz < 1) cuts.add((edge - z0) / dz);
        }
        List<double[]> pieces = new ArrayList<>();
        Double from = null;
        for (double to : cuts) {
            if (from != null) {
                // the world copy the middle of the piece lies in, moved back by whole worlds
                double shiftX = Math.floor((x0 + dx * (from + to) / 2 - minX) / size) * size;
                double shiftZ = Math.floor((z0 + dz * (from + to) / 2 - minZ) / size) * size;
                pieces.add(new double[] {x0 + dx * from - shiftX, z0 + dz * from - shiftZ,
                        x0 + dx * to - shiftX, z0 + dz * to - shiftZ});
            }
            from = to;
        }
        return pieces;
    }
}
