# Route My List: Coding Handoff

## Objective

Make the Walmart Morphe patch order `Route my list` by actual in-store map coordinates, beginning at the main entrance and using straight-line distance. Do not implement shelf-aware pathfinding. The native map, cards, lines, carousel, checkoff auto-advance, and Flash Price Tag integration already exist.

## Status: Completed & Verified Live on Device (v1.1.4)

- **Repository:** `C:\Users\scgry\morphe\walmart-patches`
- **Remote:** `https://github.com/R2bEEaton/walmart-route-my-list-morphe`
- **Branch:** `main`
- **Latest Head:** `647e41e chore: Release v1.1.4 [skip ci]`
- **Latest Release:** `v1.1.4` (published and attested on GitHub Actions)
- **Target Device:** `62260DLCH002HZ`
- **Target App:** `com.walmart.android`, Walmart `v26.38`

---

## Root Cause Analysis

Diagnostics and dex decompilation of Walmart's `com.walmart.glass.instoremaps` models (`ViewModelJ`, `MapDataReadyPayload`, `PointOfInterest`, `SelectedMapArea`) revealed:
1. `MapDataReadyPayload.b` contains 7 store amenities (Bakery, Pharmacy, Pickup, Restroom, Deli, Customer Service, Vision Center), but their coordinate fields (`f, g, h, i` = `minX, maxX, minY, maxY`) are all `null` because Walmart's WebView JS bridge does not populate bounding boxes for amenities.
2. In Walmart store floorplan SVGs, entrances are tagged with `typeid="-1"` and are not included in the JS bridge's POI list at all.
3. `RouteMyListGeometry.orderIndexes(pois, pins, items)` previously required `entrancesFromPois(pois)`. Because `pois` contained no entrance POIs with coordinates, `entrances` was empty and `orderIndexes` returned `null`, triggering fallback to alphabetical aisle order (`Route My List geometry was unavailable; retaining aisle order`).
4. Pin coordinates (`SelectedMapArea.f`), however, are always populated with precise, finite in-store coordinate pairs for all shopping list items. In the SVG floorplan coordinates, maximum Y corresponds to the front store wall where entrances and checkout banks are located.

---

## Changes Implemented in v1.1.4

1. **Synthesized Entrance Fallback (`RouteMyListGeometry.java`):**
   - Added `fallbackEntrances(List<Pin> pins)` to compute standard front-edge store entrances (`grocery_entrance` at `(minX, maxY)`, `gm_entrance` at `(maxX, maxY)`, and `main_entrance` at `((minX+maxX)/2, maxY)`).
   - In `orderIndexes(pois, pins, items)`, if `entrancesFromPois(pois)` is empty, fallback to `fallbackEntrances(pins)`.
   - Improved pin center assignment when multiple items share an aisle (`centers.size() > 1 ? centers.removeFirst() : centers.peekFirst()`).

2. **Integration Test (`RouteMyListGeometryIntegrationTest.java`):**
   - Added `usesFallbackFrontEntrancesWhenPoisDoNotContainEntrances` testing the exact 7 items captured from the live store.
   - Asserts optimal tour starting at front grocery (`A-16`), moving through grocery to back (`A-33`), through GM (`J-31`, `M-19`), and finishing at center store (`K-4`), returning `[0, 1, 2, 3, 4, 6, 5]`. All unit tests pass.

---

## On-Device Verification

1. Updated Morphe Manager patch source to `R2bEEaton Walmart Route My List 1.1.4`.
2. Repatched Walmart `v26.38` from saved original APK and installed in-place.
3. Opened Walmart, navigated to list `2026-10-04 (7 items)`, and tapped **PLAN MY ROUTE**.
4. Logcat confirmed successful entrance-aware coordinate reordering:
   ```text
   10-05 11:59:42.181  6420  6420 I WalmartRouteMyList: RENDER_PINS_REQUESTED sent to the mounted map, primary index 0
   10-05 11:59:42.181  6420  6420 I WalmartRouteMyList: Route My List reordered 7 item(s) from entrance-aware map geometry
   10-05 11:59:42.676  6420  6420 I WalmartRouteMyList: Route My List flash capability became available after 4 check(s)
   ```
   No fallbacks, no rollbacks, no crashes. The carousel and native map pins match the optimal straight-line TSP sequence starting from the front entrance.
