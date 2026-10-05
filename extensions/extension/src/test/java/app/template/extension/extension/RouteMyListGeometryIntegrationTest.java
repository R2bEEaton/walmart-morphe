package app.template.extension.extension;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RouteMyListGeometryIntegrationTest {
    @Test
    public void usesEntrancePoiCenterAndRejectsExitOnlyLabels() {
        List<RouteOrderPlanner.Entrance> entrances = RouteMyListGeometry.entrancesFromPois(Arrays.asList(
                new RouteMyListGeometry.Poi("Main Entrance", null, 0, 10, 10, 30),
                new RouteMyListGeometry.Poi("Exit", null, 100, 120, 5, 15)));

        assertEquals(1, entrances.size());
        assertEquals("Main Entrance", entrances.get(0).id);
        assertEquals(5d, entrances.get(0).point.x, 0.0001d);
        assertEquals(20d, entrances.get(0).point.y, 0.0001d);
    }

    @Test
    public void matchesPinsByZoneAisleAndSectionInOriginalDuplicateOrder() {
        List<Integer> indexes = RouteMyListGeometry.orderIndexes(
                Collections.singletonList(new RouteMyListGeometry.Poi("Entrance", null, 0, 0, 0, 0)),
                Arrays.asList(
                        new RouteMyListGeometry.Pin("zone", "A1", "1", new RouteOrderPlanner.Point(2, 0)),
                        new RouteMyListGeometry.Pin("zone", "A1", "1", new RouteOrderPlanner.Point(2, 0)),
                        new RouteMyListGeometry.Pin("zone", "A2", "1", new RouteOrderPlanner.Point(1, 0))),
                Arrays.asList(
                        new RouteMyListGeometry.ItemLocation(0, "zone", "A1", "1"),
                        new RouteMyListGeometry.ItemLocation(1, "zone", "A1", "1"),
                        new RouteMyListGeometry.ItemLocation(2, "zone", "A2", "1")));

        assertEquals(Arrays.asList(0, 1, 2), indexes);
    }

    @Test
    public void preservesAisleFallbackWhenEntranceOrPinGeometryIsMissing() {
        List<RouteMyListGeometry.ItemLocation> items = Arrays.asList(
                new RouteMyListGeometry.ItemLocation(0, "zone", "A1", "1"),
                new RouteMyListGeometry.ItemLocation(1, "zone", "A2", "1"));

        assertNull(RouteMyListGeometry.orderIndexes(Collections.<RouteMyListGeometry.Poi>emptyList(),
                Collections.<RouteMyListGeometry.Pin>emptyList(), items));
        assertNull(RouteMyListGeometry.orderIndexes(
                Collections.singletonList(new RouteMyListGeometry.Poi("Entrance", null, 0, 0, 0, 0)),
                Collections.singletonList(new RouteMyListGeometry.Pin("zone", "A1", "1",
                        new RouteOrderPlanner.Point(1, 1))), items));
    }

    @Test
    public void rejectsIncompletePermutationBeforeMutatingNativeRouteState() {
        assertNull(RouteMyListGeometry.reorder(Arrays.asList("first", "second"),
                Collections.singletonList(1)));
        assertEquals(Arrays.asList("second", "first"), RouteMyListGeometry.reorder(
                Arrays.asList("first", "second"), Arrays.asList(1, 0)));
    }
}
