package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartFindItemButton;"

object ListDetailTileViewSetActionButtonFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/ListDetailTileView;",
    name = "setActionButton",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcom/walmart/glass/lists/utils/p;"),
)

object ListDetailTileViewSetOnActionButtonClickedFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/lists/view/lists/ListDetailTileView;",
    name = "setOnActionButtonClicked",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lkotlin/jvm/functions/Function1;"),
)

val findItemButtonPatch = bytecodePatch(
    name = "Find Item for Linked Items",
    description = "Restores the 'Find item' button on linked shopping list items so you can link a different product.",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        val setActionButtonMethod = ListDetailTileViewSetActionButtonFingerprint.method
        setActionButtonMethod.addInstructions(
            0,
            "invoke-static {p0, p1}, $EXTENSION_CLASS->onSetActionButton(Landroid/view/View;Ljava/lang/Object;)V",
        )

        val setListenerMethod = ListDetailTileViewSetOnActionButtonClickedFingerprint.method
        setListenerMethod.addInstructions(
            setListenerMethod.instructions.size - 1,
            "invoke-static {p0, p1}, $EXTENSION_CLASS->onTileBind(Landroid/view/View;Ljava/lang/Object;)V",
        )
    }
}
