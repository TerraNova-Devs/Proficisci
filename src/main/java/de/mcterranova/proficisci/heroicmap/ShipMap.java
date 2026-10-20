package de.mcterranova.proficisci.heroicmap;

import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapObject;
import de.mcterranova.proficisci.Proficisci;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the ship routes on HeroicMap, as dashed lines between the ships, like Nations shows its towns.
 * Names the API of HeroicMap, so Proficisci loads it only when HeroicMap is enabled.
 */
public final class ShipMap {

    private static final MapObject.Stroke ROUTE = MapObject.Stroke.of("#DBB050").withDash(10, 10);

    private final Layer layer;
    private Map<String, MapObject> shown = new HashMap<>();

    private ShipMap(Layer layer) {
        this.layer = layer;
    }

    /** Creates the layer and syncs it every 30 seconds from the ships in memory. */
    public static void start(Proficisci plugin) {
        HeroicMapApi api = Bukkit.getServicesManager().load(HeroicMapApi.class);
        if (api == null) {
            throw new IllegalStateException("HeroicMap bietet keinen HeroicMapApi-Service an");
        }
        Layer layer = api.layer(plugin, "schiffsrouten");
        layer.name("Schiffsrouten", "Ship routes");
        layer.visible(false);
        layer.order(103);
        ShipMap map = new ShipMap(layer);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> map.update(plugin, task), 20L, 20L * 30);
    }

    private void update(Proficisci plugin, BukkitTask task) {
        Map<String, Location> ships = plugin.specialBarrelLocations;
        Map<String, MapObject> next = new HashMap<>();
        plugin.shipService.getAllRoutes().forEach((a, targets) -> {
            for (String b : targets) {
                Location from = ships.get(a);
                Location to = ships.get(b);
                if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
                    continue;
                }
                // one line per pair of ships, whichever way the route is stored
                String pair = a.compareTo(b) < 0 ? a + "\n" + b : b + "\n" + a;
                String id = UUID.nameUUIDFromBytes(pair.getBytes(StandardCharsets.UTF_8)).toString();
                next.put(id, MapObject.Line.through(id, List.of(point(from), point(to)))
                        .withDimension(from.getWorld().getKey().asString())
                        .withStroke(ROUTE));
            }
        });
        try {
            next.forEach((id, line) -> {
                if (!line.equals(shown.get(id))) {
                    layer.put(line);
                }
            });
            shown.keySet().stream().filter(id -> !next.containsKey(id)).forEach(layer::remove);
            shown = next;
        } catch (IllegalStateException e) {
            // only after the layer is gone, as while HeroicMap is being disabled
            plugin.getLogger().warning("HeroicMap: Ebene der Schiffsrouten ist weg: " + e);
            task.cancel();
        }
    }

    private static MapObject.Point point(Location l) {
        return new MapObject.Point(l.getBlockX() + 0.5, l.getBlockZ() + 0.5);
    }
}
