package app.template.extension.extension;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

public class WalmartRemoveShop {
    private static final String TAG = "WalmartRemoveShop";

    public static Object getTab2() {
        try {
            Class<?> enumCls = Class.forName("glass.platform.navigation.api.i");
            Field field = enumCls.getDeclaredField("c");
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable t) {
            Log.e(TAG, "Error getting TAB_2", t);
            return null;
        }
    }

    public static Object filterDefaultTab(Object tab) {
        if (tab == null) return getTab2();
        try {
            if (tab instanceof Enum) {
                String name = ((Enum<?>) tab).name();
                if ("TAB_1".equals(name) || "b".equals(name) || ((Enum<?>) tab).ordinal() == 0) {
                    Log.d(TAG, "Filtering default tab from TAB_1 to TAB_2");
                    return getTab2();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "filterDefaultTab error", t);
        }
        return tab;
    }

    public static Map filterNavMap(Map map) {
        if (map == null) return null;
        try {
            Iterator it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                Object key = entry.getKey();
                if (key != null) {
                    if (key instanceof Enum) {
                        String name = ((Enum<?>) key).name();
                        if ("TAB_1".equals(name) || "b".equals(name) || ((Enum<?>) key).ordinal() == 0) {
                            Log.d(TAG, "Removing Shop tab (" + name + ") from bottom nav map");
                            it.remove();
                            continue;
                        }
                    }
                    try {
                        Field aField = key.getClass().getDeclaredField("a");
                        aField.setAccessible(true);
                        int id = aField.getInt(key);
                        if (id == 0x7f0a5d07) {
                            Log.d(TAG, "Removing Shop tab (id 0x7f0a5d07) from bottom nav map");
                            it.remove();
                        }
                    } catch (Throwable ignored) {}
                }
            }
            Log.d(TAG, "filterNavMap completed, remaining tabs count: " + map.size());
        } catch (Throwable t) {
            Log.e(TAG, "Error in filterNavMap", t);
        }
        return map;
    }

    public static void onActivityStart(Activity activity) {
        if (activity == null) return;
        try {
            int tab1Id = activity.getResources().getIdentifier("navigation_tab_1", "id", activity.getPackageName());
            if (tab1Id == 0) tab1Id = 0x7f0a5d07;
            View tab1 = activity.findViewById(tab1Id);
            if (tab1 != null) {
                tab1.setVisibility(View.GONE);
                Log.d(TAG, "Hidden navigation_tab_1 view in RootActivity");
            }
        } catch (Throwable t) {
            Log.e(TAG, "onActivityStart error", t);
        }
    }
}
