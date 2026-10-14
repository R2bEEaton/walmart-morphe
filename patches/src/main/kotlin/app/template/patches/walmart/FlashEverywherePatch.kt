package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val FLASH_EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartFlashEverywhere;"

object FlashEverywhereListTileFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/ListDetailTileView;",
    name = "setBuyMoreAndSaveView",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcom/walmart/glass/lists/domain/ListItem\$ListDetailItem;"),
)

object FlashEverywhereChecklistFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/V1;",
    name = "F",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcom/walmart/glass/lists/domain/ListItem\$ChecklistItem;"),
)

val flashEverywherePatch = bytecodePatch(
    name = "Flash Everywhere",
    description = "Adds a dedicated 'Flash tag' sibling pill button next to Aisle indicators throughout the app to illuminate electronic shelf tags directly.",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        val setBuyMoreMethod = FlashEverywhereListTileFingerprint.method
        setBuyMoreMethod.addInstructions(
            setBuyMoreMethod.instructions.size - 1,
            "invoke-static {p0, p1}, $FLASH_EXTENSION_CLASS->onBindAisle(Landroid/view/View;Ljava/lang/Object;)V",
        )

        val checklistBindMethod = FlashEverywhereChecklistFingerprint.method
        checklistBindMethod.addInstructions(
            checklistBindMethod.instructions.size - 1,
            "invoke-static {p0, p1}, $FLASH_EXTENSION_CLASS->onBindChecklist(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
