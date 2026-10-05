# Route My List: Coding Handoff

## Objective

Make the Walmart Morphe patch order `Route my list` by actual in-store map coordinates, beginning at the main entrance and using straight-line distance. Do not implement shelf-aware pathfinding. The native map, cards, lines, carousel, checkoff auto-advance, and Flash Price Tag integration already exist.

## Repository and deployment

- Repository: `C:\Users\scgry\morphe\walmart-patches`
- Remote: `https://github.com/R2bEEaton/walmart-route-my-list-morphe`
- Branch: `main`
- Current head: `b06b0ce fix: inspect route map metadata`
- Latest published patch bundle: `v1.1.2`
- Release run: `https://github.com/R2bEEaton/walmart-route-my-list-morphe/actions/runs/37327573059` (success)
- Connected phone serial: `62260DLCH002HZ`
- Target app: `com.walmart.android`, Walmart `v26.38`

The phone currently has the prior diagnostic patch source `v1.1.1` installed at `2026-10-05 10:43:21`; `v1.1.2` has been released but **not yet installed**. Rebuild it through Morphe Manager from the saved `v26.38` original, then install in place. This preserves Walmart data and uses the matching Morphe signing identity. Do not uninstall Walmart.

## What works

- `Plan my route`/`Route my list` opens Walmart's native multi-item in-store map.
- Native map remains open while carousel focus changes.
- Item cards show images, aisle, section, quantity, and dynamic sizing.
- Existing custom Prev/Next overlay has been removed.
- Checking an item can auto-advance carousel selection.
- Straight blue route connectors and current-location fallback are implemented.
- Flash Price Tag is surfaced through Walmart's own feature when its native in-store eligibility permits it.
- Coordinate optimization is implemented in pure Java and tested, but has not run on-device because map POI metadata is not mapped correctly yet.

## Current blocker: confirmed runtime evidence

Open the existing Route my list screen, then run:

```powershell
adb -s 62260DLCH002HZ logcat -d -v brief | Select-String -Pattern 'WalmartRouteMyList|RML_PINS|Route My List'
```

On the seven-item live list the `v1.1.1` diagnostics reported:

```text
Route My List geometry pending: mapDataReady=false, selectedMapArea=false
Route My List geometry pending: mapDataReady=true, selectedMapArea=false
Route My List geometry incomplete: pois=0, pins=7, items=7
Route My List geometry was unavailable; retaining aisle order
```

This proves the user is seeing the fallback aisle order, not the new coordinate algorithm. The private field currently assumed to contain POIs (`MapDataReadyPayload.b`) is empty. Pin metadata is valid (`SelectedMapArea.f` gives 7 pins), so do not rewrite the route optimizer.

`v1.1.2` adds `mapDataFields=<field>=<size>` diagnostics to identify every public `List` field on the actual `mapDataReady` payload. Install it, reopen the route, collect logcat, then map the non-empty POI/entrance collection in `geometryPois(...)`.

## Relevant code

- `extensions/extension/src/main/java/app/template/extension/extension/WalmartRouteMyList.java`
  - `waitForCoordinateRouteOrder(...)`: polls map metadata for up to 3 seconds (12 × 250 ms).
  - `tryApplyCoordinateRouteOrder(...)`: reflects native state, translates it to pure geometry, applies a validated permutation to both pins and carousel, and rolls back on any error.
  - `geometryPois(...)`: currently reads `mapDataReady.b`; this is the known bad mapping.
  - `geometryPins(...)`: works against `selectedMapArea.f`.
  - `describeCollectionFields(...)`: new v1.1.2 diagnostic helper. Use its log output to locate the correct POI list field.
- `extensions/extension/src/main/java/app/template/extension/extension/RouteOrderPlanner.java`
  - exact entrance-aware TSP-style ordering for <=12 stops; nearest-neighbor + 2-opt above that.
- `extensions/extension/src/main/java/app/template/extension/extension/RouteMyListGeometry.java`
  - map DTO conversion and validation. Return `null` on incomplete geometry so native aisle order remains safe.
- Tests:
  - `RouteOrderPlannerTest.java`
  - `RouteMyListGeometryIntegrationTest.java`

## Constraints and decisions

- User explicitly requested main entrance as start/end and coordinate/straight-line routing; shelf/path tracing is out of scope.
- Keep fallback behavior if any geometry is incomplete.
- The carousel and native pin list must always receive the identical permutation; commit `46b9b3d` fixed a prior mismatch.
- Avoid manual reconstruction of a floor plan.
- Preserve untracked user files: `classes/` and `docs/superpowers/plans/2026-10-04-route-my-list-navigation.md`.
- `manager.png` and `manager-raw.png` are agent-created temporary screenshots and can be deleted when convenient.

## Recommended next sequence

1. In Morphe Manager, refresh the `R2bEEaton Walmart Route My List` source until it reads `1.1.2`.
2. Patch only Walmart using its saved `v26.38` original; install it in place.
3. Clear logs, open the current list's `Route my list`, and collect `WalmartRouteMyList` logs.
4. Inspect `mapDataFields=...`; select the non-empty list that represents map POIs/entrances and update `geometryPois(...)` accordingly. Validate its element fields before relying on it.
5. Add/update a pure `RouteMyListGeometry` test before altering production mapping, run it red, then implement and run the full test/build suite.
6. Deploy and confirm an on-device log such as `Route My List reordered 7 item(s) from entrance-aware map geometry` before telling the user it is fixed.

## Build and release

```powershell
.\gradlew.bat :extensions:extension:testDebugUnitTest :extensions:extension:build :patches:build generatePatchesList --no-daemon
git add <only intended tracked files>
git commit -m "fix: ..."
git fetch origin
git rebase origin/main
git push origin main
```

Use a Conventional Commit `fix:` or `feat:` for code meant to publish a patch bundle. A `chore:` commit builds but does not create a semantic-release patch version. The release workflow adds its own `[skip ci]` release commit, so rebase before a later push if needed.
