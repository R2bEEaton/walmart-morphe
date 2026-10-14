package app.template.extension.extension;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class WalmartFlashEverywhere {
    private static final String TAG = "WalmartFlashEverywhere";
    public static final String FLASH_BTN_TAG = "walmart_flash_everywhere_btn";
    public static final String CONTAINER_TAG = "walmart_aisle_flash_container";

    public static void onBindAisle(View tileView, Object itemObj) {
        try {
            if (tileView == null || !(tileView instanceof ViewGroup)) {
                return;
            }
            final ViewGroup vg = (ViewGroup) tileView;
            final Context context = vg.getContext();

            // Locate the aisle view inside tileView
            View aisleView = findAisleView(vg);
            if (aisleView == null || aisleView.getVisibility() != View.VISIBLE) {
                removeFlashButton(vg);
                return;
            }

            // Guard check: Does this card or screen already have a dedicated native flash button?
            if (hasNativeFlashButton(vg)) {
                removeFlashButton(vg);
                return;
            }

            // Extract item ID for shelf tag flashing
            final String finalItemId = extractItemId(itemObj);
            if (finalItemId == null || finalItemId.isEmpty()) {
                removeFlashButton(vg);
                return;
            }

            // Check if aisleView is already wrapped in our horizontal container
            ViewParent vp = aisleView.getParent();
            if (vp instanceof LinearLayout && CONTAINER_TAG.equals(((LinearLayout) vp).getTag())) {
                LinearLayout container = (LinearLayout) vp;
                View existingBtn = container.findViewWithTag(FLASH_BTN_TAG);
                if (existingBtn != null) {
                    existingBtn.setVisibility(View.VISIBLE);
                    existingBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                WalmartRouteMyList.flashShelfTagDirectly(context, finalItemId);
                            } catch (Throwable t) {
                                Log.e(TAG, "Error flashing shelf tag on click", t);
                            }
                        }
                    });
                    return;
                }
            }

            // The parent container that holds aisleView
            ViewGroup parent = (aisleView.getParent() instanceof ViewGroup) ? (ViewGroup) aisleView.getParent() : vg;

            // Check if our sibling button already exists in parent
            View existingBtn = parent.findViewWithTag(FLASH_BTN_TAG);
            if (existingBtn != null) {
                existingBtn.setVisibility(View.VISIBLE);
                existingBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            WalmartRouteMyList.flashShelfTagDirectly(context, finalItemId);
                        } catch (Throwable t) {
                            Log.e(TAG, "Error flashing shelf tag on click", t);
                        }
                    }
                });
                return;
            }

            // Build the sibling "Flash tag" pill button
            View flashPill = createFlashPillView(context, finalItemId);

            // Attach pill immediately adjacent to aisleView
            attachPill(parent, aisleView, flashPill, context, finalItemId);

        } catch (Throwable t) {
            Log.e(TAG, "Error in onBindAisle", t);
        }
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

            onBindAisle(tileView, listDetailItem);
        } catch (Throwable t) {
            Log.e(TAG, "Error in onBindChecklist", t);
        }
    }

    private static View createFlashPillView(final Context context, final String itemId) {
        LinearLayout pill = new LinearLayout(context);
        pill.setId(View.generateViewId());
        pill.setTag(FLASH_BTN_TAG);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setClickable(true);
        pill.setFocusable(true);

        int padH = dpToPx(10, context);
        int padV = dpToPx(6, context);
        pill.setPadding(padH, padV, padH + dpToPx(2, context), padV);

        // Styling: Warm subtle amber pill with clean amber outline and touch ripple
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(16, context));
        bg.setColor(Color.parseColor("#FFFBEB")); // Subtle warm amber background
        bg.setStroke(dpToPx(1.5f, context), Color.parseColor("#F59E0B")); // Amber outline

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#FDE68A")),
                    bg,
                    null
            );
            pill.setBackground(ripple);
        } else {
            pill.setBackground(bg);
        }

        // 1. Radar Beacon Icon
        ImageView radarIcon = new ImageView(context);
        radarIcon.setTag(FLASH_BTN_TAG);
        int iconSize = dpToPx(16, context);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_VERTICAL;
        radarIcon.setLayoutParams(iconLp);
        radarIcon.setImageDrawable(new RadarBeaconDrawable(Color.parseColor("#D97706"), context));
        pill.addView(radarIcon);

        // 2. Text Label "Flash tag"
        TextView label = new TextView(context);
        label.setTag(FLASH_BTN_TAG);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textLp.gravity = Gravity.CENTER_VERTICAL;
        textLp.leftMargin = dpToPx(5, context);
        label.setLayoutParams(textLp);
        label.setText("Flash tag");
        label.setTextColor(Color.parseColor("#B45309")); // Bold legible amber
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        pill.addView(label);

        // Click handler with haptic feedback
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    WalmartRouteMyList.flashShelfTagDirectly(context, itemId);
                } catch (Throwable t) {
                    Log.e(TAG, "Error triggering shelf tag flash", t);
                }
            }
        });

        return pill;
    }

    private static void attachPill(ViewGroup parent, View aisleView, View flashPill, final Context context, final String itemId) {
        int marginStart = dpToPx(8, context);

        // If parent is already a horizontal LinearLayout (e.g. checklist or simple container)
        if (parent instanceof LinearLayout && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            pillLp.gravity = Gravity.CENTER_VERTICAL;
            pillLp.leftMargin = marginStart;
            int idx = parent.indexOfChild(aisleView);
            parent.addView(flashPill, idx + 1, pillLp);
            parent.requestLayout();
            return;
        }

        // For ConstraintLayout, RelativeLayout, FrameLayout, etc:
        // Wrap aisleView and flashPill together in a horizontal LinearLayout container
        // that replaces aisleView in parent and perfectly inherits aisleView's original LayoutParams!
        try {
            ViewGroup.LayoutParams origLp = aisleView.getLayoutParams();
            int idx = parent.indexOfChild(aisleView);
            if (idx >= 0) {
                parent.removeViewAt(idx);

                LinearLayout container = new LinearLayout(context);
                container.setTag(CONTAINER_TAG);
                container.setOrientation(LinearLayout.HORIZONTAL);
                container.setGravity(Gravity.CENTER_VERTICAL);

                // Add aisleView into container
                LinearLayout.LayoutParams aisleLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                aisleView.setLayoutParams(aisleLp);
                container.addView(aisleView);

                // Add flashPill into container
                LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                pillLp.leftMargin = marginStart;
                flashPill.setLayoutParams(pillLp);
                container.addView(flashPill);

                // Add container into parent at original index with original LayoutParams
                parent.addView(container, idx, origLp);
                parent.requestLayout();
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error wrapping aisleView in container", t);
        }

        // Fallback for safety
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        parent.addView(flashPill, lp);
        parent.requestLayout();
    }

    private static boolean hasNativeFlashButton(ViewGroup root) {
        if (root == null) return false;
        return checkViewForFlash(root);
    }

    private static boolean checkViewForFlash(View v) {
        if (v == null) return false;
        if (FLASH_BTN_TAG.equals(v.getTag())) return false;
        if (CONTAINER_TAG.equals(v.getTag())) return false;

        // Skip anything inside our own container or pill
        ViewParent vp = v.getParent();
        while (vp instanceof View) {
            Object tag = ((View) vp).getTag();
            if (FLASH_BTN_TAG.equals(tag) || CONTAINER_TAG.equals(tag)) {
                return false;
            }
            vp = vp.getParent();
        }

        // Check native view ID
        if (v.getId() != View.NO_ID) {
            try {
                String entry = v.getResources().getResourceEntryName(v.getId()).toLowerCase(Locale.ROOT);
                if (entry.contains("flash_shelf_label") || entry.contains("shop_to_light") ||
                    entry.contains("native_flash") || entry.contains("esl_flash")) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // Check text or content description for native flash buttons
        if (v instanceof TextView) {
            CharSequence txt = ((TextView) v).getText();
            if (txt != null) {
                String s = txt.toString().toLowerCase(Locale.ROOT);
                if (s.contains("flash shelf") || s.contains("light up")) {
                    return true;
                }
            }
        }

        CharSequence desc = v.getContentDescription();
        if (desc != null) {
            String s = desc.toString().toLowerCase(Locale.ROOT);
            if (s.contains("flash shelf") || s.contains("light up")) {
                return true;
            }
        }

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (checkViewForFlash(vg.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeFlashButton(ViewGroup vg) {
        if (vg == null) return;
        View existing = vg.findViewWithTag(FLASH_BTN_TAG);
        if (existing != null) {
            existing.setVisibility(View.GONE);
        }
    }

    private static View findAisleView(ViewGroup vg) {
        // Direct ID lookups
        View v = findViewByIdName(vg, "lists_item_detail_aisle_button_description");
        if (v != null && v.getVisibility() == View.VISIBLE) return v;

        v = findViewByIdName(vg, "lists_item_detail_aisle");
        if (v != null && v.getVisibility() == View.VISIBLE) return v;

        v = findViewByIdName(vg, "lists_item_detail_location");
        if (v != null && v.getVisibility() == View.VISIBLE) return v;

        v = findViewByIdName(vg, "aisle_button");
        if (v != null && v.getVisibility() == View.VISIBLE) return v;

        v = findViewByIdName(vg, "item_location");
        if (v != null && v.getVisibility() == View.VISIBLE) return v;

        // Try data-binding reflection
        try {
            Field fBinding = vg.getClass().getDeclaredField("a");
            fBinding.setAccessible(true);
            Object binding = fBinding.get(vg);
            if (binding != null) {
                Field fI = binding.getClass().getDeclaredField("i");
                fI.setAccessible(true);
                View vI = (View) fI.get(binding);
                if (vI != null && vI.getVisibility() == View.VISIBLE) {
                    return vI;
                }
                Field fJ = binding.getClass().getDeclaredField("j");
                fJ.setAccessible(true);
                View vJ = (View) fJ.get(binding);
                if (vJ != null && vJ.getVisibility() == View.VISIBLE) {
                    return vJ;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static String extractItemId(Object itemObj) {
        if (itemObj == null) return "";
        try {
            Object product = null;
            for (Method m : itemObj.getClass().getMethods()) {
                if (m.getName().equals("A") && m.getParameterTypes().length == 0) {
                    product = m.invoke(itemObj);
                    break;
                }
            }

            String resolvedItemId = "";
            if (product != null) {
                Object idObj = WalmartRouteMyList.tryCallAny(product, "getUsItemId", "D3", "getId", "d");
                if (idObj != null) resolvedItemId = idObj.toString();
            }
            if (resolvedItemId.isEmpty()) {
                Object itemObjId = WalmartRouteMyList.tryCallAny(itemObj, "getUsItemId", "D3", "getId", "d", "a", "b", "t");
                if (itemObjId != null) resolvedItemId = itemObjId.toString();
            }
            return resolvedItemId;
        } catch (Throwable t) {
            return "";
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

    private static int dpToPx(float dp, Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    /**
     * Crisp Canvas-drawn Radar Beacon Icon (Center dot + 2 concentric wave arcs).
     */
    public static class RadarBeaconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Context context;

        public RadarBeaconDrawable(int color, Context context) {
            this.context = context;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float size = Math.min(bounds.width(), bounds.height());

            float strokeW = Math.max(dpToPx(1.6f, context), 2.5f);
            paint.setStrokeWidth(strokeW);

            // 1. Center pulsing dot
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, strokeW * 1.1f, paint);

            // 2. Inner concentric arcs
            paint.setStyle(Paint.Style.STROKE);
            float r1 = size * 0.28f;
            RectF rect1 = new RectF(cx - r1, cy - r1, cx + r1, cy + r1);
            canvas.drawArc(rect1, 120, 120, false, paint);
            canvas.drawArc(rect1, -60, 120, false, paint);

            // 3. Outer concentric arcs
            float r2 = size * 0.44f;
            RectF rect2 = new RectF(cx - r2, cy - r2, cx + r2, cy + r2);
            canvas.drawArc(rect2, 125, 110, false, paint);
            canvas.drawArc(rect2, -55, 110, false, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
