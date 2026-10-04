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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            showMapForCurrentIndex();
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

            Object pinOptions = pinOptionsCtor.newInstance(
                    zone, aisleNumber, section, null, null, null, null, null, null);
            Object itemDetails = itemDetailsCtor.newInstance(
                    null, itemId, name, isEmpty(displayValue) ? aisleNumber : displayValue, null, null,
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
        pinItems.sort((p1, p2) -> {
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
