package app.template.extension.extension;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a "Plan my route" button to the Walmart shopping-list screen that opens Walmart's own
 * native in-store map (InterfaceC18119a.c, the same single-item "find in aisle" entry point used
 * by product pages and the list screen's own "storeMaps" click handler), fed with pins for every
 * list item that already has a resolved aisle location. A Prev/Next overlay is injected directly
 * onto the map screen itself (InStoreMapsItemLocatorFragment) to step through items, each step
 * re-invoking the native locator with a different "primary" item.
 *
 * Everything here uses reflection because the patches module cannot compile against Walmart's
 * internal (obfuscated, renamed-per-release) classes. Field/method/class names below match
 * Walmart Android v26.38 (versionCode 26380016) exactly, as traced from a jadx decompile of that
 * build, cross-checked against real call sites in glass/lists/view/lists/C0.java and
 * glass/instoremaps/view/InStoreMapsBaseFragment.java. They WILL need re-verification against
 * logcat output ("WalmartRouteMyList" tag) on first run, and updating for any other app version.
 */
@SuppressWarnings("unused")
public class WalmartRouteMyList {
    private static final String TAG = "WalmartRouteMyList";

    // Arbitrary unique-ish menu item id, unlikely to collide with Walmart's own menu ids.
    public static final int MENU_ITEM_ID = 0x57414C31;

    // State for the currently active route: the sorted pins, which one is "primary" right now,
    // and the ListDetailFragment used both to resolve items and as the (verified-real) receiver
    // for Walmart's own v0 analytics lambda passed into .c(). Cached so the Prev/Next overlay
    // (which lives on the map screen, not the list screen) can re-invoke the locator without
    // needing to re-walk the list screen's view model each time.
    private static Object cachedListFragment;
    private static Object cachedMapFragment;
    private static List<Object> cachedPinItems;
    private static int currentIndex = 0;
    private static View navOverlayView;
    private static long routeSessionId = 0L;
    private static boolean coordinateOrderApplied = false;

    // Retain neither a Fragment nor a View: Route My List fragments are short lived and its
    // carousel is rebuilt as the shopper checks items off.  Weak references only prevent us from
    // registering the same layout observer more than once.
    private static final List<WeakReference<View>> FLASH_ROUTE_ROOTS = new ArrayList<>();

    /** Called from the patched Z0.kb(Menu, MenuInflater) to add our button. */
    public static void addRouteMenuItem(Menu menu) {
        try {
            MenuItem plan = menu.add(0, MENU_ITEM_ID, 0, "Plan my route");
            plan.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            Log.i(TAG, "addRouteMenuItem: added, menu now has " + menu.size() + " item(s)");
        } catch (Throwable t) {
            Log.e(TAG, "addRouteMenuItem failed", t);
        }
    }

    /**
     * Called from the patched Z0.Md(MenuItem). Returns true if we handled the click (which the
     * original Z0.Md always returns anyway, so the patch always returns our result directly).
     */
    public static boolean onMenuItemSelected(MenuItem item, Object listDetailFragment) {
        if (item.getItemId() != MENU_ITEM_ID) {
            return true;
        }
        try {
            cachedListFragment = listDetailFragment;
            cachedPinItems = computeSortedPinItems(listDetailFragment);
            currentIndex = 0;
            routeSessionId++;
            coordinateOrderApplied = false;
            showNativeRouteMyList();
        } catch (Throwable t) {
            Log.e(TAG, "Route My List failed", t);
            try {
                Context context = (Context) callNoArg(listDetailFragment, "requireContext");
                Toast.makeText(context, "Route planner failed: " + t, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
                // Context lookup itself failed; nothing more we can safely do.
            }
        }
        return true;
    }

    /**
     * Called after Walmart creates its multi-item Route My List carousel.  The native view model
     * already knows whether the selected store/item can flash an ESL tag and owns the timer and
     * cooldown.  Asking it to refresh here preserves those rules; we only compact its otherwise
     * large action button into an eye button beside the item's checkbox.
     */
    public static void onNativeRouteMyListViewCreated(Object routeFragment) {
        try {
            cachedMapFragment = routeFragment;
            Object viewModel = callNoArg(routeFragment, "cf");
            viewModel.getClass().getMethod("Me").invoke(viewModel);
            View root = (View) callNoArg(routeFragment, "getView");
            if (root == null) return;
            final long session = routeSessionId;
            root.post(() -> waitForCoordinateRouteOrder(root, routeFragment, session, 0));
            if (!hasFlashRouteObserver(root)) {
                rememberFlashRouteRoot(root);
                root.getViewTreeObserver().addOnGlobalLayoutListener(() -> compactNativeFlashButtons(root));
                root.post(() -> compactNativeFlashButtons(root));
                refreshRouteFlashCapabilityWhenReady(root, viewModel, 0);
            }
            Log.i(TAG, "Native Route My List flash capability refresh requested");
        } catch (Throwable t) {
            // Flashing is an optional enhancement.  Do not interfere with the route if a future
            // Walmart release changes this private view-model API.
            Log.w(TAG, "Unable to initialize Route My List flash control", t);
        }
    }

    /** Waits for Walmart's map-ready metadata and rendered pin rectangles without reopening it. */
    private static void waitForCoordinateRouteOrder(View root, Object routeFragment, long session, int attempt) {
        if (session != routeSessionId || coordinateOrderApplied) return;
        if (!root.isAttachedToWindow()) {
            if (attempt < 12) {
                root.postDelayed(() -> waitForCoordinateRouteOrder(root, routeFragment, session, attempt + 1), 250L);
            }
            return;
        }
        if (tryApplyCoordinateRouteOrder(routeFragment, session)) return;
        if (attempt >= 12) {
            Log.i(TAG, "Route My List geometry was unavailable; retaining aisle order");
            return;
        }
        root.postDelayed(() -> waitForCoordinateRouteOrder(root, routeFragment, session, attempt + 1), 250L);
    }

    /**
     * Converts Walmart's private map state to the pure route planner input.  Reflection failures
     * deliberately leave the already-open native route untouched so aisle order remains usable.
     */
    static boolean tryApplyCoordinateRouteOrder(Object routeFragment, long session) {
        if (session != routeSessionId || coordinateOrderApplied || cachedPinItems == null
                || cachedPinItems.size() < 2 || routeFragment != cachedMapFragment) return false;
        List<Object> previousPins = null;
        List<?> previousNativePins = null;
        List<?> previousCarouselItems = null;
        int previousIndex = currentIndex;
        Object viewModel = null;
        boolean nativeStateMutated = false;
        try {
            viewModel = callNoArg(routeFragment, "cf");
            Object mapDataReady = readField(viewModel, "Y");
            Object selectedMapArea = readField(viewModel, "Z");
            if (mapDataReady == null || selectedMapArea == null) {
                Log.i(TAG, "Route My List geometry pending: mapDataReady="
                        + (mapDataReady != null) + ", selectedMapArea="
                        + (selectedMapArea != null));
                return false;
            }

            List<RouteMyListGeometry.Poi> pois = geometryPois((List<?>) readField(mapDataReady, "b"));
            List<RouteMyListGeometry.Pin> pins = geometryPins((List<?>) readField(selectedMapArea, "f"));
            List<RouteMyListGeometry.ItemLocation> items = geometryItems(cachedPinItems);
            List<Integer> orderedIndexes = RouteMyListGeometry.orderIndexes(pois, pins, items);
            if (orderedIndexes == null || orderedIndexes.size() != cachedPinItems.size()) {
                Log.i(TAG, "Route My List geometry incomplete: pois=" + pois.size()
                        + ", pins=" + pins.size() + ", items=" + items.size()
                        + ", mapDataFields=" + describeCollectionFields(mapDataReady));
                return false;
            }

            previousPins = cachedPinItems;
            List<Object> reordered = RouteMyListGeometry.reorder(previousPins, orderedIndexes);
            previousNativePins = (List<?>) readField(viewModel, "t1");
            previousCarouselItems = (List<?>) readField(viewModel, "z1");
            if (reordered == null || previousNativePins == null || previousCarouselItems == null) return false;
            List<?> reorderedNativePins = RouteMyListGeometry.reorder(previousNativePins, orderedIndexes);
            List<Object> reorderedCarouselItems = reorderCarouselItems(previousCarouselItems, reordered);
            if (reorderedNativePins == null || reorderedCarouselItems == null) return false;
            if (reordered.equals(previousPins)) {
                coordinateOrderApplied = true;
                return true;
            }
            cachedPinItems = reordered;
            nativeStateMutated = true;
            writeField(viewModel, "t1", reorderedNativePins);
            writeField(viewModel, "z1", reorderedCarouselItems);
            currentIndex = 0;
            viewModel.getClass().getMethod("Me").invoke(viewModel);
            coordinateOrderApplied = true;
            try {
                renderMountedMapWithFocusedPin();
            } catch (Throwable renderFailure) {
                throw renderFailure;
            }
            Log.i(TAG, "Route My List reordered " + reordered.size()
                    + " item(s) from entrance-aware map geometry");
            return true;
        } catch (Throwable t) {
            if (nativeStateMutated && viewModel != null) {
                try {
                    cachedPinItems = previousPins;
                    currentIndex = previousIndex;
                    writeField(viewModel, "t1", previousNativePins);
                    writeField(viewModel, "z1", previousCarouselItems);
                    viewModel.getClass().getMethod("Me").invoke(viewModel);
                    renderMountedMapWithFocusedPin();
                } catch (Throwable rollbackFailure) {
                    Log.w(TAG, "Unable to restore native Route My List order", rollbackFailure);
                }
            }
            coordinateOrderApplied = false;
            Log.w(TAG, "Unable to apply Route My List coordinate order", t);
            return false;
        }
    }

    /** Mirrors the same permutation into Walmart's carousel models, keyed by its stable item id. */
    private static List<Object> reorderCarouselItems(List<?> carouselItems, List<Object> reorderedPins)
            throws Exception {
        Map<String, ArrayDeque<Object>> byItemId = new HashMap<>();
        for (Object carouselItem : carouselItems) {
            Object details = readField(carouselItem, "a");
            String itemId = (String) readField(details, "b");
            ArrayDeque<Object> matches = byItemId.get(itemId);
            if (matches == null) {
                matches = new ArrayDeque<>();
                byItemId.put(itemId, matches);
            }
            matches.addLast(carouselItem);
        }
        List<Object> reordered = new ArrayList<>();
        for (Object pinItem : reorderedPins) {
            String itemId = routeItemId(pinItem);
            ArrayDeque<Object> matches = byItemId.get(itemId);
            if (matches == null || matches.isEmpty()) return null;
            reordered.add(matches.removeFirst());
        }
        return reordered.size() == carouselItems.size() ? reordered : null;
    }

    private static String routeItemId(Object pinItem) throws Exception {
        Object details = readField(pinItem, "b");
        return (String) readField(details, "b");
    }

    private static List<RouteMyListGeometry.Poi> geometryPois(List<?> rawPois) throws Exception {
        List<RouteMyListGeometry.Poi> pois = new ArrayList<>();
        if (rawPois == null) return pois;
        for (Object poi : rawPois) {
            Double minX = asDouble(readField(poi, "f"));
            Double maxX = asDouble(readField(poi, "g"));
            Double minY = asDouble(readField(poi, "h"));
            Double maxY = asDouble(readField(poi, "i"));
            if (minX == null || maxX == null || minY == null || maxY == null) continue;
            pois.add(new RouteMyListGeometry.Poi((String) readField(poi, "a"),
                    (String) readField(poi, "b"), minX, maxX, minY, maxY));
        }
        return pois;
    }

    private static List<RouteMyListGeometry.Pin> geometryPins(List<?> rawPins) throws Exception {
        List<RouteMyListGeometry.Pin> pins = new ArrayList<>();
        if (rawPins == null) return pins;
        for (Object pin : rawPins) {
            Object pinRect = readField(pin, "i");
            Object center = pinRect == null ? null : readField(pinRect, "a");
            Double x = center == null ? null : asDouble(readField(center, "a"));
            Double y = center == null ? null : asDouble(readField(center, "b"));
            if (x == null || y == null) continue;
            pins.add(new RouteMyListGeometry.Pin((String) readField(pin, "c"),
                    (String) readField(pin, "d"), (String) readField(pin, "e"),
                    new RouteOrderPlanner.Point(x, y)));
        }
        return pins;
    }

    private static List<RouteMyListGeometry.ItemLocation> geometryItems(List<Object> pinItems)
            throws Exception {
        Class<?> pinItemCls = Class.forName("com.walmart.glass.instoremaps.api.model.StoreMapPinItemDetails");
        Class<?> pinOptionsCls = Class.forName("com.walmart.glass.instoremaps.api.PinOptions");
        Field itemOptions = pinItemCls.getField("a");
        Field zone = pinOptionsCls.getField("a");
        Field aisle = pinOptionsCls.getField("b");
        Field section = pinOptionsCls.getField("c");
        List<RouteMyListGeometry.ItemLocation> items = new ArrayList<>();
        for (int index = 0; index < pinItems.size(); index++) {
            Object options = itemOptions.get(pinItems.get(index));
            items.add(new RouteMyListGeometry.ItemLocation(index, (String) zone.get(options),
                    (String) aisle.get(options), (String) section.get(options)));
        }
        return items;
    }

    private static Double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static String describeCollectionFields(Object value) {
        StringBuilder result = new StringBuilder();
        for (Field field : value.getClass().getFields()) {
            try {
                Object fieldValue = field.get(value);
                if (fieldValue instanceof List<?>) {
                    if (result.length() > 0) result.append(',');
                    result.append(field.getName()).append('=').append(((List<?>) fieldValue).size());
                }
            } catch (IllegalAccessException ignored) {
                // Diagnostics must never affect the native route.
            }
        }
        return result.toString();
    }

    /**
     * The Route My List carousel is first bound before Walmart's remote flash configuration has
     * arrived.  Its own ViewModel intentionally omits the action until that state is available;
     * recompute once it is ready so the native button (and its native cooldown flow) can bind.
     */
    private static void refreshRouteFlashCapabilityWhenReady(View root, Object viewModel, int attempt) {
        if (!root.isAttachedToWindow() || attempt >= 6) return;
        try {
            Object flashConfigState = readField(viewModel, "r1");
            Object flashConfig = callNoArg(flashConfigState, "getValue");
            if (flashConfig != null) {
                viewModel.getClass().getMethod("Me").invoke(viewModel);
                Log.i(TAG, "Route My List flash capability became available after " + attempt + " check(s)");
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to check Route My List flash capability", t);
            return;
        }
        root.postDelayed(() -> refreshRouteFlashCapabilityWhenReady(root, viewModel, attempt + 1), 750L);
    }

    /**
     * Called after Walmart's own checkbox listener has updated its route state. Scrolling the
     * native RecyclerView keeps its item-focus callback, map pin selection, and animations intact.
     */
    public static void onNativeRouteCheckboxToggled(Object carouselClickListener) {
        try {
            Object routeFragment = readField(carouselClickListener, "a");
            View root = (View) callNoArg(routeFragment, "getView");
            if (root != null) {
                root.post(() -> advanceToNextUnchecked(routeFragment));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to schedule Route My List auto-advance", t);
        }
    }

    private static void advanceToNextUnchecked(Object routeFragment) {
        try {
            Object binding = callNoArg(routeFragment, "Ve");
            Object carouselBinding = readField(binding, "h");
            Object carousel = readField(carouselBinding, "b");
            String completedItemId = (String) readField(carousel, "b");
            Object adapter = readField(carousel, "a");
            List<?> items = (List<?>) readField(adapter, "c");
            if (items == null || items.isEmpty()) return;

            int completedIndex = -1;
            for (int index = 0; index < items.size(); index++) {
                Object itemDetails = readField(items.get(index), "a");
                String itemId = (String) readField(itemDetails, "b");
                if (completedItemId.equals(itemId)) {
                    completedIndex = index;
                    break;
                }
            }
            if (completedIndex < 0) return;

            for (int offset = 1; offset < items.size(); offset++) {
                int nextIndex = (completedIndex + offset) % items.size();
                Object candidate = items.get(nextIndex);
                boolean checked = ((Boolean) readField(candidate, "c")).booleanValue();
                if (!checked) {
                    carousel.getClass().getMethod("smoothScrollToPosition", int.class)
                            .invoke(carousel, nextIndex);
                    Log.i(TAG, "Advanced Route My List to unchecked carousel item " + (nextIndex + 1));
                    return;
                }
            }
            Log.i(TAG, "All Route My List items are checked; leaving native completed state selected");
        } catch (Throwable t) {
            Log.w(TAG, "Unable to advance Route My List carousel", t);
        }
    }

    private static synchronized boolean hasFlashRouteObserver(View root) {
        for (int i = FLASH_ROUTE_ROOTS.size() - 1; i >= 0; i--) {
            View observed = FLASH_ROUTE_ROOTS.get(i).get();
            if (observed == null) {
                FLASH_ROUTE_ROOTS.remove(i);
            } else if (observed == root) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void rememberFlashRouteRoot(View root) {
        FLASH_ROUTE_ROOTS.add(new WeakReference<>(root));
    }

    /** Walks the native carousel after each bind.  Only native buttons whose own label says
     * "flash" are replaced; navigation, item-detail, and check-off controls remain untouched. */
    private static void compactNativeFlashButtons(View root) {
        try {
            List<View> allViews = new ArrayList<>();
            collectViews(root, allViews);
            hideRouteFeedbackPrompt(allViews);
            collapseUnusedCarouselCardSpace(allViews);
            for (View candidate : allViews) {
                if (!candidate.getClass().getName().endsWith("WcpButton")) continue;
                if (!(candidate instanceof TextView)) continue;
                String label = String.valueOf(((TextView) candidate).getText()).toLowerCase();
                if (!label.contains("flash")) continue;
                compactNativeFlashButton((TextView) candidate);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to compact native flash control", t);
        }
    }

    /** Removes only the native feedback sentence shown below Route My List's carousel. */
    private static void hideRouteFeedbackPrompt(List<View> views) {
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            String text = String.valueOf(((TextView) view).getText());
            if (text.startsWith("We'd love to hear what you think!") ||
                    "Give feedback".equals(text.trim())) {
                view.setVisibility(View.GONE);
            }
        }
    }

    /** Let the native Add-back action expand a carousel card only when it is actually rendered. */
    private static void collapseUnusedCarouselCardSpace(List<View> views) {
        for (View view : views) {
            if (!(view instanceof ViewGroup) ||
                    !hasResourceEntryName(view, "instoremaps_item_carousel_card_root")) continue;
            ViewGroup card = (ViewGroup) view;
            boolean addBackIsVisible = false;
            for (int i = 0; i < card.getChildCount(); i++) {
                View child = card.getChildAt(i);
                if (hasResourceEntryName(child, "instoremaps_item_carousel_add_back_button")
                        && child.getVisibility() == View.VISIBLE) {
                    addBackIsVisible = true;
                    break;
                }
            }
            ViewGroup.LayoutParams params = card.getLayoutParams();
            if (params == null) continue;
            int desiredHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (params.height != desiredHeight) {
                params.height = desiredHeight;
                card.setLayoutParams(params);
            }
            // The horizontal RecyclerView measures every item to its full carousel height.  The
            // native card itself is a ConstraintLayout, whose maxHeight is honored after that
            // parent measurement; it is the constraint that removes the otherwise blank area.
            int contentBottom = 0;
            for (int i = 0; i < card.getChildCount(); i++) {
                View child = card.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    contentBottom = Math.max(contentBottom, child.getBottom());
                }
            }
            setCardMaximumHeight(card, addBackIsVisible
                    ? Integer.MAX_VALUE
                    : contentBottom + dp(card, 16));
            card.requestLayout();
            final boolean hasAddBackAction = addBackIsVisible;
            card.post(() -> {
                // LinearLayoutManager top-aligns every carousel item. Compact cards must instead
                // share the full card's bottom edge so neighboring cards do not appear to float.
                int tallestAttachedCard = tallestAttachedCarouselCard(card);
                float bottomAlignment = hasAddBackAction ? 0f
                        : Math.max(0, tallestAttachedCard - card.getHeight());
                card.setTranslationY(bottomAlignment);
            });
        }
    }

    private static int tallestAttachedCarouselCard(View card) {
        if (!(card.getParent() instanceof ViewGroup)) return card.getHeight();
        ViewGroup carousel = (ViewGroup) card.getParent();
        int tallest = card.getHeight();
        for (int i = 0; i < carousel.getChildCount(); i++) {
            View sibling = carousel.getChildAt(i);
            if (hasResourceEntryName(sibling, "instoremaps_item_carousel_card_root")) {
                tallest = Math.max(tallest, sibling.getHeight());
            }
        }
        return tallest;
    }

    private static void setCardMaximumHeight(View card, int maxHeight) {
        try {
            // Keep this reflective: the extension is compiled independently of Walmart's
            // ConstraintLayout version, while the runtime method is stable across its releases.
            card.getClass().getMethod("setMaxHeight", int.class).invoke(card, maxHeight);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to adjust Route My List card maximum height", t);
        }
    }

    private static boolean hasResourceEntryName(View view, String entryName) {
        int id = view.getId();
        if (id == View.NO_ID) return false;
        try {
            return entryName.equals(view.getResources().getResourceEntryName(id));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void compactNativeFlashButton(TextView nativeButton) {
        ViewGroup parent = nativeButton.getParent() instanceof ViewGroup
                ? (ViewGroup) nativeButton.getParent() : null;
        if (parent == null) return;

        TextView eye = findFlashEye(parent, nativeButton);
        if (eye == null) {
            eye = new TextView(parent.getContext());
            eye.setText("\uD83D\uDC41");
            eye.setTextSize(19);
            eye.setGravity(Gravity.CENTER);
            eye.setTextColor(Color.rgb(0, 113, 206));
            eye.setContentDescription("Flash price tag");
            eye.setPadding(0, 0, 0, 0);
            eye.setTag(nativeButton);

            if (!addEyeBesideCheckbox(parent, eye)) return;
            final TextView eyeControl = eye;
            eye.setOnClickListener(v -> {
                // The native click listener emits the real flashPriceLabel event and hands its
                // timer/cooldown state back to the carousel. We deliberately do not synthesize a
                // flash request or a local timer here.
                eyeControl.setEnabled(false);
                eyeControl.setAlpha(0.45f);
                nativeButton.performClick();
            });
        }

        // When the native timer is showing, let its visible cooldown UI take over. Once its
        // state machine returns to the normal flash action this method makes the eye active again.
        if (containsVisibleTimedButton(parent)) {
            eye.setVisibility(View.GONE);
            return;
        }
        eye.setVisibility(View.VISIBLE);
        eye.setEnabled(true);
        eye.setAlpha(1f);
        nativeButton.setVisibility(View.GONE);
    }

    private static TextView findFlashEye(ViewGroup parent, TextView nativeButton) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && child.getTag() == nativeButton) return (TextView) child;
        }
        return null;
    }

    private static boolean addEyeBesideCheckbox(ViewGroup parent, TextView eye) {
        try {
            View checkbox = findViewByClassSuffix(parent, "WcpCheckbox");
            if (checkbox == null || checkbox.getId() == View.NO_ID ||
                    !parent.getClass().getName().endsWith("ConstraintLayout")) return false;
            Class<?> paramsClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams");
            Object params = paramsClass.getConstructor(int.class, int.class)
                    .newInstance(dp(parent, 36), dp(parent, 36));
            paramsClass.getField("endToStart").setInt(params, checkbox.getId());
            paramsClass.getField("topToTop").setInt(params, checkbox.getId());
            paramsClass.getField("bottomToBottom").setInt(params, checkbox.getId());
            parent.addView(eye, (ViewGroup.LayoutParams) params);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsVisibleTimedButton(View root) {
        List<View> views = new ArrayList<>();
        collectViews(root, views);
        for (View view : views) {
            if (view.getVisibility() == View.VISIBLE &&
                    view.getClass().getName().endsWith("TimedButtonView")) return true;
        }
        return false;
    }

    private static View findViewByClassSuffix(View root, String suffix) {
        if (root.getClass().getName().endsWith(suffix)) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findViewByClassSuffix(group.getChildAt(i), suffix);
            if (found != null) return found;
        }
        return null;
    }

    private static void collectViews(View root, List<View> output) {
        output.add(root);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectViews(group.getChildAt(i), output);
        }
    }

    private static int dp(View view, int dp) {
        return (int) (dp * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Opens Walmart's own multi-item Route My List destination. This is the internal flow that
     * owns the provider-specific entrance/exit route line; the patch only supplies resolved list
     * pins and the normal shopping-list launch context.
     */
    private static void showNativeRouteMyList() throws Exception {
        if (cachedPinItems == null || cachedPinItems.isEmpty()) return;

        Class<?> pinItemCls = Class.forName("com.walmart.glass.instoremaps.api.model.StoreMapPinItemDetails");
        Class<?> routeDetailsCls = Class.forName("com.walmart.glass.instoremaps.api.model.RouteMyListAnalyticsDetails");
        Class<?> contextEnumCls = Class.forName("com.walmart.analytics.schema.ContextEnum");
        Class<?> multiItemMapCls = Class.forName("com.walmart.glass.instoremaps.view.InStoreMapsMultiItemLocatorFragment");
        Class<?> routeArgsCls = Class.forName("com.walmart.glass.instoremaps.view.g0");
        Class<?> navigationApiCls = Class.forName("glass.platform.navigation.api.f");
        Class<?> navigationActionsCls = Class.forName("glass.platform.navigation.api.e");
        Class<?> registryCls = Class.forName("glass.platform.registry.api.a");
        Class<?> function1Cls = Class.forName("kotlin.jvm.functions.Function1");

        Object context = contextEnumCls.getField("myItems").get(null);
        Object analyticsDetails = routeDetailsCls.getConstructor(contextEnumCls, String.class, String.class)
                .newInstance(context, "", "");
        String storeId = getCurrentStoreId(cachedListFragment);
        Object pinArray = java.lang.reflect.Array.newInstance(pinItemCls, cachedPinItems.size());
        for (int index = 0; index < cachedPinItems.size(); index++) {
            java.lang.reflect.Array.set(pinArray, index, cachedPinItems.get(index));
        }
        Object routeArgs = routeArgsCls.getConstructor(pinArray.getClass(), String.class, routeDetailsCls)
                .newInstance(pinArray, storeId, analyticsDetails);
        Object routeFragment = multiItemMapCls.getConstructor().newInstance();
        multiItemMapCls.getMethod("setArguments", Class.forName("android.os.Bundle"))
                .invoke(routeFragment, routeArgsCls.getMethod("a").invoke(routeArgs));

        Object navigationApi = getFromRegistryUsingE(registryCls, navigationApiCls);
        if (navigationApi == null) {
            throw new IllegalStateException("Walmart navigation API is unavailable.");
        }
        Object navigationCallback = Proxy.newProxyInstance(
                function1Cls.getClassLoader(), new Class[]{function1Cls}, (proxy, method, args) -> {
                    if ("invoke".equals(method.getName()) && args != null && args.length == 1) {
                        return navigationActionsCls.getMethod("Q", Class.forName("androidx.fragment.app.Fragment"), boolean.class)
                                .invoke(args[0], routeFragment, true);
                    }
                    if ("toString".equals(method.getName())) return "RouteMyListNavigationCallback";
                    return null;
                });
        Context androidContext = (Context) callNoArg(cachedListFragment, "requireContext");
        navigationApiCls.getMethod("O2", Context.class, function1Cls)
                .invoke(navigationApi, androidContext, navigationCallback);
        Log.i(TAG, "Opened native Route My List with " + cachedPinItems.size() + " pin(s)");
    }

    /** Gets the active shopping-list store ID using the same view-model path as Walmart's UI. */
    private static String getCurrentStoreId(Object listDetailFragment) {
        try {
            Object viewModel = callNoArg(listDetailFragment, "Ye");
            Object storeLiveData = viewModel.getClass().getField("l").get(viewModel);
            Object store = callNoArg(storeLiveData, "getValue");
            Object storeId = store == null ? null : store.getClass().getField("a").get(store);
            return storeId == null ? "" : storeId.toString();
        } catch (Throwable t) {
            Log.w(TAG, "Unable to read selected store ID; native Route My List will resolve it", t);
            return "";
        }
    }

    /** Called from the patched InStoreMapsBaseFragment.onViewCreated to add the Prev/Next bar. */
    public static void onMapFragmentViewCreated(Object mapFragment) {
        try {
            cachedMapFragment = mapFragment;
            injectNavBar(mapFragment);
        } catch (Throwable t) {
            Log.e(TAG, "injectNavBar failed", t);
        }
    }

    /** Called from the patched InStoreMapsItemLocatorFragment.onDestroyView to clean up the bar. */
    public static void onMapFragmentDestroyed(Object mapFragment) {
        if (cachedMapFragment == mapFragment) {
            cachedMapFragment = null;
        }
        removeNavBar();
    }

    /** Builds (or rebuilds) the list of pins/items for the current list, sorted by aisle code. */
    private static List<Object> computeSortedPinItems(Object fragment) throws Exception {
        List<Object> products = findProducts(fragment);
        Log.i(TAG, "findProducts returned " + products.size() + " product(s)");

        Class<?> pinOptionsCls = Class.forName("com.walmart.glass.instoremaps.api.PinOptions");
        Class<?> pinTypeCls = Class.forName("com.walmart.glass.instoremaps.api.l0");
        Class<?> itemDetailsCls = Class.forName("com.walmart.glass.instoremaps.api.model.InstoreMapsItemDetails");
        Class<?> storeMapPinItemDetailsCls = Class.forName("com.walmart.glass.instoremaps.api.model.StoreMapPinItemDetails");

        Constructor<?> pinOptionsCtor = pinOptionsCls.getConstructor(
                String.class, String.class, String.class, String.class, Boolean.class,
                Integer.class, pinTypeCls, Boolean.class, Boolean.class);
        Constructor<?> itemDetailsCtor = itemDetailsCls.getConstructor(
                String.class, String.class, String.class, String.class, String.class, Double.class,
                String.class, String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, List.class, int.class);
        Constructor<?> pinItemCtor = storeMapPinItemDetailsCls.getConstructor(pinOptionsCls, itemDetailsCls);

        List<Object> pinItems = new ArrayList<>();
        for (Object product : products) {
            // Real compiled getter names differ from jadx's Kotlin-metadata display names
            // (e.g. getName() is really "e", getUsItemId() is really "D3"); both are tried.
            String name = (String) tryCallAny(product, "getName", "e");
            String itemId = (String) tryCallAny(product, "getUsItemId", "D3");
            if (isEmpty(itemId)) {
                itemId = (String) tryCallAny(product, "getId", "d");
            }

            Object productLocation = tryCallAny(product, "getProductLocation", "K");
            if (productLocation == null) {
                Log.i(TAG, "Skipping '" + name + "': no productLocation");
                continue;
            }
            String displayValue = (String) tryCallAny(productLocation, "getDisplayValue", "a");
            Object aisle = tryCallAny(productLocation, "getAisle", "b");

            String zone = "";
            String aisleNumber = "";
            String section = "";
            if (aisle != null) {
                Object z = tryCallAny(aisle, "getZone", "a");
                Object a = tryCallAny(aisle, "getAisle", "b");
                Object s = tryCallAny(aisle, "getSection", "d");
                if (z != null) zone = z.toString();
                if (a != null) aisleNumber = a.toString();
                if (s != null) section = s.toString();
            }
            if (isEmpty(aisleNumber)) {
                Log.i(TAG, "Skipping '" + name + "': no resolved aisle number");
                continue;
            }
            if (itemId == null) itemId = "";
            if (name == null) name = "";
            Object imageInfo = tryCallAny(product, "getImageInfo", "P", "g2");
            String thumbnailUrl = imageInfo == null ? null : (String) tryCallAny(
                    imageInfo, "getThumbnailUrl", "a", "i");
            String preciseLocation = aisleNumber;
            if (!isEmpty(section)) {
                preciseLocation += " \u00b7 Section " + section;
            }

            Object pinOptions = pinOptionsCtor.newInstance(
                    zone, aisleNumber, section, null, null, null, null, null, null);
            Object itemDetails = itemDetailsCtor.newInstance(
                    thumbnailUrl, itemId, name, preciseLocation, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    Collections.emptyList(), 1);
            pinItems.add(pinItemCtor.newInstance(pinOptions, itemDetails));
        }

        if (pinItems.isEmpty()) {
            throw new IllegalStateException(
                    "No list items had a resolved aisle location (see earlier 'Skipping' log lines)");
        }

        // Approximate a sensible walking order with no floorplan graph available: a natural
        // (alphanumeric-aware) sort of the aisle code puts e.g. "A2" before "A10" and groups
        // same-letter aisles together, which is a reasonable proxy for "walk the aisles in order."
        Field pinOptionsAisleField = pinOptionsCls.getField("b");
        Field pinItemOptionsField = storeMapPinItemDetailsCls.getField("a");
        Collections.sort(pinItems, (p1, p2) -> {
            try {
                String aisle1 = (String) pinOptionsAisleField.get(pinItemOptionsField.get(p1));
                String aisle2 = (String) pinOptionsAisleField.get(pinItemOptionsField.get(p2));
                return naturalCompare(aisle1, aisle2);
            } catch (Exception e) {
                return 0;
            }
        });

        return pinItems;
    }

    /** (Re)opens the native map, focused on cachedPinItems.get(currentIndex), with all pins shown. */
    private static void showMapForCurrentIndex() throws Exception {
        if (cachedPinItems == null || cachedPinItems.isEmpty()) return;
        currentIndex = ((currentIndex % cachedPinItems.size()) + cachedPinItems.size()) % cachedPinItems.size();
        Log.i(TAG, "Showing item " + (currentIndex + 1) + " of " + cachedPinItems.size() + " (sorted by aisle)");

        Class<?> pinOptionsCls = Class.forName("com.walmart.glass.instoremaps.api.PinOptions");
        Class<?> itemDetailsCls = Class.forName("com.walmart.glass.instoremaps.api.model.InstoreMapsItemDetails");
        Class<?> storeMapPinItemDetailsCls = Class.forName("com.walmart.glass.instoremaps.api.model.StoreMapPinItemDetails");
        // Real name is "a" - jadx displayed it as "InterfaceC18119a" only because "a.java" collided
        // with another file on a case-insensitive filesystem during decompilation; that display
        // name never existed in the compiled app.
        Class<?> instoreMapsApiCls = Class.forName("com.walmart.glass.instoremaps.api.a");
        Class<?> registryCls = Class.forName("glass.platform.registry.api.a");

        // Use the single-item locator (.c), proven live today from product pages and the list
        // screen's own existing "storeMaps" click handler (glass/lists/view/lists/C0.java) rather
        // than the unused/unwired .g() route-my-list method. The real call site there is:
        //   interfaceC18119a.c(collection, instoreMapsItemDetails, AbstractC18121c.a.a,
        //       new C14686v0(listDetailFragment, 3));
        // We replicate it exactly, reusing that same merged-lambda receiver pattern with our own
        // fragment instance - a verified-real (arity, receiver-type) pair, not a guess.
        List<Object> allPinOptions = new ArrayList<>();
        for (Object pinItem : cachedPinItems) {
            allPinOptions.add(pinOptionsCls.cast(storeMapPinItemDetailsCls.getField("a").get(pinItem)));
        }
        Object primaryItemDetails = itemDetailsCls.cast(
                storeMapPinItemDetailsCls.getField("b").get(cachedPinItems.get(currentIndex)));

        Class<?> launchSourceCls = Class.forName("com.walmart.glass.instoremaps.api.c");
        Class<?> launchSourceVariantCls = Class.forName("com.walmart.glass.instoremaps.api.c$a");
        Object launchSource = launchSourceVariantCls.getField("a").get(null);

        Class<?> function1Cls = Class.forName("kotlin.jvm.functions.Function1");
        // jadx displayed this as "C14686v0" (a collision-disambiguation prefix, same pattern as
        // InterfaceC18119a -> "a"); the real class name is just the preserved "v0" suffix.
        // (A Proxy implementing Function1 directly was tried to hook the Fragment it receives -
        // confirmed real class com.walmart.glass.instoremaps.view.InStoreMapsItemLocatorFragment -
        // but returning null from it stopped .c() from actually showing the screen, so the real
        // v0 lambda is used here; the fragment is hooked separately via its own patched lifecycle.)
        Class<?> callbackImplCls = Class.forName("com.walmart.glass.checkout.analytics.v0");
        Constructor<?> callbackCtor = callbackImplCls.getDeclaredConstructor(Object.class, int.class);
        callbackCtor.setAccessible(true);
        Object callback = callbackCtor.newInstance(cachedListFragment, 3);

        Object instoreMapsApi = getFromRegistry(registryCls, instoreMapsApiCls);
        if (instoreMapsApi == null) {
            throw new IllegalStateException("glass.platform.registry.api.a returned null for InterfaceC18119a");
        }

        Method cMethod = instoreMapsApiCls.getMethod("c", java.util.Collection.class, itemDetailsCls, launchSourceCls, function1Cls);
        cMethod.invoke(instoreMapsApi, allPinOptions, primaryItemDetails, launchSource, callback);
        Log.i(TAG, "InterfaceC18119a.c() invoked with " + allPinOptions.size() + " pin(s), primary index " + currentIndex);
    }

    /** Adds a floating Prev/Next bar to the map screen's own window, below the item detail card. */
    private static void injectNavBar(Object mapFragment) throws Exception {
        removeNavBar();
        if (cachedPinItems == null || cachedPinItems.size() <= 1) {
            return; // Nothing to page through.
        }

        Method getView = mapFragment.getClass().getMethod("getView");
        View root = (View) getView.invoke(mapFragment);
        if (root == null) {
            Log.i(TAG, "injectNavBar: fragment view is null, skipping");
            return;
        }
        View windowRoot = root.getRootView();
        if (!(windowRoot instanceof ViewGroup)) {
            Log.i(TAG, "injectNavBar: root view is not a ViewGroup, skipping");
            return;
        }
        Context context = root.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.argb(230, 0, 113, 206)); // Walmart blue, mostly opaque
        int padV = (int) (10 * density);
        int padH = (int) (18 * density);
        bar.setPadding(padH, padV, padH, padV);

        Button prev = new Button(context);
        prev.setText("◀ Prev");
        styleNavButton(prev);

        TextView label = new TextView(context);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setPadding(padH, 0, padH, 0);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelParams);

        Button next = new Button(context);
        next.setText("Next ▶");
        styleNavButton(next);

        prev.setOnClickListener(v -> step(-1, label));
        next.setOnClickListener(v -> step(1, label));

        bar.addView(prev);
        bar.addView(label);
        bar.addView(next);
        updateLabel(label);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = (int) (220 * density); // sits just below the item detail card

        ((ViewGroup) windowRoot).addView(bar, params);
        navOverlayView = bar;
        Log.i(TAG, "injectNavBar: added Prev/Next bar for " + cachedPinItems.size() + " items");
    }

    private static void styleNavButton(Button button) {
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.argb(60, 255, 255, 255));
    }

    private static void updateLabel(TextView label) {
        if (cachedPinItems == null) return;
        label.setText((currentIndex + 1) + " of " + cachedPinItems.size());
    }

    private static void step(int delta, TextView label) {
        try {
            if (cachedPinItems == null || cachedPinItems.isEmpty()) {
                return;
            }
            currentIndex += delta;
            currentIndex = ((currentIndex % cachedPinItems.size()) + cachedPinItems.size()) % cachedPinItems.size();
            renderMountedMapWithFocusedPin();
            updateMountedItemCard();
            updateLabel(label);
        } catch (Throwable t) {
            Log.e(TAG, "step failed", t);
        }
    }

    /**
     * Re-renders pins through the WebView already owned by the visible Fragment. The native
     * launcher creates a new Fragment and WebView; this sends the same RENDER_PINS_REQUESTED
     * message to the mounted page. Walmart's response to that message carries the selected map
     * area and its existing callback animates the camera to that area.
     */
    private static void renderMountedMapWithFocusedPin() throws Exception {
        if (cachedMapFragment == null || cachedPinItems == null || cachedPinItems.isEmpty()) {
            throw new IllegalStateException("The active store map is not available.");
        }

        Class<?> pinOptionsCls = Class.forName("com.walmart.glass.instoremaps.api.PinOptions");
        Class<?> pinItemCls = Class.forName("com.walmart.glass.instoremaps.api.model.StoreMapPinItemDetails");
        Class<?> renderPinCls = Class.forName("com.walmart.glass.instoremaps.model.request.RenderPin$Pin");
        Field pinItemOptionsField = pinItemCls.getField("a");
        Constructor<?> renderPinCtor = renderPinCls.getConstructor(
                Boolean.class, Boolean.class, Boolean.class, Integer.class,
                String.class, String.class, String.class, String.class, String.class);

        List<Object> renderedPins = new ArrayList<>();
        for (int index = 0; index < cachedPinItems.size(); index++) {
            Object original = pinOptionsCls.cast(pinItemOptionsField.get(cachedPinItems.get(index)));
            Object pinType = pinOptionsCls.getField("g").get(original);
            String pinTypeName = pinType == null ? null : (String) callNoArg(pinType, "a");
            renderedPins.add(renderPinCtor.newInstance(
                    pinOptionsCls.getField("e").get(original),
                    Boolean.valueOf(index == currentIndex),
                    pinOptionsCls.getField("i").get(original),
                    pinOptionsCls.getField("f").get(original),
                    pinTypeName,
                    pinOptionsCls.getField("a").get(original),
                    pinOptionsCls.getField("b").get(original),
                    pinOptionsCls.getField("c").get(original),
                    null));
        }

        Class<?> jsMessageCls = Class.forName("com.walmart.glass.instoremaps.z");
        String script = (String) jsMessageCls.getMethod("a", List.class).invoke(null, renderedPins);
        WebView webView = (WebView) cachedMapFragment.getClass().getField("i").get(cachedMapFragment);
        if (webView == null) {
            throw new IllegalStateException("The mounted map WebView is unavailable.");
        }
        webView.evaluateJavascript(script, null);
        Log.i(TAG, "RENDER_PINS_REQUESTED sent to the mounted map, primary index " + currentIndex);
    }

    /** Updates the locator Fragment's native item card without recreating that Fragment. */
    private static void updateMountedItemCard() throws Exception {
        Class<?> pinItemCls = Class.forName("com.walmart.glass.instoremaps.api.model.StoreMapPinItemDetails");
        Object itemDetails = pinItemCls.getField("b").get(cachedPinItems.get(currentIndex));
        String title = (String) itemDetails.getClass().getField("c").get(itemDetails);
        String location = (String) itemDetails.getClass().getField("d").get(itemDetails);

        Object fragmentBinding = callNoArg(cachedMapFragment, "Ve");
        Object itemCardBinding = fragmentBinding.getClass().getField("g").get(fragmentBinding);
        TextView titleView = (TextView) itemCardBinding.getClass().getField("j").get(itemCardBinding);
        TextView locationView = (TextView) itemCardBinding.getClass().getField("h").get(itemCardBinding);
        titleView.setText(title == null ? "" : title);
        locationView.setText(location == null || location.isEmpty() ? "" : "Aisle " + location);
        Log.i(TAG, "Updated mounted item card for primary index " + currentIndex);
    }

    private static void removeNavBar() {
        if (navOverlayView != null && navOverlayView.getParent() instanceof ViewGroup) {
            ((ViewGroup) navOverlayView.getParent()).removeView(navOverlayView);
        }
        navOverlayView = null;
    }

    /** Tries the fragment's ViewModel first (via Ye()), then the fragment itself, for a list of items with getProduct(). */
    private static List<Object> findProducts(Object fragment) {
        List<Object> results = new ArrayList<>();

        Object viewModel = tryCallNoArg(fragment, "Ye");
        List<Object> items = viewModel != null ? findBestProductList(viewModel) : Collections.emptyList();
        if (items.isEmpty()) {
            Log.i(TAG, "No product list found on Ye() view model; trying fragment itself");
            items = findBestProductList(fragment);
        }

        for (Object item : items) {
            Object product = tryCallAny(item, "getProduct", "A");
            if (product != null) {
                results.add(product);
            }
        }
        return results;
    }

    /** Scans getters and fields of target for a non-empty List whose elements expose getProduct(). */
    @SuppressWarnings("unchecked")
    private static List<Object> findBestProductList(Object target) {
        List<Object> best = null;
        List<String> candidates = new ArrayList<>();

        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || !List.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                m.setAccessible(true);
                Object result = m.invoke(target);
                if (!(result instanceof List) || ((List<?>) result).isEmpty()) continue;
                List<?> list = (List<?>) result;
                candidates.add(m.getName() + "() -> " + list.size() + "x " + list.get(0).getClass().getName());
                if (best == null && hasGetProduct(list.get(0))) {
                    best = (List<Object>) list;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Field f : target.getClass().getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object result = f.get(target);
                if (!(result instanceof List) || ((List<?>) result).isEmpty()) continue;
                List<?> list = (List<?>) result;
                candidates.add(f.getName() + " (field) -> " + list.size() + "x " + list.get(0).getClass().getName());
                if (best == null && hasGetProduct(list.get(0))) {
                    best = (List<Object>) list;
                }
            } catch (Throwable ignored) {
            }
        }

        Log.i(TAG, "List candidates on " + target.getClass().getName() + ": " + candidates);
        return best != null ? best : Collections.emptyList();
    }

    private static boolean hasGetProduct(Object element) {
        if (element == null) return false;
        return findMethodAny(element.getClass(), "getProduct", "A") != null;
    }

    /** Finds a public zero-arg method by any of the candidate names (jadx's display name may
     * differ from the real compiled name due to Kotlin-metadata-based getter renaming). */
    private static Method findMethodAny(Class<?> cls, String... candidateNames) {
        for (String candidate : candidateNames) {
            try {
                return cls.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /** Handles both true-static methods and Kotlin `object` singletons (INSTANCE field) uniformly. */
    private static Object getFromRegistry(Class<?> registryCls, Class<?> wantedCls) throws Exception {
        Method m = registryCls.getMethod("a", Class.class);
        m.setAccessible(true);
        if (Modifier.isStatic(m.getModifiers())) {
            return m.invoke(null, wantedCls);
        }
        Field instanceField = registryCls.getField("INSTANCE");
        return m.invoke(instanceField.get(null), wantedCls);
    }

    /** Gets an eagerly-registered platform service; navigation is registered through e(), not a(). */
    private static Object getFromRegistryUsingE(Class<?> registryCls, Class<?> wantedCls) throws Exception {
        Method m = registryCls.getMethod("e", Class.class);
        m.setAccessible(true);
        return Modifier.isStatic(m.getModifiers()) ? m.invoke(null, wantedCls)
                : m.invoke(registryCls.getField("INSTANCE").get(null), wantedCls);
    }

    private static Object callNoArg(Object target, String methodName) throws Exception {
        Method m = target.getClass().getMethod(methodName);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Object tryCallNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            return callNoArg(target, methodName);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Tries each candidate method name in order, returning the first successful call's result. */
    private static Object tryCallAny(Object target, String... candidateNames) {
        if (target == null) return null;
        Method m = findMethodAny(target.getClass(), candidateNames);
        if (m == null) return null;
        try {
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** Alphanumeric-aware comparison so "A2" sorts before "A10" instead of after it. */
    private static int naturalCompare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String numA = a.substring(si, i).replaceFirst("^0+(?=\\d)", "");
                String numB = b.substring(sj, j).replaceFirst("^0+(?=\\d)", "");
                if (numA.length() != numB.length()) return numA.length() - numB.length();
                int cmp = numA.compareTo(numB);
                if (cmp != 0) return cmp;
            } else {
                if (ca != cb) return Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }
}
