# Route My List: Coding Handoff

## Objective

Make the Walmart Morphe patch order \Route my list\ by actual in-store map coordinates, beginning at the main entrance and using straight-line distance. Do not implement shelf-aware pathfinding. The native map, cards, lines, carousel, checkoff auto-advance, and Flash Price Tag integration already exist.

## Status: Completed & Verified Live on Device (v1.1.5)

- **Repository:** \C:\\Users\\scgry\\morphe\\walmart-patches- **Remote:** \https://github.com/R2bEEaton/walmart-route-my-list-morphe- **Branch:** \main- **Latest Head:** d999a1 fix: focus carousel and camera on coordinate route start item- **Latest Release:** \1.1.5\ (published and attested on GitHub Actions)
- **Target Device:** ƒ60DLCH002HZ- **Target App:** \com.walmart.android\, Walmart \26.38
---

## Root Cause Analysis (Focus on Start Item vs Coordinate Tour)

When opening \Route my list\ / tapping \PLAN MY ROUTE\:
1. The shopping list items may start with an item in General Merchandise or far from the entrance (e.g. \Purolator LX7317 Oil Filter\ at aisle \M19\).
2. In earlier builds, even after calculating the entrance-aware coordinate tour (which begins at front grocery, e.g. \A16 Section 14\), the view model's native pins list (\	1\), the carousel selection (\itemCarouselView.b\), and the camera focus halo (\isPrimary\) were not synchronously forced to stop 0 of the coordinate tour.
3. If eordered.equals(previousPins)\ hit an early return, or if carousel view state was updated after native adapter binding without resetting selection to item 0, the screen could focus on the first item of the unpermuted shopping list rather than stop 0 of the coordinate mapping.

---

## Changes Implemented in v1.1.5

1. **Synchronous Native Pin Reordering with Primary Pin Assignment (\WalmartRouteMyList.java\):**
   - Added eorderNativePins(List<?> nativePins, List<Object> reorderedPins)\. Reconstructs \StoreMapPinItemDetails\ instances in exact coordinate sequence.
   - Strictly sets \PinOptions.isPrimary = Boolean.valueOf(i == 0)\, ensuring pin 0 has the active focus halo while pins 1..N have \isPrimary = false\.
   - Clears leaving item state (\A1 = null\, \B1 = 0L\, \C1 = false\) and triggers \iewModel.Me()\ to notify observers.

2. **Carousel Selection & Scroll Synchronization:**
   - Added \syncCarouselSelection(Object routeFragment, List<Object> reorderedPins)\.
   - Sets \itemCarouselView.b = firstItemId\ and calls \itemCarouselView.scrollToPosition(0)\ with post-runnable layout requests to ensure the UI card immediately focuses on stop 0.

3. **Instant Focus via Static Coordinate Caching:**
   - Added \STORE_PIN_COORDINATES\ (\ConcurrentHashMap<String, Point>\) keyed by \storeId:zone:aisle:section\.
   - Populated from \SelectedMapArea.f\ when map geometry is received.
   - On subsequent entries within the app session, \onNativeRouteMyListViewCreated\ immediately applies the coordinate tour on stop 0 without waiting 2.5 seconds for the WebView to finish loading.

4. **Comparator Sort Ordering:**
   - Updated \computeSortedPinItems\ to sort by zone, natural aisle, and natural section matching Walmart's native ordering.

5. **Integration Test (\RouteMyListGeometryIntegrationTest.java\):**
   - Added \ordersStartingAtClosestToFrontEntranceRegardlessOfOriginalItemOrder()\.
   - Verifies that even when the input list starts with GM item \M-19\, the resulting coordinate tour sequence starts with \A-16\ at index 0.

---

## On-Device Verification

1. Released \1.1.5\ via GitHub Actions with \patches-1.1.5.mpp\.
2. Refreshed Morphe Manager patch source to \R2bEEaton Walmart Route My List 1.1.5\.
3. Repatched Walmart \26.38\ from saved original APK and installed in-place on device ƒ60DLCH002HZ\.
4. Opened Walmart, navigated to list 6-10-04 (7 items)\ (where list item 0 is \Purolator LX7317 Oil Filter @ M19\), and tapped **PLAN MY ROUTE**.
5. Logcat confirmed immediate entrance-aware coordinate ordering:
   \\	ext
   10-05 12:40:12.536 17494 17494 I WalmartRouteMyList: findProducts returned 7 product(s)
   10-05 12:40:12.539 17494 17494 I WalmartRouteMyList: Opened native Route My List with 7 pin(s)
   10-05 12:40:12.583 17494 17494 I WalmartRouteMyList: Route My List reordered 7 item(s) from entrance-aware map geometry
   10-05 12:40:14.875 17494 17494 I WalmartRouteMyList: Route My List flash capability became available after 3 check(s)
   \6. Live screen inspection verified that the bottom card and map focus centered on stop 0 of the coordinate tour (\Great Value Cinnamon Applesauce @ Aisle 16 Section 14\) at the grocery entrance, and not on \Purolator LX7317 Oil Filter @ M19\.
