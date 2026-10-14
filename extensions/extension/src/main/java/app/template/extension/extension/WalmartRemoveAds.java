package app.template.extension.extension;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

public class WalmartRemoveAds {
    private static final String TAG = "WalmartRemoveAds";

    private static final Set<String> BLOCKED_MODULE_NAMES = new HashSet<String>();
    private static final WeakHashMap<View, int[]> originalLayoutParams = new WeakHashMap<View, int[]>();

    static {
        // Hero Carousels (Top promo carousels in Shop tab)
        BLOCKED_MODULE_NAMES.add("HeroCarouselModule");
        BLOCKED_MODULE_NAMES.add("HeroCarouselUnifiedModule");
        BLOCKED_MODULE_NAMES.add("PrismHeroCarouselModule");
        BLOCKED_MODULE_NAMES.add("PrismHeroCarouselUnifiedModule");
        BLOCKED_MODULE_NAMES.add("Hero4Module");
        BLOCKED_MODULE_NAMES.add("HeroPovModule");

        // Adjustable Banners ("OMG-worthy fits", "New from Forever 21", "Make home updates easy")
        BLOCKED_MODULE_NAMES.add("AdjustableBannerModule");
        BLOCKED_MODULE_NAMES.add("AdjustableBannerUnifiedModule");
        BLOCKED_MODULE_NAMES.add("PrismAdjustableBannerModule");
        BLOCKED_MODULE_NAMES.add("PrismAdjustableBannerUnifiedModule");

        // Triple Pack ("Save on Halloween variety bags", "Fall treats have hit the bakery")
        BLOCKED_MODULE_NAMES.add("TriplePackModule");
        BLOCKED_MODULE_NAMES.add("TriplePackUnifiedModule");
        BLOCKED_MODULE_NAMES.add("TriplePackCardModule");
        BLOCKED_MODULE_NAMES.add("TriplePackUnifiedCardModule");
        BLOCKED_MODULE_NAMES.add("PrismTriplePackModule");
        BLOCKED_MODULE_NAMES.add("PrismTriplePackUnifiedModule");

        // Walmart+ & Gas Savings Banners (e.g. "Members save 10¢/gal at CITGO", W+ promo banners)
        BLOCKED_MODULE_NAMES.add("PrismEvergreenBannerModule");
        BLOCKED_MODULE_NAMES.add("PrismEvergreenBannerUnifiedModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusBannerV1Module");
        BLOCKED_MODULE_NAMES.add("WalmartPlusBannerV1UnifiedModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusBannerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusBottomBannerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusLiveEventBannerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusLiveEventBannerUnifiedModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusAcquisitionBannerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusBeforeEventBannerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusBeforeEventBannerUnifiedModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusSignupSharedBannerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusEngagementNudgeModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusRepeatBenefitEngagementDetailsModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusStreamingBenefitTrackerModule");
        BLOCKED_MODULE_NAMES.add("WalmartPlusCardPlaceholderModule");

        // Ads & Sponsored Modules
        BLOCKED_MODULE_NAMES.add("SponsoredProductAdModule");
        BLOCKED_MODULE_NAMES.add("SponsoredVideoAdModule");
        BLOCKED_MODULE_NAMES.add("StoreSponsoredAdModule");
        BLOCKED_MODULE_NAMES.add("SponsoredBrandAmplifierAdModule");
        BLOCKED_MODULE_NAMES.add("BrandboxDisplayAdModule");
        BLOCKED_MODULE_NAMES.add("BuyboxAdModule");
        BLOCKED_MODULE_NAMES.add("DisplayVideoCardAdModule");
        BLOCKED_MODULE_NAMES.add("DynamicAdContainerModule");
        BLOCKED_MODULE_NAMES.add("GalleryDisplayAdModule");
        BLOCKED_MODULE_NAMES.add("MarqueeDisplayAdModule");
        BLOCKED_MODULE_NAMES.add("OfferSponsoredAdModule");
        BLOCKED_MODULE_NAMES.add("SkylineDisplayAdModule");
        BLOCKED_MODULE_NAMES.add("AdsContentProviderModule");
        BLOCKED_MODULE_NAMES.add("PrismTileTakeOverModule");

        // Search Banners & Ads
        BLOCKED_MODULE_NAMES.add("SearchBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchDealsBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchDualMessageBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchNonProductBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchSubscriptionBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchPromptBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchSamplingBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchSamplingUserInfoBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("SearchShopNowOrAddCardBenefitsBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("DealsConfigTempoModule");

        // Credit Cards & Financial Banners
        BLOCKED_MODULE_NAMES.add("CapitalOneBannerModule");
        BLOCKED_MODULE_NAMES.add("OneCreditCardBannerModule");
        BLOCKED_MODULE_NAMES.add("OnePayLaterBannerModule");
        BLOCKED_MODULE_NAMES.add("SynchronyCreditBannerModule");

        // Skinny & Other Promo Banners
        BLOCKED_MODULE_NAMES.add("SkinnyBannerModule");
        BLOCKED_MODULE_NAMES.add("PrismSkinnyBannerModule");
        BLOCKED_MODULE_NAMES.add("DualMessageBannerModule");
        BLOCKED_MODULE_NAMES.add("DynamicMessagesBannerModule");
        BLOCKED_MODULE_NAMES.add("HeadingBannerModule");
        BLOCKED_MODULE_NAMES.add("ImageDetailBannerModule");
        BLOCKED_MODULE_NAMES.add("LocationHeadingBannerModule");
        BLOCKED_MODULE_NAMES.add("SellerPromotionBannerModule");
        BLOCKED_MODULE_NAMES.add("MembershipUpgradeBannerModule");
        BLOCKED_MODULE_NAMES.add("DroneBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("EmailCaptureBannerTempoModule");
        BLOCKED_MODULE_NAMES.add("TextBannerTempoModule");

        // Video & Card Carousels
        BLOCKED_MODULE_NAMES.add("VideoCarouselModule");
        BLOCKED_MODULE_NAMES.add("VideoCardModule");

        // Nudges & Surveys
        BLOCKED_MODULE_NAMES.add("SurveyNudgeModule");
    }

    /**
     * Intercepts Tempo module list before submitting to RecyclerView adapter.
     * Filters out unwanted promo banners, hero carousels, and ads.
     */
    public static List filterModules(List list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        try {
            Log.e(TAG, "filterModules called with " + list.size() + " items");
            ArrayList filtered = new ArrayList(list.size());
            for (Object item : list) {
                if (item == null) continue;
                if (isUnwantedModule(item)) {
                    Log.e(TAG, "Filtered unwanted module: " + item.getClass().getName());
                    continue;
                }
                filtered.add(item);
            }
            Log.e(TAG, "filterModules retained " + filtered.size() + " / " + list.size() + " items");
            return filtered;
        } catch (Throwable t) {
            Log.e(TAG, "Error filtering modules", t);
            return list;
        }
    }

    private static boolean isUnwantedModule(Object module) {
        String simpleName = module.getClass().getSimpleName();
        if (BLOCKED_MODULE_NAMES.contains(simpleName)) {
            return true;
        }

        String className = module.getClass().getName().toLowerCase(Locale.US);

        // Promo carousels & banners
        if (className.contains("herocarousel") ||
            className.contains("adjustablebanner") ||
            className.contains("adjustablecardcarousel") ||
            className.contains("heropov") ||
            className.contains("povcarousel") ||
            className.contains("triplepack") ||
            className.contains("skinnybanner") ||
            className.contains("prismskinnybanner") ||
            className.contains("walmartplusbanner") ||
            className.contains("walmartplusacquisitionbanner") ||
            className.contains("walmartplusmpsmarketing") ||
            className.contains("walmartplusbeforeeventbanner") ||
            className.contains("walmartplussignupsharedbanner") ||
            className.contains("wplusintegratedsignup") ||
            className.contains("wplussplash") ||
            className.contains("wpluslinking") ||
            className.contains("trendengine") ||
            className.contains("contentcarousel") ||
            className.contains("socialcontentcarousel") ||
            className.contains("socialreel") ||
            className.contains(".ads.") ||
            className.contains(".ad.") ||
            className.contains("advertisement") ||
            className.contains("sponsored") ||
            className.contains("skyline") ||
            className.contains("spotlightad") ||
            className.contains("bannerv1deals") ||
            className.contains("membershipupgradebanner") ||
            className.contains("capitalonebanner") ||
            className.contains("onecreditcardbanner") ||
            className.contains("onepaylater") ||
            className.contains("synchronycredit") ||
            className.contains("surveynudge") ||
            className.contains("signinnudge") ||
            className.contains("evergreenbanner") ||
            className.contains("spcarouselad") ||
            className.contains("spvideoad") ||
            className.contains("storesponsoredad")) {
            return true;
        }

        // Check getType() via reflection
        try {
            Method getTypeMethod = module.getClass().getMethod("getType");
            Object typeObj = getTypeMethod.invoke(module);
            if (typeObj != null) {
                String type = typeObj.toString().toLowerCase(Locale.US);
                if (type.contains("hero_carousel") ||
                    type.contains("adjustable_banner") ||
                    type.contains("adjustable_card") ||
                    type.contains("triple_pack") ||
                    type.contains("evergreen_banner") ||
                    type.contains("wplus_banner") ||
                    type.contains("display_ad") ||
                    type.contains("skyline_ad") ||
                    type.contains("sponsored_product") ||
                    type.contains("trend_engine") ||
                    type.contains("gas_savings")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // Check getName() via reflection
        try {
            Method getNameMethod = module.getClass().getMethod("getName");
            Object nameObj = getNameMethod.invoke(module);
            if (nameObj != null) {
                String name = nameObj.toString().toLowerCase(Locale.US);
                if (name.contains("gas_savings") ||
                    name.contains("omg_worthy") ||
                    name.contains("forever21") ||
                    name.contains("citgo") ||
                    name.contains("hero_carousel") ||
                    name.contains("trending") ||
                    name.contains("fits")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * Collapses product tile when SponsoredContainerView is shown or set with a label.
     */
    public static void onSponsoredLabelSet(View sponsoredContainer, String label) {
        if (sponsoredContainer == null) return;
        boolean isSponsored = (label != null && !label.trim().isEmpty() && !label.equalsIgnoreCase("null"));
        Log.e(TAG, "onSponsoredLabelSet: isSponsored=" + isSponsored + ", label=" + label);
        setTileCollapsed(sponsoredContainer, isSponsored);
    }

    private static void setTileCollapsed(View view, boolean collapse) {
        try {
            View current = view;
            ViewParent parent = current.getParent();
            while (parent instanceof View) {
                View pv = (View) parent;
                ViewParent grandParent = pv.getParent();
                if (grandParent != null && grandParent.getClass().getName().contains("RecyclerView")) {
                    if (collapse) {
                        ViewGroup.LayoutParams lp = pv.getLayoutParams();
                        if (lp != null && !originalLayoutParams.containsKey(pv)) {
                            originalLayoutParams.put(pv, new int[]{lp.width, lp.height});
                        }
                        pv.setVisibility(View.GONE);
                        if (lp != null) {
                            lp.width = 0;
                            lp.height = 0;
                            pv.setLayoutParams(lp);
                        }
                        Log.e(TAG, "Collapsed sponsored product tile: " + pv);
                    } else if (originalLayoutParams.containsKey(pv)) {
                        int[] dims = originalLayoutParams.get(pv);
                        pv.setVisibility(View.VISIBLE);
                        ViewGroup.LayoutParams lp = pv.getLayoutParams();
                        if (lp != null && dims != null) {
                            lp.width = dims[0];
                            lp.height = dims[1];
                            pv.setLayoutParams(lp);
                        }
                        originalLayoutParams.remove(pv);
                    }
                    return;
                }
                parent = grandParent;
            }
        } catch (Throwable t) {
            Log.w(TAG, "setTileCollapsed error", t);
        }
    }
}
