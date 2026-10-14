package app.template.patches.walmart

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/WalmartRouteMyList;"

object MyItemsFragmentViewCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/walmart/glass/myitems/view/MyItemsFragment;",
    name = "onViewCreated",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

val defaultListsTabPatch = bytecodePatch(
    name = "Default Lists Tab",
    description = "Opens the 'Lists' tab by default when tapping 'My Items' instead of the 'Reorder' tab.",
    default = true,
) {
    compatibleWith(walmartCompatibility)

    extendWith("extensions/extension.mpe")

    execute {
        val onViewCreatedMethod = MyItemsFragmentViewCreatedFingerprint.method
        onViewCreatedMethod.addInstructions(
            onViewCreatedMethod.instructions.size - 1,
            "invoke-static/range {p0 .. p1}, $EXTENSION_CLASS->onMyItemsViewCreated(Ljava/lang/Object;Landroid/view/View;)V",
        )
    }
}
