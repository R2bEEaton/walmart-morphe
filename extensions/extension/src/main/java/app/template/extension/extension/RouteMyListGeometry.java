package app.template.extension.extension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure conversion and matching for the reflective native-map bridge. */
final class RouteMyListGeometry {
    private RouteMyListGeometry() {
    }

    static final class Poi {
        final String name;
        final String internalName;
        final double minX;
        final double maxX;
        final double minY;
        final double maxY;

        Poi(String name, String internalName, double minX, double maxX, double minY, double maxY) {
            this.name = name;
            this.internalName = internalName;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        RouteOrderPlanner.Point center() {
            double x = (minX + maxX) / 2d;
            double y = (minY + maxY) / 2d;
            RouteOrderPlanner.Point point = new RouteOrderPlanner.Point(x, y);
            return point.isFinite() ? point : null;
        }
    }

    static final class Pin {
        final String zone;
        final String aisle;
        final String section;
        final RouteOrderPlanner.Point center;

        Pin(String zone, String aisle, String section, RouteOrderPlanner.Point center) {
            this.zone = zone;
            this.aisle = aisle;
            this.section = section;
            this.center = center;
        }
    }

    static final class ItemLocation {
        final int originalIndex;
        final String zone;
        final String aisle;
        final String section;

        ItemLocation(int originalIndex, String zone, String aisle, String section) {
            this.originalIndex = originalIndex;
            this.zone = zone;
            this.aisle = aisle;
            this.section = section;
        }
    }

    static List<RouteOrderPlanner.Entrance> entrancesFromPois(List<Poi> pois) {
        List<RouteOrderPlanner.Entrance> entrances = new ArrayList<>();
        if (pois == null) return entrances;
        for (Poi poi : pois) {
            if (poi == null || !isEntrance(poi) || isExitOnly(poi)) continue;
            RouteOrderPlanner.Point center = poi.center();
            if (center != null) entrances.add(new RouteOrderPlanner.Entrance(entranceId(poi), center));
        }
        return entrances;
    }

    static List<Integer> orderIndexes(List<Poi> pois, List<Pin> pins, List<ItemLocation> items) {
        if (items == null || items.size() < 2) return null;
        List<RouteOrderPlanner.Entrance> entrances = entrancesFromPois(pois);
        if (entrances.isEmpty()) return null;
        Map<String, ArrayDeque<RouteOrderPlanner.Point>> pinsByLocation = new HashMap<>();
        if (pins != null) {
            for (Pin pin : pins) {
                if (pin == null || pin.center == null || !pin.center.isFinite()) continue;
                String key = key(pin.zone, pin.aisle, pin.section);
                ArrayDeque<RouteOrderPlanner.Point> centers = pinsByLocation.get(key);
                if (centers == null) {
                    centers = new ArrayDeque<>();
                    pinsByLocation.put(key, centers);
                }
                centers.addLast(pin.center);
            }
        }
        List<RouteOrderPlanner.Stop> stops = new ArrayList<>();
        for (ItemLocation item : items) {
            if (item == null) return null;
            ArrayDeque<RouteOrderPlanner.Point> centers = pinsByLocation.get(key(item.zone, item.aisle, item.section));
            if (centers == null || centers.isEmpty()) return null;
            stops.add(new RouteOrderPlanner.Stop(item.originalIndex, centers.removeFirst()));
        }
        RouteOrderPlanner.Result result = RouteOrderPlanner.optimize(entrances, stops);
        return result == null ? null : result.orderedOriginalIndexes;
    }

    private static boolean isEntrance(Poi poi) {
        String label = normalizedLabel(poi);
        return label.contains("entrance") || label.contains(" entry") || label.startsWith("entry ");
    }

    private static boolean isExitOnly(Poi poi) {
        String label = normalizedLabel(poi);
        return label.contains("exit") && !label.contains("entrance");
    }

    private static String entranceId(Poi poi) {
        if (poi.name != null && !poi.name.trim().isEmpty()) return poi.name.trim();
        return poi.internalName == null ? "entrance" : poi.internalName.trim();
    }

    private static String normalizedLabel(Poi poi) {
        return ((poi.name == null ? "" : poi.name) + " "
                + (poi.internalName == null ? "" : poi.internalName)).toLowerCase(Locale.US);
    }

    private static String key(String zone, String aisle, String section) {
        return safe(zone) + "\u001f" + safe(aisle) + "\u001f" + safe(section);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
