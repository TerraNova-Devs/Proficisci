package de.mcterranova.proficisci.pl3xmap;

import de.mcterranova.proficisci.Proficisci;
import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.guis.ShipGUI;
import de.mcterranova.proficisci.services.ShipService;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.WorldLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.world.World;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Pl3xMapShipRouteLayer extends WorldLayer {

    Collection<Marker<?>> markers = new ArrayList<>();

    public Pl3xMapShipRouteLayer(@NotNull World world) throws SQLException {
        super("ship-route-layer", world, () -> "Schiffsrouten");
        BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();
        ShipService shipService = Proficisci.getInstance().shipService;

        setUpdateInterval(0);
        setLiveUpdate(true);
        setShowControls(true);
        setDefaultHidden(true);
        setPriority(100);
        setZIndex(999);
        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();
        Options optionroutes;
        optionroutes = Options.builder()
                .strokeColor(0xFFDBB050)
                //https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/stroke-dasharray
                .strokeDashPattern("10")
                .strokeDashOffset("10")
                .build();

        List<String> done = new ArrayList<>();

        shipService.getAllRoutes().forEach((s, d) -> {
            Location location = locations.get(s);
            d.forEach((s2) -> {
                if(!done.contains(s2)) {
                    Location location2 = locations.get(s2);
                    markers.add(new Polyline(s + s2, new Point((int) location.x(), (int) location.z()), new Point((int) location2.x(), (int) location2.z())).setOptions(optionroutes));
                }
            });
            done.add(s);
        });

    }

    @Override
    public @NotNull Collection<Marker<?>> getMarkers() {
        return markers;
    }
}
