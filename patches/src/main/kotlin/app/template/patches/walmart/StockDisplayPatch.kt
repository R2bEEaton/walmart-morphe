package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val STOCK_EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartStockBadge;"

object ListDetailTileViewSetBuyMoreAndSaveViewFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/ListDetailTileView;",
    name = "setBuyMoreAndSaveView",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcom/walmart/glass/lists/domain/ListItem\$ListDetailItem;"),
)

object V1ChecklistBindFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/V1;",
    name = "F",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcom/walmart/glass/lists/domain/ListItem\$ChecklistItem;"),
)

val stockDisplayPatch = bytecodePatch(
    name = "Real-Time Stock & Inventory Display",
    description = "Displays real-time in-store stock availability badges on list and checklist items (e.g. In Stock, Low Stock / Only X left, Out of Stock).",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        val setBuyMoreMethod = ListDetailTileViewSetBuyMoreAndSaveViewFingerprint.method
        setBuyMoreMethod.addInstructions(
            setBuyMoreMethod.instructions.size - 1,
            "invoke-static {p0, p1}, $STOCK_EXTENSION_CLASS->onBindStock(Landroid/view/View;Ljava/lang/Object;)V",
        )

        val checklistBindMethod = V1ChecklistBindFingerprint.method
        checklistBindMethod.addInstructions(
            checklistBindMethod.instructions.size - 1,
            "invoke-static {p0, p1}, $STOCK_EXTENSION_CLASS->onBindChecklist(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
