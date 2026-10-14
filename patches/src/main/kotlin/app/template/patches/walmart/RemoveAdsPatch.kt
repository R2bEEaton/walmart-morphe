package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartRouteMyList;"
private const val REMOVE_ADS_EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartRemoveAds;"

object CCMUtilsFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/ads/utils/CCMUtilsKt;",
    name = "h",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Lcom/walmart/glass/ads/ccm/CcmManager\$PrimaryConfigModel;",
    parameters = listOf("Lcom/walmart/glass/ads/utils/AdModuleId;", "Lcom/walmart/glass/ads/utils/PageUniqueId;"),
)

object AdPlaceHolderViewOnAttachedFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/ads/view/AdPlaceHolderView;",
    name = "onAttachedToWindow",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
)

object TempoAdapterSetItemsFingerprint : Fingerprint(
    definingClass = "Lglass/platform/tempo/components/widget/adapter/a;",
    name = "y",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/util/List;", "Lkotlin/jvm/functions/Function0;"),
)

object SponsoredContainerViewSetLabelFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/ui/shared/sponsored/SponsoredContainerView;",
    name = "setLabel",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)

val removeAdsPatch = bytecodePatch(
    name = "Remove Sponsored Ads",
    description = "Removes sponsored product carousels, banner ads, and promotional placements for a faster, cleaner app experience.",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        val hMethod = CCMUtilsFingerprint.method
        hMethod.addInstructions(
            0,
            """
                new-instance v0, Lcom/walmart/glass/ads/ccm/CcmManager${'$'}PrimaryConfigModel;
                const/4 v1, 0x0
                invoke-direct {v0, v1, v1, v1, v1}, Lcom/walmart/glass/ads/ccm/CcmManager${'$'}PrimaryConfigModel;-><init>(ZZZZ)V
                return-object v0
            """.trimIndent()
        )

        val onAttachedMethod = AdPlaceHolderViewOnAttachedFingerprint.method
        onAttachedMethod.addInstructions(
            0,
            "invoke-static {p0}, $EXTENSION_CLASS->hideAdView(Landroid/view/View;)V",
        )

        val tempoAdapterMethod = TempoAdapterSetItemsFingerprint.method
        tempoAdapterMethod.addInstructions(
            0,
            """
                invoke-static {p1}, $REMOVE_ADS_EXTENSION_CLASS->filterModules(Ljava/util/List;)Ljava/util/List;
                move-result-object p1
            """.trimIndent()
        )

        val sponsoredLabelMethod = SponsoredContainerViewSetLabelFingerprint.method
        sponsoredLabelMethod.addInstructions(
            0,
            "invoke-static {p0, p1}, $REMOVE_ADS_EXTENSION_CLASS->onSponsoredLabelSet(Landroid/view/View;Ljava/lang/String;)V",
        )
    }
}
