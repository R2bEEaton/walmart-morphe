package app.template.extension.extension;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class WalmartStockBadge {
    private static final String TAG = "WalmartStockBadge";
    private static final String BADGE_TAG = "walmart_stock_badge_view";

    public static void onBindStock(View tileView, Object itemObj) {
        try {
            if (tileView == null || !(tileView instanceof ViewGroup)) {
                return;
            }
            final ViewGroup vg = (ViewGroup) tileView;
            final Context context = vg.getContext();

            // Remove legacy floating badge if present from earlier versions
            View legacyBadge = vg.findViewWithTag(BADGE_TAG);
            if (legacyBadge != null) {
                vg.removeView(legacyBadge);
            }

            // Find title views
            TextView nameView = (TextView) findViewByIdName(vg, "lists_item_detail_name");
            TextView genericNameView = (TextView) findViewByIdName(vg, "lists_item_detail_generic_name");
            TextView titleView = (nameView != null && nameView.getVisibility() == View.VISIBLE) ? nameView : genericNameView;

            if (titleView == null) {
                return;
            }

            if (itemObj == null) {
                resetTitleHighlight(titleView);
                return;
            }

            // Extract ListItemProduct via itemObj.A()
            Method methodA = null;
            for (Method m : itemObj.getClass().getMethods()) {
                if (m.getName().equals("A") && m.getParameterTypes().length == 0) {
                    methodA = m;
                    break;
                }
            }
            if (methodA == null) {
                resetTitleHighlight(titleView);
                return;
            }

            Object product = methodA.invoke(itemObj);
            if (product == null) {
                resetTitleHighlight(titleView);
                return;
            }

            // Extract fields from product
            String statusName = "";
            try {
                Method w1 = product.getClass().getMethod("w1");
                Object statusEnum = w1.invoke(product);
                if (statusEnum != null) {
                    statusName = statusEnum.toString();
                }
            } catch (Throwable t) {
                try {
                    Field fB = product.getClass().getDeclaredField("b");
                    fB.setAccessible(true);
                    Object statusEnum = fB.get(product);
                    if (statusEnum != null) {
                        statusName = statusEnum.toString();
                    }
                } catch (Throwable ignored) {}
            }

            String availMsg = null;
            try {
                Field fC = product.getClass().getDeclaredField("c");
                fC.setAccessible(true);
                availMsg = (String) fC.get(product);
            } catch (Throwable ignored) {}

            double orderLimit = 0.0;
            try {
                Field fO = product.getClass().getDeclaredField("o");
                fO.setAccessible(true);
                orderLimit = fO.getDouble(product);
            } catch (Throwable ignored) {}

            Boolean nearbyStore = null;
            try {
                Field fA = product.getClass().getDeclaredField("a");
                fA.setAccessible(true);
                nearbyStore = (Boolean) fA.get(product);
            } catch (Throwable ignored) {}

            Log.i(TAG, "Item stock info: status=" + statusName + ", msg=" + availMsg + ", limit=" + orderLimit + ", nearby=" + nearbyStore);

            boolean isOut = "OUT_OF_STOCK".equalsIgnoreCase(statusName) ||
                            (availMsg != null && availMsg.toLowerCase().contains("out of stock"));
            boolean isNotAvail = "NOT_AVAILABLE".equalsIgnoreCase(statusName);

            boolean isLowStock = availMsg != null && (
                    availMsg.toLowerCase().contains("left") ||
                    availMsg.toLowerCase().contains("few") ||
                    availMsg.toLowerCase().matches(".*\\b\\d+\\s+left\\b.*")
            );

            final int bgColor;
            final int textColor;
            final String toastLabel;

            if (isOut) {
                // Red highlight: Out of stock
                bgColor = Color.parseColor("#FCE8E6");
                textColor = Color.parseColor("#B3261E");
                toastLabel = "Out of stock";
            } else if (isNotAvail) {
                // Gray highlight: Unavailable
                bgColor = Color.parseColor("#F1F3F4");
                textColor = Color.parseColor("#444746");
                toastLabel = "Unavailable";
            } else if (isLowStock) {
                // Amber highlight: Low stock / few left
                bgColor = Color.parseColor("#FEF7E0");
                textColor = Color.parseColor("#8A3B00");
                toastLabel = (availMsg != null && !availMsg.isEmpty()) ? availMsg : "Low stock";
            } else {
                // Green highlight: In stock
                bgColor = Color.parseColor("#E6F4EA");
                textColor = Color.parseColor("#0F5223");
                if (orderLimit > 0 && orderLimit <= 5) {
                    toastLabel = "In stock (Limit " + (int) orderLimit + ")";
                } else if (availMsg != null && !availMsg.isEmpty()) {
                    toastLabel = "In stock: " + availMsg;
                } else {
                    toastLabel = "In stock";
                }
            }

            // Apply subtle highlight styling to title
            applyTitleHighlight(titleView, context, bgColor, textColor);

            // Also attach a quick Toast on click / long click for stock details
            final String finalToast = toastLabel;
            titleView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(context, "Stock status: " + finalToast, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

        } catch (Throwable t) {
            Log.e(TAG, "Error applying stock title highlight", t);
        }
    }

    private static void applyTitleHighlight(TextView tv, Context context, int bgColor, int textColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(6, context));
        gd.setColor(bgColor);

        tv.setBackground(gd);
        tv.setTextColor(textColor);

        int padH = dpToPx(6, context);
        int padV = dpToPx(3, context);
        tv.setPadding(padH, padV, padH, padV);
    }

    private static void resetTitleHighlight(TextView tv) {
        tv.setBackground(null);
        tv.setTextColor(Color.parseColor("#2E2F32"));
        tv.setPadding(0, 0, 0, 0);
    }

    public static void onBindChecklist(Object viewHolder, Object checklistItem) {
        try {
            if (viewHolder == null || checklistItem == null) return;

            Method mG = null;
            for (Method m : checklistItem.getClass().getMethods()) {
                if (m.getName().equals("g") && m.getParameterTypes().length == 0) {
                    mG = m;
                    break;
                }
            }
            if (mG == null) return;
            Object listDetailItem = mG.invoke(checklistItem);
            if (listDetailItem == null) return;

            View itemView = null;
            Class<?> curr = viewHolder.getClass();
            while (curr != null && curr != Object.class && itemView == null) {
                for (Field f : curr.getDeclaredFields()) {
                    if (View.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        try {
                            Object val = f.get(viewHolder);
                            if (val instanceof View) {
                                itemView = (View) val;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                curr = curr.getSuperclass();
            }

            if (itemView == null) return;

            View tileView = itemView;
            if (itemView instanceof ViewGroup) {
                View holder = findViewByIdName((ViewGroup) itemView, "lists_detail_holder");
                if (holder != null) {
                    tileView = holder;
                }
            }

            onBindStock(tileView, listDetailItem);
        } catch (Throwable t) {
            Log.e(TAG, "Error binding checklist stock highlight", t);
        }
    }

    private static View findViewByIdName(ViewGroup parent, String resName) {
        if (parent == null || resName == null) return null;
        Context context = parent.getContext();
        int resId = context.getResources().getIdentifier(resName, "id", context.getPackageName());
        if (resId != 0) {
            View v = parent.findViewById(resId);
            if (v != null) return v;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getId() != View.NO_ID) {
                try {
                    String entry = child.getResources().getResourceEntryName(child.getId());
                    if (resName.equals(entry)) return child;
                } catch (Throwable ignored) {}
            }
            if (child instanceof ViewGroup) {
                View found = findViewByIdName((ViewGroup) child, resName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dpToPx(int dp, Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
