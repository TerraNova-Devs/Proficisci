package de.mcterranova.proficisci.pl3xmap;

import de.mcterranova.proficisci.database.BarrelDatabase;
import de.mcterranova.proficisci.guis.ShipGUI;
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
import java.util.Map;
import java.util.function.Supplier;

public class Pl3xMapShipRouteLayer extends WorldLayer {

    Collection<Marker<?>> markers = new ArrayList<>();

    public Pl3xMapShipRouteLayer(@NotNull World world) throws SQLException {
        super("ship-route-layer", world, () -> "Schiffsrouten");
        BarrelDatabase barrelDatabase = BarrelDatabase.getInstance();

        setUpdateInterval(0);
        setLiveUpdate(true);
        setShowControls(true);
        setDefaultHidden(true);
        setPriority(100);
        setZIndex(999);

        Map<String, Location> locations = barrelDatabase.loadTeleportLocations();

        Options optionroutes;
        optionroutes = Options.builder()
                .strokeColor(0xDD8640E6)
                .build();

        locations.forEach((s, location) -> {
            System.out.println("Test1");
            locations.forEach((s2, location2) -> {
                if (location.equals(location2) || location.distance(location2) > ShipGUI.DISTANCE) return;
                System.out.println("Test2");
                markers.add(new Polyline(s + s2,new Point((int)location.x(),(int)location.z()),new Point((int)location2.x(),(int)location2.z())).setOptions(optionroutes));
            });
        });

    }

    @Override
    public @NotNull Collection<@NotNull Marker<?>> getMarkers() {
        return markers;
    }
}
